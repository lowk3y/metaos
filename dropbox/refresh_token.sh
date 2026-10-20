#!/usr/bin/env bash
# A refresh token for MetaOS's Dropbox bot account (DROPBOX_BOT_EMAIL), through MetaOS's own Dropbox app.
#
# The app, once: at https://www.dropbox.com/developers/apps, "Create app" with scoped access and "Full Dropbox" (an app
# folder can't see the folders shared with the bot). On its Permissions tab tick the scopes below and submit; its
# Settings tab has the app key and secret for DROPBOX_APP_KEY and DROPBOX_APP_SECRET.
#
# MetaOS turns the token into access tokens itself, so this is one-time - re-run it only when the token stops working
# (the app disconnected at https://www.dropbox.com/account/connected_apps, or its permissions changed).
#
# Sign in as the bot, not as yourself: in a private window if the browser is signed in to Dropbox as someone else.
# Dropbox then shows a code to copy back into the terminal.
#
# From the environment (.envrc):
#   DROPBOX_APP_KEY, DROPBOX_APP_SECRET  MetaOS's Dropbox app
#   DROPBOX_BOT_EMAIL                    optional, the account to expect; iva.ogrodje@gmail.com when not set
set -euo pipefail

: "${DROPBOX_APP_KEY:?}" "${DROPBOX_APP_SECRET:?}"
BOT_EMAIL=${DROPBOX_BOT_EMAIL:-iva.ogrodje@gmail.com}

# Who the bot is, the files in the folders shared with it (not deleting), and which folders those are
SCOPES=(account_info.read files.metadata.read files.content.read files.content.write sharing.read)

# token_access_type=offline, or Dropbox leaves the refresh token out
QUERY=$(jq -rn \
  --arg client_id "$DROPBOX_APP_KEY" --arg response_type code --arg token_access_type offline \
  --arg scope "${SCOPES[*]}" --arg force_reapprove true \
  '$ARGS.named | to_entries | map("\(.key)=\(.value | @uri)") | join("&")')
AUTH_URL="https://www.dropbox.com/oauth2/authorize?$QUERY"

echo "Sign in as $BOT_EMAIL at:"
echo
echo "  $AUTH_URL"
echo
command -v open >/dev/null && open "$AUTH_URL"
read -rp "Code Dropbox shows: " CODE
[[ -n $CODE ]] || { echo "No code" >&2; exit 1; }

TOKENS=$(curl -sS https://api.dropboxapi.com/oauth2/token -u "$DROPBOX_APP_KEY:$DROPBOX_APP_SECRET" \
  --data-urlencode "code=$CODE" -d grant_type=authorization_code)
REFRESH_TOKEN=$(jq -er .refresh_token <<<"$TOKENS") || { echo "$TOKENS" >&2; exit 1; }
ACCESS_TOKEN=$(jq -r .access_token <<<"$TOKENS")

# All of them, or MetaOS fails on what's missing: the app's Permissions tab has to have them ticked
GRANTED=" $(jq -r .scope <<<"$TOKENS") "
for scope in "${SCOPES[@]}"; do
  [[ $GRANTED == *" $scope "* ]] || { echo "Dropbox didn't grant $scope - tick it on the app's Permissions tab and re-run" >&2; exit 1; }
done

api() { curl -sS --fail-with-body -X POST -H "Authorization: Bearer $ACCESS_TOKEN" "$@"; }

# Catches signing in with the wrong account
ACCOUNT=$(api https://api.dropboxapi.com/2/users/get_current_account | jq -r .email)
echo
echo "Signed in as $ACCOUNT"
if [[ $ACCOUNT != "$BOT_EMAIL" ]]; then
  echo "That isn't $BOT_EMAIL - disconnect the app at https://www.dropbox.com/account/connected_apps and re-run" >&2
  exit 1
fi

# Search sees only those in the bot's Dropbox, so the others want adding there ("Add to Dropbox" on dropbox.com)
echo "Folders shared with it:"
api https://api.dropboxapi.com/2/sharing/list_folders -H 'Content-Type: application/json' -d '{"limit": 100}' |
  jq -r '.entries[] | "  \(.name)  id=\(.shared_folder_id)  \(.access_type[".tag"])  \(.path_display // "not in its Dropbox yet")"'

echo
echo "Add to .envrc (and to production's secret, k8s/set_secrets.sh):"
echo "  export DROPBOX_REFRESH_TOKEN='$REFRESH_TOKEN'"
