#!/usr/bin/env bash
# The front-end's Keycloak client metaos-ui (fe/, with keycloak-js) and the scopes that let its tokens into /graphql,
# set up through Keycloak's admin API - for realms imported before metaos-realm.json had them, as Keycloak imports the
# file only once.
#
# metaos-ui is a public client, as a browser can't keep a secret, so it signs users in with PKCE. metaos:read and
# metaos:write put /graphql into its tokens' audience. They're the client's own scopes rather than realm defaults, as a
# client that registers itself (DCR) may ask for any realm default.
#
# Idempotent - re-running it updates the client and the scopes, e.g. with new URLs.
#
# From the environment:
#   KEYCLOAK_URL, KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD  see approval_gate.sh
#   METAOS_UI_URL   the front-end's public URL, defaults to https://metaos.ogrodje.si
#   METAOS_API_URL  public URL of /graphql, defaults to https://metaos.ogrodje.si/graphql
set -euo pipefail

UI=${METAOS_UI_URL:-https://metaos.ogrodje.si}
API=${METAOS_API_URL:-https://metaos.ogrodje.si/graphql}
CLIENT=metaos-ui

source "$(dirname "$0")/approval_gate.sh"

scope_id() {
  api GET /client-scopes | jq -r --arg n "$1" '.[] | select(.name == $n) | .id'
}

# upsert_scope NAME DESCRIPTION - the client scope NAME, with the mapper that adds /graphql to the audience
upsert_scope() {
  local id mapper existing
  id=$(scope_id "$1")
  if [ -z "$id" ]; then
    api POST /client-scopes "$(jq -n --arg n "$1" --arg d "$2" '{name: $n, description: $d, protocol: "openid-connect",
      attributes: {"include.in.token.scope": "true", "display.on.consent.screen": "false"}}')"
    id=$(scope_id "$1")
    echo "Created client scope $1"
  fi

  mapper=$(jq -n --arg api "$API" '{name: "metaos-api-audience", protocol: "openid-connect",
    protocolMapper: "oidc-audience-mapper", consentRequired: false,
    config: {"included.custom.audience": $api, "access.token.claim": "true", "introspection.token.claim": "true",
             "id.token.claim": "false", "lightweight.claim": "false"}}')
  existing=$(api GET "/client-scopes/$id/protocol-mappers/models" |
    jq -r '.[] | select(.name == "metaos-api-audience") | .id')
  if [ -n "$existing" ]; then
    api PUT "/client-scopes/$id/protocol-mappers/models/$existing" "$(jq --arg id "$existing" '.id = $id' <<<"$mapper")"
  else
    api POST "/client-scopes/$id/protocol-mappers/models" "$mapper"
  fi
  echo "$1 adds audience $API"
}

upsert_scope metaos:read "Read MetaOS through its GraphQL API"
upsert_scope metaos:write "Change MetaOS through its GraphQL API"

client=$(jq -n --arg c "$CLIENT" --arg ui "$UI" '{
  clientId: $c, name: "MetaOS", description: "The front-end (fe/), signing users in with keycloak-js", enabled: true,
  publicClient: true, standardFlowEnabled: true, implicitFlowEnabled: false, directAccessGrantsEnabled: false,
  serviceAccountsEnabled: false, redirectUris: ["\($ui)/*"], webOrigins: [$ui],
  attributes: {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": "+"}}')
id=$(api GET "/clients?clientId=$CLIENT" | jq -r '.[0].id // empty')
if [ -n "$id" ]; then
  api PUT "/clients/$id" "$client"
  echo "Updated client $CLIENT"
else
  api POST /clients "$client"
  id=$(api GET "/clients?clientId=$CLIENT" | jq -r '.[0].id')
  echo "Created client $CLIENT"
fi

# Its tokens always carry both scopes. mcp:tools, a realm default for the MCP clients, is no business of the front-end's.
for scope in metaos:read metaos:write; do
  api PUT "/clients/$id/default-client-scopes/$(scope_id "$scope")"
done
if api GET "/clients/$id/optional-client-scopes" | jq -e 'any(.[]; .name == "mcp:tools")' >/dev/null; then
  api DELETE "/clients/$id/optional-client-scopes/$(scope_id mcp:tools)"
fi
echo "$CLIENT signs users in at $UI"
