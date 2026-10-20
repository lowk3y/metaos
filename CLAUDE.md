# MetaOS

MetaOS is a autonomous, agentic framework for managing and operating media organisations.

MetaOS was designed for scaling, optimising and helping grow the [Ogrodje] organisation.

## Development && Infrastructure notes

- This project uses Nix and devenv for dependency isolation and reproducability.
- The project is written in Kotlin and uses Spring Boot at its core.
- Services and infrastructure is deployed via Kubernetes
- **MCP tools and the Usage page stay in sync.** Whenever an MCP tool (`@McpTool`) is added, renamed, removed or
  changes what it does, update the front-end's Usage page in the same change: the tool's entry in
  [`fe/src/tools.ts`](fe/src/tools.ts) (name, group and one-line description). `UsageToolsTest` fails on a missing
  or extra name, but can't tell when a description no longer fits.

### Front-end

The web front-end lives in [`fe/`](fe): Vue 3 with TypeScript, built by Vite, with Pinia for state and Vue Router.
Node and Yarn (Berry) come from devenv (`languages.javascript` in `devenv.nix`). Yarn installs into `node_modules`
(`nodeLinker: node-modules` in `fe/.yarnrc.yml`), as Vite doesn't support Plug'n'Play: under it pinia couldn't find its
`@vue/devtools-api` peer dependency.

```bash
cd fe
yarn            # install
yarn dev        # dev server with hot reload, http://localhost:5173
yarn build      # type-check (vue-tsc) and build into fe/dist
```

- The devenv shell runs `yarn install` in `fe/` when `yarn.lock` has changed, and `devenv up` runs `yarn dev`. Vite
  proxies `/graphql` and `/config.json` to MetaOS on `:8080`, so in development too the browser sees one origin.
- `./gradlew bootJar` builds `fe/` (`feInstall`, `feBuild`) with the devenv's Node and Yarn, so it needs the devenv
  shell, and puts it into the jar's `static/`. [`Frontend.kt`](src/main/kotlin/si/ogrodje/metaos/Frontend.kt) serves
  it: the files, and `index.html` for the router's paths (`createWebHistory`); its files and pages are public, while
  its data needs a token. Runs and tests don't build `fe/`.
- `/config.json` tells the front-end where users sign in (the Keycloak issuer) and as which client
  (`metaos.frontend.client-id`), and where MCP clients connect (`METAOS_MCP_URL`), so one build works in every
  environment.
- The Usage page (`/usage`) shows how to install the MCP server and lists its tools by hand, from
  [`fe/src/tools.ts`](fe/src/tools.ts), which has to follow every change to the tools (see above).
- Data comes from the GraphQL API at `/graphql` ([`schema.graphqls`](src/main/resources/graphql/schema.graphqls),
  [`GraphQL.kt`](src/main/kotlin/si/ogrodje/metaos/GraphQL.kt)), through `fe/src/graphql.ts`. It's open to anyone, as
  some of it is public (`releasedEpisodes`); a bearer token says who is asking (`me`). What isn't public needs
  `metaos:read`: whole queries with `@PreAuthorize`, and fields that public episodes and sessions also have (history,
  docs, addresses, ...) come back empty for anyone else.
- Signed in, episode titles and session labels lead to their pages, `/episodes/<code or ID>` and `/sessions/<ID>`:
  everything about them, and their history on a date rail (`HistoryTimeline.vue`), from the audit trail as
  [`History.kt`](src/main/kotlin/si/ogrodje/metaos/History.kt) tells it.
- Episode ideas (`IDEA`) have a page of their own, `/ideas`, from the `ideas` query, which needs `metaos:read`, as
  does its link in the header; the `episodes` query, and so the Episodes page, leaves them out.
