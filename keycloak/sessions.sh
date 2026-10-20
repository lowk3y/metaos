#!/usr/bin/env bash
# How long users stay signed in to realm metaos, set through Keycloak's admin API - for realms imported before
# metaos-realm.json had it, as Keycloak imports the file only once.
#
# A sign-in is a Keycloak session, and refresh tokens last as long as it: it ends after IDLE without a refresh, and after
# MAX in any case. The front-end refreshes its token whenever it asks the API something, and MCP clients whenever they
# call a tool. Keycloak's defaults, 30 minutes and 10 hours, signed people out of both several times a day. Access
# tokens stay at 5 minutes, as they're what every request carries.
#
# Idempotent.
#
# From the environment:
#   KEYCLOAK_URL, KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD  see approval_gate.sh
#   SSO_SESSION_IDLE  seconds, defaults to 7 days
#   SSO_SESSION_MAX   seconds, defaults to 30 days
set -euo pipefail

IDLE=${SSO_SESSION_IDLE:-604800}
MAX=${SSO_SESSION_MAX:-2592000}

source "$(dirname "$0")/approval_gate.sh"

# Only these two: Keycloak leaves out of an update what it doesn't have
api PUT "" "$(jq -n --argjson idle "$IDLE" --argjson max "$MAX" '{ssoSessionIdleTimeout: $idle, ssoSessionMaxLifespan: $max}')"
api GET "" | jq -r '"Sessions end after \(.ssoSessionIdleTimeout / 86400) days idle, \(.ssoSessionMaxLifespan / 86400) days at most"'
