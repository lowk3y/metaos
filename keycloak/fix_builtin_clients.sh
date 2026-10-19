#!/usr/bin/env bash
# Gives realm metaos's built-in clients (account console, admin console, ...) the client scopes a fresh realm gives them.
#
# Only for realms imported from metaos-realm.json before it set CreateDefaultClientScopes (DEPLOY.md, "Known
# trade-offs"): that import left Keycloak's own clients without any client scopes, so their tokens carry no roles and
# the account console at /realms/metaos/account fails with "Something went wrong" (403 from the account API). Clients
# that register through DCR aren't affected. Imports of the current file don't need this; it's idempotent either way.
#
# From the environment:
#   KEYCLOAK_URL                                      defaults to https://metaos-auth.ogrodje.si
#   KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD  an admin of the master realm
set -euo pipefail

KC=${KEYCLOAK_URL:-https://metaos-auth.ogrodje.si}
REALM=metaos
# Not realm-management: Keycloak refuses changes to it, and it never issues tokens
CLIENTS=(account account-console admin-cli broker security-admin-console)
# What Keycloak 26.8 assigns these clients in a fresh realm
DEFAULT_SCOPES=(acr basic email profile roles web-origins)
OPTIONAL_SCOPES=(address microprofile-jwt offline_access organization phone)

: "${KEYCLOAK_ADMIN_USERNAME:?}" "${KEYCLOAK_ADMIN_PASSWORD:?}"

TOKEN=$(curl -sS --fail-with-body "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  --data-urlencode "username=$KEYCLOAK_ADMIN_USERNAME" --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" |
  jq -r .access_token)

# api METHOD PATH - PATH is relative to the realm's admin API
api() {
  curl -sS --fail-with-body -X "$1" -H "Authorization: Bearer $TOKEN" "$KC/admin/realms/$REALM$2"
}

scopes=$(api GET /client-scopes)
scope_id() { jq -er --arg n "$1" '.[] | select(.name == $n) | .id' <<<"$scopes"; }

for client in "${CLIENTS[@]}"; do
  id=$(api GET "/clients?clientId=$client" | jq -er '.[0].id')
  for scope in "${DEFAULT_SCOPES[@]}"; do api PUT "/clients/$id/default-client-scopes/$(scope_id "$scope")"; done
  for scope in "${OPTIONAL_SCOPES[@]}"; do api PUT "/clients/$id/optional-client-scopes/$(scope_id "$scope")"; done
  echo "$client: ${#DEFAULT_SCOPES[@]} default, ${#OPTIONAL_SCOPES[@]} optional scopes"
done