- Users sign in with keycloak-js (`fe/src/stores/auth.ts`), straight to Discord or Google (`idpHint`). On load it
  checks for a Keycloak session in a hidden iframe of `fe/public/silent-check-sso.html`, which is why MetaOS's pages
  may be framed by MetaOS itself (`X-Frame-Options: SAMEORIGIN`).

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
- The front-end signs users in as `metaos-ui`, a public client with PKCE. Its tokens carry `metaos:read` and
  `metaos:write`, which put the `/graphql` URL into `aud` (and don't get them into `/mcp`). They're the client's own
  scopes, not realm defaults, as MCP clients registering themselves could ask for any realm default.
  [`keycloak/frontend_client.sh`](keycloak/frontend_client.sh) sets them up in a realm imported without them, e.g.
  production's (`METAOS_UI_URL` and `METAOS_API_URL` default to production's).
- Dev login: `dev` / `dev`.
- A sign-in lasts 7 days without use and 30 days at most (`ssoSessionIdleTimeout`, `ssoSessionMaxLifespan`; for
  realms imported before, [`keycloak/sessions.sh`](keycloak/sessions.sh)), and refresh tokens as long. Access tokens
  last 5 minutes; keycloak-js keeps them in memory and refreshes them before each request (`fe/src/stores/auth.ts`),
  and only signs the page out when Keycloak refuses the refresh token, not when it can't be reached.
- `METAOS_MCP_URL` (default `http://localhost:8080/mcp`) and `METAOS_API_URL` (default
  `http://localhost:8080/graphql`) must be the public URLs of `/mcp` and `/graphql`; they are used by both Keycloak
  (token audience) and the app (audience check). `METAOS_UI_URL` (default `http://localhost:5173`) is where the
  front-end is, for `metaos-ui`'s redirect URIs. `KEYCLOAK_ISSUER_URI` points the app at another realm.
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
that started it; writes made any other way (psql, migrations) are logged without them. `AuditLog` reads it, for the
`get_history` and `list_recent_changes` MCP tools and for the history on the front-end's episode and session pages
(`History.kt`, which says each change in words).

A new table needs its own `<table>_audit` trigger, `AuditLog.history` needs to know about new link columns, and
`History.kt` how to say what a change to it means.

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

### Google Cloud Storage

