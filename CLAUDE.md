# MetaOS

MetaOS is a autonomous, agentic framework for managing and operating media organisations.

MetaOS was designed for scaling, optimising and helping grow the [Ogrodje] organisation.

## Development && Infrastructure notes

- This project uses Nix and devenv for dependency isolation and reproducability.
- The project is written in Kotlin and uses Spring Boot at its core.
- Services and infrastructure is deployed via Kubernetes

### Authentication (Keycloak)

The MCP endpoint (`/mcp`) is an OAuth2 resource server. [Keycloak] from `compose.yml` is the identity provider /
authorization server, with realm `metaos` imported from [`keycloak/metaos-realm.json`](keycloak/metaos-realm.json).
Keycloak persists into the `keycloak` database of the `postgres` service (same credentials as the app, from `.envrc`);
the one-shot `keycloak-db-init` service creates that database when it is missing.

```bash
docker compose up -d keycloak   # http://localhost:8180 (admin console: admin / admin)
```

- MCP clients (Claude Code, MCP Inspector, ...) discover Keycloak via `/.well-known/oauth-protected-resource/mcp`,
  register themselves with Dynamic Client Registration (`localhost` redirect URIs only) and log the user in.
- Tokens need the `mcp:tools` scope; it also puts the MCP URL into `aud`, which the server checks.
- Dev login: `dev` / `dev`.
- `METAOS_MCP_URL` (default `http://localhost:8080/mcp`) must be the public URL of `/mcp`; it is used by both Keycloak
  (token audience) and the app (audience check). `KEYCLOAK_ISSUER_URI` points the app at another realm.
- The realm is only imported when it doesn't exist yet. To re-import after editing the JSON (wipes all Keycloak data,
  including registered MCP clients):

  ```bash
  docker compose stop keycloak
  docker compose exec postgres sh -c 'dropdb -U "$POSTGRES_USER" keycloak'
  docker compose up -d keycloak
  ```

### Audit trail

Postgres triggers ([`V6__audit_log.sql`](src/main/resources/db/migration/V6__audit_log.sql)) write every insert, update
and delete on the domain tables to `audit_log`, with the old and new row as JSON. `AuditingTransactionManager` tags each
read-write transaction with the signed-in user (`sub`, `preferred_username`), their OAuth client (`azp`) and the MCP tool
that started it; writes made any other way (psql, migrations) are logged without them. The `get_history` and
`list_recent_changes` MCP tools read it.

A new table needs its own `<table>_audit` trigger, and `get_history` needs to know about new link columns.

### Google (Drive and Gmail)

MetaOS uses Google as a bot account, `iva.ogrodje@gmail.com`, through the "Desktop app" OAuth client "MetaOS" of
Google Cloud project `ogrodje-metaos` and Google's Java client libraries ([`Google.kt`](src/main/kotlin/si/ogrodje/metaos/Google.kt)).
Drive and Gmail have a refresh token each, from [`google/refresh_token.sh`](google/refresh_token.sh) `[drive|gmail]`:
Google revokes tokens with Gmail access when the account's password changes, and that shouldn't take Drive down too.

- **Drive** ([`Drive.kt`](src/main/kotlin/si/ogrodje/metaos/Drive.kt)): Ogrodje's folder is shared with the bot.
  `DocumentsMCP` has the tools and only touches what's inside the folder; docs are linked to episodes and recording
  sessions by Drive file ID (`episode_documents`, `session_documents`).
- **Gmail** ([`Gmail.kt`](src/main/kotlin/si/ogrodje/metaos/Gmail.kt)): the bot's mailbox - search, threads, drafts,
  sending. Not exposed as MCP tools.
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_DRIVE_REFRESH_TOKEN`, `METAOS_DRIVE_ROOT_FOLDER_ID` and
  `GOOGLE_GMAIL_REFRESH_TOKEN` (`.envrc`) turn them on; each is off without its refresh token. In production they come
  from `metaos-secret` (`k8s/set_secrets.sh`) and `metaos-config`.
- The refresh tokens don't expire with time. The `drive` and `gmail` health checks use them every hour, as Google
  drops tokens that go unused for six months, and report a revoked one there, in `metaos_<drive|gmail>_connected` and
  in the log.
- `GoogleDriveTest` and `GmailTest` run against the real account when its credentials are in the environment; Gmail's
  only makes and deletes a draft.

### Koofr

MetaOS also uses the bot account in Koofr ([`Koofr.kt`](src/main/kotlin/si/ogrodje/metaos/Koofr.kt)), through
Koofr's Java SDK (from JitPack, as it isn't on Maven Central). It works only in the folders other Koofr users share with
the bot; `KoofrMCP` lists, searches and reads them, uploads, creates folders and moves or renames, but never deletes or
replaces anything unasked.

- `KOOFR_EMAIL` and `KOOFR_APP_PASSWORD` (`.envrc`) turn it on; it's off without the app password, which is made at
  https://app.koofr.net/app/admin/preferences/password while signed in as the bot. In production the email is in
  `metaos-config` and the app password in `metaos-secret` (`k8s/set_secrets.sh`).
- `KoofrFoldersMCP` links folders to episodes and recording sessions (`episode_koofr_folders`,
  `session_koofr_folders`). Koofr has no file IDs, so a link is a place (mount ID) and a path: moves through
  `move_koofr_file` take the links along, while a folder moved in the Koofr app leaves its links listed as missing.
- The `koofr` health check uses it every hour and reports a rejected password there, in `metaos_koofr_connected` and in
  the log. `KoofrTest` runs against the real account (read-only) when the app password is in the environment.

### Deployment

Production runs on the ogrodje-one Kubernetes cluster, namespace `metaos-prod`: the app at `https://metaos.ogrodje.si`
and its Keycloak at `https://metaos-auth.ogrodje.si`. Pushes to `master` deploy through
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml); [`k8s/README.md`](k8s/README.md) has the manifests,
the one-time setup and the `just` recipes for deploying by hand. [`DEPLOY.md`](DEPLOY.md) explains the setup, the
decisions behind it and what is left to do.

[Ogrodje]: https://ogrodje.si
[Nix]: https://nixos.org/
[devenv]: https://devenv.sh/
[Keycloak]: https://www.keycloak.org/



