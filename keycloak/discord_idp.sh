#!/usr/bin/env bash
# "Sign in with Discord" for realm metaos, set up through Keycloak's admin API (DEPLOY.md, "Discord login").
# Keycloak has no Discord provider, so this uses its generic OAuth v2 one against Discord's /users/@me.
#
# Anyone with a Discord account can sign in, and every user of the realm may use every MCP tool - so a post-login flow
# denies Discord logins until an admin grants the user the realm role "metaos-user". Emails aren't trusted either:
# Discord also returns unverified ones, and a verified email is what links a login to a Person (Security.kt).
# Approving someone = granting the role + turning on "Email verified" once you know the address is theirs.
#
# Idempotent - re-running it updates the identity provider, e.g. with a new client secret.
#
# From the environment:
#   KEYCLOAK_URL                                defaults to https://metaos-auth.ogrodje.si
#   KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD  an admin of the master realm
#   DISCORD_CLIENT_ID, DISCORD_CLIENT_SECRET    from the Discord application's OAuth2 page; its redirect is
#                                               $KEYCLOAK_URL/realms/metaos/broker/discord/endpoint
set -euo pipefail

KC=${KEYCLOAK_URL:-https://metaos-auth.ogrodje.si}
REALM=metaos
ROLE=metaos-user
FLOW=discord-post-login

: "${KEYCLOAK_ADMIN_USERNAME:?}" "${KEYCLOAK_ADMIN_PASSWORD:?}"
: "${DISCORD_CLIENT_ID:?}" "${DISCORD_CLIENT_SECRET:?}"

TOKEN=$(curl -sS --fail-with-body "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  --data-urlencode "username=$KEYCLOAK_ADMIN_USERNAME" --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" |
  jq -r .access_token)

# api METHOD PATH [JSON] - PATH is relative to the realm's admin API
api() {
  curl -sS --fail-with-body -X "$1" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    "$KC/admin/realms/$REALM$2" ${3:+-d "$3"}
}

# The role that lets a Discord user in
if ! api GET /roles | jq -e --arg r "$ROLE" 'any(.[]; .name == $r)' >/dev/null; then
  api POST /roles "$(jq -n --arg r "$ROLE" '{name: $r, description: "May sign in to MetaOS with Discord"}')"
  echo "Created role $ROLE"
fi

# Post-login flow: a conditional subflow that denies access when the user lacks the role
if ! api GET /authentication/flows | jq -e --arg f "$FLOW" 'any(.[]; .alias == $f)' >/dev/null; then
  api POST /authentication/flows "$(jq -n --arg f "$FLOW" --arg r "$ROLE" \
    '{alias: $f, providerId: "basic-flow", topLevel: true, builtIn: false,
      description: "Denies Discord logins without the \($r) role"}')"
  api POST "/authentication/flows/$FLOW/executions/flow" \
    '{"alias": "discord-require-role", "type": "basic-flow", "provider": "registration-page-form"}'
  api POST /authentication/flows/discord-require-role/executions/execution '{"provider": "conditional-user-role"}'
  api POST /authentication/flows/discord-require-role/executions/execution '{"provider": "deny-access-authenticator"}'
  # Without a step that succeeds, a flow whose condition didn't match fails as "Invalid username or password"
  api POST "/authentication/flows/$FLOW/executions/execution" '{"provider": "allow-access-authenticator"}'

  # Executions are created DISABLED; the subflow is CONDITIONAL, everything else REQUIRED
  api GET "/authentication/flows/$FLOW/executions" | jq -c '.[]' | while read -r execution; do
    requirement=$(jq -r 'if .authenticationFlow then "CONDITIONAL" else "REQUIRED" end' <<<"$execution")
    api PUT "/authentication/flows/$FLOW/executions" "$(jq -c --arg q "$requirement" '.requirement = $q' <<<"$execution")"
  done

  executions=$(api GET "/authentication/flows/$FLOW/executions")
  condition=$(jq -r '.[] | select(.providerId == "conditional-user-role") | .id' <<<"$executions")
  deny=$(jq -r '.[] | select(.providerId == "deny-access-authenticator") | .id' <<<"$executions")
  api POST "/authentication/executions/$condition/config" "$(jq -n --arg r "$ROLE" \
    '{alias: "without-\($r)", config: {condUserRole: $r, negate: "true"}}')"
  api POST "/authentication/executions/$deny/config" \
    '{"alias": "awaiting-approval", "config": {"denyErrorMessage": "Your account is waiting for approval by a MetaOS admin."}}'
  echo "Created flow $FLOW"
fi

# The identity provider. Discord's /users/@me has id, username, global_name and email (with scope "email").
idp=$(jq -n --arg id "$DISCORD_CLIENT_ID" --arg secret "$DISCORD_CLIENT_SECRET" --arg flow "$FLOW" '{
  alias: "discord", displayName: "Discord", providerId: "oauth2", enabled: true,
  trustEmail: false, postBrokerLoginFlowAlias: $flow,
  config: {
    authorizationUrl: "https://discord.com/oauth2/authorize",
    tokenUrl: "https://discord.com/api/oauth2/token",
    userInfoUrl: "https://discord.com/api/users/@me",
    clientId: $id, clientSecret: $secret, clientAuthMethod: "client_secret_post",
    defaultScope: "identify email",
    userIDClaim: "id", userNameClaim: "username", fullNameClaim: "global_name", emailClaim: "email",
    syncMode: "IMPORT"
  }}')
if api GET /identity-provider/instances | jq -e 'any(.[]; .alias == "discord")' >/dev/null; then
  api PUT /identity-provider/instances/discord "$idp"
  echo "Updated identity provider discord"
else
  api POST /identity-provider/instances "$idp"
  echo "Created identity provider discord"
fi
echo "Discord's redirect URI: $KC/realms/$REALM/broker/discord/endpoint"