MetaOS's own files (pictures, videos, anything) are in Google Cloud Storage, in project `ogrodje-metaos`, through
[`Storage.kt`](src/main/kotlin/si/ogrodje/metaos/Storage.kt). Unlike Drive and Gmail, this isn't the bot account: each
environment has its own bucket and its own service account, which can use only that bucket.
[`google/storage.sh`](google/storage.sh) `setup` creates them (it's idempotent) and `key dev|prod` makes the keys;
gcloud comes from the devenv shell.

| Bucket                | Service account       | For                                                                   |
|-----------------------|-----------------------|-----------------------------------------------------------------------|
| `ogrodje-metaos-dev`  | `metaos-storage-dev`  | development and tests; `test/` is emptied after a day; no soft delete |
| `ogrodje-metaos-prod` | `metaos-storage-prod` | production; deleted and replaced files are kept 30 days; Autoclass    |

- Both are in Frankfurt (`europe-west3`) and private. MetaOS hands out signed URLs instead, which it signs with the key
  itself: `uploadUrl` starts a resumable upload, so big videos go from the browser straight to the bucket, without
  MetaOS in between. The buckets' CORS allows that from the front-end's origins.
- `GOOGLE_STORAGE_KEY` (the service account's JSON key, on one line) and `METAOS_STORAGE_BUCKET` (`.envrc`) turn it on;
  it's off without the key. In production the key is in `metaos-secret` (`PROD_GOOGLE_STORAGE_KEY`,
  `k8s/set_secrets.sh`) and the bucket in `metaos-config`.
- Keys don't expire. The `storage` health check uses the key every hour and reports a deleted one there, in
  `metaos_storage_connected` and in the log.
- `GoogleCloudStorageTest` runs against the dev bucket when its key is in the environment, under `test/`.

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
- On the front-end's episode and session pages, a linked folder links to Koofr's web app (`app.koofr.net/app/storage/
  <place>?path=...`; a shared place has the same ID for everyone it's shared with), and shows its photos, eight at a
  time with arrows to the rest (`PhotoStrip.vue`): those right in it, then those in the folders right in it
  ([`KoofrPhotos.kt`](src/main/kotlin/si/ogrodje/metaos/KoofrPhotos.kt)). Koofr makes the thumbnails; MetaOS passes
  them on at `/koofr/thumbnail`, behind `metaos:read` and only for those photos, so the page fetches them with its
  token, only for the photos it shows and the next ones. In lists, Koofr's logo links to an episode's or session's folders,
  as the doc icon does to its Google Docs; the API batches the folders, and asks Koofr only for place names and photos.
- The `koofr` health check uses it every hour and reports a rejected password there, in `metaos_koofr_connected` and in
  the log. `KoofrTest` runs against the real account (read-only) when the app password is in the environment.

### Dropbox

The bot account has Dropbox too ([`Dropbox.kt`](src/main/kotlin/si/ogrodje/metaos/Dropbox.kt)), for the folders shared
with it, like Artwork, which has the episodes' artwork. As with Koofr, `DropboxMCP` works only in those: it lists,
searches, reads text, shows pictures (`view_dropbox_image`, as an image MCP clients give their model), uploads, creates
folders and moves or renames, but never deletes or replaces anything unasked.

- It goes through MetaOS's own Dropbox app (scoped access, Full Dropbox), with a refresh token of the bot's from
  [`dropbox/refresh_token.sh`](dropbox/refresh_token.sh), which also says how to make the app. Dropbox's API never takes
  the account's password.
- `DROPBOX_APP_KEY`, `DROPBOX_APP_SECRET` and `DROPBOX_REFRESH_TOKEN` (`.envrc`) turn it on; it's off without the
  refresh token. In production they're in `metaos-secret` (`k8s/set_secrets.sh`).
- A place is a shared folder, by its ID, which is also its namespace's (`ns:<id>/path`): that finds it wherever the bot
  has it in its Dropbox. Search only sees the folders that are in the bot's Dropbox ("Add to Dropbox"), and finds them
  by where they are there.
- `DropboxFoldersMCP` links folders to episodes and recording sessions (`episode_dropbox_folders`,
  `session_dropbox_folders`), like Koofr's, but by Dropbox's ID for the folder: wherever it's moved or renamed in its
  place, also in the Dropbox app, it stays linked, and is listed where it is now (by ID, with the place as the path
  root). Only one deleted or moved out of its place is missing.
- Linking a folder also makes a Dropbox link to it that only those with access to it can open (`sharing.write`), kept
  with the link: Dropbox's web paths (`dropbox.com/home/...`) are in each viewer's own Dropbox, e.g. the owner has
  Artwork at `/Ogrodje/Artwork` and Iva at `/artwork`. It's a link for `no_one` (Dropbox's "only people invited"),
  which gives no access of its own; the bot's Basic account can make only those or public ones, and Dropbox refuses
  `members` for a new link. MetaOS never makes one for anyone else, and takes it back should Dropbox make it anyway;
  while Dropbox won't make one, the folder is linked without it (`urlError` says why), and linking it again makes it.
- On the front-end, a linked folder shows like a Koofr one: Dropbox's logo in lists, and in the episode's and session's
  details, opening its link for members, or else its path in Dropbox's web app as the bot has it. Lists ask Dropbox
  where the folders are once per place and minute (`DropboxFolderIndex`), not per folder.
- An episode's page shows the pictures in its folders as its Artwork, above its photos and like them, eight at a
  time: each whole, as covers,
  thumbnails and stories have different shapes, and by file name, as that says what it's for. As with Koofr's photos,
  they're those right in a folder, then those in the folders right in it ([`DropboxPictures.kt`](src/main/kotlin/si/ogrodje/metaos/DropboxPictures.kt)).
  Dropbox makes the thumbnails; MetaOS passes them on at `/dropbox/thumbnail`, behind `metaos:read` and only for those
  pictures, which it checks against the linked folders' IDs through `DropboxFolderIndex`.
- The `dropbox` health check uses it every hour and reports a rejected token there, in `metaos_dropbox_connected` and in
  the log. `DropboxTest` runs against the real account (read-only) when the refresh token is in the environment.

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



