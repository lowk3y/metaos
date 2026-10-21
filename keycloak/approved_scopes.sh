#!/usr/bin/env bash
# Only approved users get MetaOS's scopes: maps mcp:tools, metaos:read and metaos:write to the realm role metaos-user,
# so Keycloak puts them in a token only when its user has the role. That keeps them away from anything else that can get
# a token from realm metaos: a client registered through DCR for its own service account (client_credentials, no user
# and no login, so no approval gate), a user whose approval was taken back, a password user an admin never approved.
# MetaOS checks the role as well (Security.kt), so this script and that check each close it on their own.
#
# The mappings take effect at once: from then on a user without the role gets none of the scopes. So it only reports
# by default - who would lose access, and any service account that has the role - and changes the realm only with
# --apply, which refuses while users without the role remain, unless --force says they are meant to lose access.
#
#   keycloak/approved_scopes.sh                   # report
#   keycloak/approved_scopes.sh --apply           # map the scopes, once everyone who should keep access has the role
#   keycloak/approved_scopes.sh --apply --force   # map them even though the users listed lose access
#
# Idempotent. From the environment: KEYCLOAK_URL, KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD (approval_gate.sh)
set -euo pipefail

apply=false force=false
for arg in "$@"; do
  case $arg in
    --apply) apply=true ;;
    --force) force=true ;;
    *) echo "Unknown argument $arg" >&2; exit 2 ;;
  esac
done

source "$(dirname "$0")/approval_gate.sh"

SCOPES=(mcp:tools metaos:read metaos:write)
MAX=10000

scope_id() {
  local id
  id=$(api GET /client-scopes | jq -r --arg n "$1" '.[] | select(.name == $n) | .id')
  [ -n "$id" ] || { echo "No client scope $1 in realm $REALM" >&2; exit 1; }
  echo "$id"
}

# All of a listing, or a failure - never a list cut off at MAX
listing() {
  local items sep='?'
  [[ $1 == *\?* ]] && sep='&'
  items=$(api GET "$1${sep}max=$((MAX + 1))")
  [ "$(jq length <<<"$items")" -le "$MAX" ] || { echo "More than $MAX entries at $1; page through them" >&2; exit 1; }
  echo "$items"
}

for scope in "${SCOPES[@]}"; do scope_id "$scope" >/dev/null; done

if api GET /roles | jq -e --arg r "$ROLE" 'any(.[]; .name == $r)' >/dev/null; then
  with_role=$(listing "/roles/$ROLE/users")
else
  with_role='[]'
fi
users=$(listing "/users?enabled=true")

without=$(jq -r --argjson in "$(jq '[.[].id]' <<<"$with_role")" \
  '.[] | select(.username | startswith("service-account-") | not) | select(.id as $id | $in | index($id) | not)
   | "  \(.username)\t\(.email // "")"' <<<"$users")
accounts=$(jq -r '.[] | select(.username | startswith("service-account-")) | "  \(.username)"' <<<"$with_role")

echo "Enabled users without $ROLE (they lose MetaOS once the scopes are mapped; grant it to those who should keep it):"
echo "${without:-  none}"
if [ -n "$accounts" ]; then
  echo "Service accounts WITH $ROLE (a client's own account should never have it; remove it, and look at the client):"
  echo "$accounts"
fi

$apply || { echo; echo "Report only; --apply maps the scopes."; exit 0; }
if [ -n "$without" ] && ! $force; then
  echo "Not applied: grant $ROLE to the users above who should keep access, or add --force." >&2
  exit 1
fi

# The role, unless the identity provider scripts made it already
if [ "$with_role" = '[]' ] && ! api GET /roles | jq -e --arg r "$ROLE" 'any(.[]; .name == $r)' >/dev/null; then
  api POST /roles "$(jq -n --arg r "$ROLE" '{name: $r, description: "May sign in to MetaOS with Discord or Google"}')"
  echo "Created role $ROLE"
fi
role=$(api GET "/roles/$ROLE")

for scope in "${SCOPES[@]}"; do
  id=$(scope_id "$scope")
  if api GET "/client-scopes/$id/scope-mappings/realm" | jq -e --arg r "$ROLE" 'any(.[]; .name == $r)' >/dev/null; then
    echo "$scope already needs $ROLE"
  else
    api POST "/client-scopes/$id/scope-mappings/realm" "[$role]"
    echo "$scope now needs $ROLE"
  fi
done

# Read back: every scope must now be mapped, or this did not do its job
for scope in "${SCOPES[@]}"; do
  api GET "/client-scopes/$(scope_id "$scope")/scope-mappings/realm" | jq -e --arg r "$ROLE" 'any(.[]; .name == $r)' >/dev/null ||
    { echo "$scope is still not mapped to $ROLE" >&2; exit 1; }
done
echo "All of ${SCOPES[*]} need $ROLE"
