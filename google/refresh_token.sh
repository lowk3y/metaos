#!/usr/bin/env bash
# A refresh token for MetaOS's Google bot account (GOOGLE_BOT_EMAIL), which has edit access to the "Ogrodje" Drive
# folder. MetaOS turns it into access tokens itself, so this is one-time - re-run it only when Google revokes the token
# (access removed at https://myaccount.google.com/connections, or unused for 6 months).
#
# Sign in as the bot, not as yourself. Google then redirects to a port nothing listens on, so the browser shows an
# error page: copy the URL from its address bar back into the terminal.
#
# From the environment (.envrc):
#   GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET  the "Desktop app" OAuth client of the Google Cloud project
#   GOOGLE_BOT_EMAIL                        optional, preselects the account on Google's sign-in page
set -euo pipefail

: "${GOOGLE_CLIENT_ID:?}" "${GOOGLE_CLIENT_SECRET:?}"

REDIRECT=http://127.0.0.1:8765
# Docs API accepts the Drive scope too
SCOPE=https://www.googleapis.com/auth/drive

# PKCE
VERIFIER=$(openssl rand -hex 48)
CHALLENGE=$(printf %s "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')

# access_type=offline + prompt=consent, or Google leaves the refresh token out
QUERY=$(jq -rn \
  --arg client_id "$GOOGLE_CLIENT_ID" --arg redirect_uri "$REDIRECT" --arg response_type code --arg scope "$SCOPE" \
  --arg access_type offline --arg prompt consent --arg login_hint "${GOOGLE_BOT_EMAIL:-}" \
  --arg code_challenge "$CHALLENGE" --arg code_challenge_method S256 \
  '$ARGS.named | to_entries | map("\(.key)=\(.value | @uri)") | join("&")')
AUTH_URL="https://accounts.google.com/o/oauth2/v2/auth?$QUERY"

echo "Sign in as ${GOOGLE_BOT_EMAIL:-the bot account} at:"
echo
echo "  $AUTH_URL"
echo
command -v open >/dev/null && open "$AUTH_URL"
read -rp "URL the browser ended up on: " RETURNED

# Still URL-encoded, which is what the form body below wants
CODE=$(sed -nE 's/.*[?&]code=([^&]+).*/\1/p' <<<"$RETURNED")
[[ -n $CODE ]] || { echo "No code= in that URL" >&2; exit 1; }

TOKENS=$(curl -sS https://oauth2.googleapis.com/token \
  -d "code=$CODE" -d grant_type=authorization_code \
  --data-urlencode "client_id=$GOOGLE_CLIENT_ID" --data-urlencode "client_secret=$GOOGLE_CLIENT_SECRET" \
  --data-urlencode "redirect_uri=$REDIRECT" --data-urlencode "code_verifier=$VERIFIER")
REFRESH_TOKEN=$(jq -er .refresh_token <<<"$TOKENS") || { echo "$TOKENS" >&2; exit 1; }
ACCESS_TOKEN=$(jq -r .access_token <<<"$TOKENS")

drive() { curl -sS --fail-with-body -G -H "Authorization: Bearer $ACCESS_TOKEN" "https://www.googleapis.com/drive/v3/$1" "${@:2}"; }

# Catches signing in with the wrong account, and shows the folders shared with it - Ogrodje's ID goes into the config
ACCOUNT=$(drive about --data-urlencode 'fields=user(emailAddress)' | jq -r .user.emailAddress)
echo
echo "Signed in as $ACCOUNT"
if [[ -n ${GOOGLE_BOT_EMAIL:-} && $ACCOUNT != "$GOOGLE_BOT_EMAIL" ]]; then
  echo "That isn't $GOOGLE_BOT_EMAIL - remove the app at https://myaccount.google.com/connections and re-run" >&2
  exit 1
fi
echo "Folders shared with it:"
drive files --data-urlencode "q=sharedWithMe = true and mimeType = 'application/vnd.google-apps.folder'" \
  --data-urlencode 'fields=files(id,name,owners(emailAddress))' |
  jq -r '.files[] | "  \(.name)  id=\(.id)  owner=\(.owners[0].emailAddress)"'

echo
echo "Add to .envrc (and to production's secret):"
echo "  export GOOGLE_REFRESH_TOKEN='$REFRESH_TOKEN'"
