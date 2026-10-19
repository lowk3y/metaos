#!/usr/bin/env bash
# MetaOS's own files - pictures, videos, anything - in Google Cloud Storage, in project GOOGLE_CLOUD_PROJECT
# (ogrodje-metaos). Needs gcloud (in the devenv shell), signed in as an owner of the project: `gcloud auth login`.
#
#   google/storage.sh setup               the buckets and their service accounts; idempotent, re-run after editing
#   google/storage.sh key dev >> .envrc   a key for dev's service account (GOOGLE_STORAGE_KEY, METAOS_STORAGE_BUCKET)
#   google/storage.sh key prod >> .envrc  one for production's (PROD_GOOGLE_STORAGE_KEY), which k8s/set_secrets.sh
#                                         puts into metaos-secret
#
# A bucket per environment, in Frankfurt, each with a service account that can use only that bucket - its files, not
# its settings:
#   ogrodje-metaos-dev   development and tests. Tests write under test/, which is emptied after a day; no soft delete.
#   ogrodje-metaos-prod  production. Deleted and replaced files stay recoverable for 30 days (soft delete); Autoclass
#                        moves files nobody reads to cheaper storage classes, without retrieval fees.
# Both are private (public access prevention): MetaOS hands out signed URLs instead (Storage.kt), and the buckets'
# CORS lets the front-end upload and download with them straight from the browser.
#
# A key works until it's deleted. `key` makes a new one each time and leaves the older ones working, up to 10 per
# service account - delete the ones no longer used:
#   gcloud iam service-accounts keys list --iam-account=metaos-storage-dev@ogrodje-metaos.iam.gserviceaccount.com
#   gcloud iam service-accounts keys delete KEY_ID --iam-account=...
set -euo pipefail

: "${GOOGLE_CLOUD_PROJECT:?}"
export CLOUDSDK_CORE_PROJECT=$GOOGLE_CLOUD_PROJECT
# API calls count against this project too, whatever quota project gcloud's own config has
export CLOUDSDK_BILLING_QUOTA_PROJECT=$GOOGLE_CLOUD_PROJECT

# Bucket names are global, so they carry the organisation's name
bucket() { echo "ogrodje-metaos-$1"; }
account() { echo "metaos-storage-$1@$GOOGLE_CLOUD_PROJECT.iam.gserviceaccount.com"; }

usage() { echo "Usage: $0 setup | key dev|prod" >&2; exit 2; }

setup_bucket() {
  local env=$1 origins settings
  local bucket=gs://$(bucket "$env") account=$(account "$env")
  case $env in
    # Vite's dev server, and the app serving the built front-end
    dev) origins='["http://localhost:5173", "http://localhost:8080"]'
         settings=(--clear-soft-delete --no-enable-autoclass) ;;
    prod) origins='["https://metaos.ogrodje.si"]'
          settings=(--soft-delete-duration=30d --enable-autoclass) ;;
  esac

  local files
  files=$(mktemp -d)
  # Resumable uploads from the browser: it starts one with a signed POST, reads the session from Location and PUTs
  # the file there, in chunks if it's big. responseHeader is both the headers it may send and those it may read.
  cat >"$files/cors.json" <<JSON
[{
  "origin": $origins,
  "method": ["GET", "HEAD", "PUT", "POST"],
  "responseHeader": ["Content-Type", "Content-Range", "Range", "ETag", "Location", "x-goog-resumable",
                     "x-goog-if-generation-match"],
  "maxAgeSeconds": 3600
}]
JSON
  # Unfinished multipart uploads are billed until they're aborted; tests' leftovers go after a day
  jq -n --arg env "$env" '{rule: ([
    {action: {type: "AbortIncompleteMultipartUpload"}, condition: {age: 7}}
  ] + if $env == "dev" then [{action: {type: "Delete"}, condition: {age: 1, matchesPrefix: ["test/"]}}] else [] end)}' \
    >"$files/lifecycle.json"

  # The location can only be set here, on creation
  if ! gcloud storage buckets describe "$bucket" >/dev/null 2>&1; then
    gcloud storage buckets create "$bucket" --location=europe-west3 --default-storage-class=STANDARD \
      --uniform-bucket-level-access --public-access-prevention
  fi
  gcloud storage buckets update "$bucket" \
    --uniform-bucket-level-access --public-access-prevention \
    --cors-file="$files/cors.json" --lifecycle-file="$files/lifecycle.json" \
    --update-labels=app=metaos,env="$env" "${settings[@]}"
  rm -r "$files"

  if ! gcloud iam service-accounts describe "$account" >/dev/null 2>&1; then
    gcloud iam service-accounts create "metaos-storage-$env" --display-name="MetaOS storage ($env)" \
      --description="MetaOS's files in gs://$(bucket "$env") (google/storage.sh)"
  fi
  # Create, read, replace and delete files in this bucket only. A new service account takes a few seconds to show up.
  for attempt in 1 2 3 4 5 6; do
    gcloud storage buckets add-iam-policy-binding "$bucket" \
      --member="serviceAccount:$account" --role=roles/storage.objectUser >/dev/null && break
    [[ $attempt == 6 ]] && exit 1
    sleep 10
  done
  echo "$bucket: ready for $account" >&2
}

case ${1:-} in
  setup)
    gcloud services enable storage.googleapis.com iam.googleapis.com
    setup_bucket dev
    setup_bucket prod
    ;;

  # The export lines go to stdout and everything else to stderr, so they can be appended to .envrc as they are
  key)
    case ${2:-} in
      dev) VARIABLE=GOOGLE_STORAGE_KEY ;;
      prod) VARIABLE=PROD_GOOGLE_STORAGE_KEY ;;
      *) usage ;;
    esac
    KEY=$(mktemp)
    trap 'rm -f "$KEY"' EXIT
    gcloud iam service-accounts keys create "$KEY" --iam-account="$(account "$2")"
    echo
    echo "# Google Cloud Storage: key $(jq -r .private_key_id "$KEY") of $(account "$2") (google/storage.sh key $2)"
    [[ $2 == dev ]] && echo "export METAOS_STORAGE_BUCKET=$(bucket dev)"
    # One line, which single quotes keep as it is: the private key's newlines are \n in JSON
    echo "export $VARIABLE='$(jq -c . "$KEY")'"
    ;;

  *) usage ;;
esac
