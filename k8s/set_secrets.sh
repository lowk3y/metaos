#!/usr/bin/env bash
# What k8s/base needs but doesn't hold (k8s/README.md, "One-time setup"): the Postgres roles and databases on the
# shared CNPG cluster, the secrets the Deployments read and the registry pull secret. Idempotent - re-running it with
# new passwords rotates them (then `kubectl -n metaos-prod rollout restart deployment` to pick them up).
#
# From the environment (.envrc):
#   PROD_METAOS_DB_PASSWORD       the app's Postgres password
#   PROD_KEYCLOAK_DB_PASSWORD     Keycloak's Postgres password
#   PROD_KEYCLOAK_ADMIN_PASSWORD  Keycloak's bootstrap admin ("admin")
#   PROD_KEYCLOAK_DEV_PASSWORD    the realm's "dev" user from metaos-realm.json
#   DOCKER_REGISTRY_USERNAME, DOCKER_REGISTRY_PASSWORD
#   GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_DRIVE_REFRESH_TOKEN  the Google bot account's, as locally
#   GOOGLE_GMAIL_REFRESH_TOKEN    optional, Gmail stays off without it (google/refresh_token.sh)
# The DB passwords go into SQL literals, so keep them alphanumeric: `openssl rand -hex 24`.
set -euo pipefail

NS=metaos-prod
REGISTRY=registry.folk-decibel.ts.net

: "${PROD_METAOS_DB_PASSWORD:?}" "${PROD_KEYCLOAK_DB_PASSWORD:?}"
: "${PROD_KEYCLOAK_ADMIN_PASSWORD:?}" "${PROD_KEYCLOAK_DEV_PASSWORD:?}"
: "${DOCKER_REGISTRY_USERNAME:?}" "${DOCKER_REGISTRY_PASSWORD:?}"
: "${GOOGLE_CLIENT_ID:?}" "${GOOGLE_CLIENT_SECRET:?}" "${GOOGLE_DRIVE_REFRESH_TOKEN:?}"

cd "$(dirname "$0")"
kubectl apply -f base/namespace.yaml

# Roles and databases - names as in base/deployment-*.yaml. Each role owns its database, so the app's
# `CREATE EXTENSION citext` (V1__init.sql) works: citext is a trusted extension.
PRIMARY=$(kubectl get pod -n pg -l cnpg.io/cluster=main-cluster,cnpg.io/instanceRole=primary -o name)
kubectl exec -n pg -i "$PRIMARY" -- psql -U postgres -v ON_ERROR_STOP=1 -q postgres <<SQL
\set metaos_password '$PROD_METAOS_DB_PASSWORD'
\set keycloak_password '$PROD_KEYCLOAK_DB_PASSWORD'
SELECT format('CREATE ROLE %I LOGIN', r) FROM unnest(ARRAY['metaos', 'metaos_keycloak']) r
  WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) \gexec
ALTER ROLE metaos PASSWORD :'metaos_password';
ALTER ROLE metaos_keycloak PASSWORD :'keycloak_password';
SELECT format('CREATE DATABASE %I OWNER %I', d, d) FROM unnest(ARRAY['metaos', 'metaos_keycloak']) d
  WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = d) \gexec
SQL

kubectl create secret generic metaos-secret -n "$NS" \
  --from-literal=postgres_password="$PROD_METAOS_DB_PASSWORD" \
  --from-literal=google_client_id="$GOOGLE_CLIENT_ID" \
  --from-literal=google_client_secret="$GOOGLE_CLIENT_SECRET" \
  --from-literal=google_drive_refresh_token="$GOOGLE_DRIVE_REFRESH_TOKEN" \
  --from-literal=google_gmail_refresh_token="${GOOGLE_GMAIL_REFRESH_TOKEN:-}" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic keycloak-secret -n "$NS" \
  --from-literal=kc_db_password="$PROD_KEYCLOAK_DB_PASSWORD" \
  --from-literal=keycloak_admin_password="$PROD_KEYCLOAK_ADMIN_PASSWORD" \
  --from-literal=keycloak_dev_password="$PROD_KEYCLOAK_DEV_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

# On the namespace's default ServiceAccount, as in boo-prod, so no Deployment has to name it
kubectl create secret docker-registry regcred -n "$NS" \
  --docker-server="$REGISTRY" \
  --docker-username="$DOCKER_REGISTRY_USERNAME" \
  --docker-password="$DOCKER_REGISTRY_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl patch serviceaccount default -n "$NS" -p '{"imagePullSecrets":[{"name":"regcred"}]}'
