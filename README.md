# MetaOS

MetaOS is a autonomous, agentic framework for managing and operating media organisations.

MetaOS was designed for scaling, optimising and helping grow the [Ogrodje] organisation.

## Development && Infrastructure notes

- This project uses [Nix] and [devenv] for dependency isolation and reproducability.
- The project is written in Kotlin and uses Spring Boot at its core.
- Services and infrastructure is deployed via Kubernetes

```bash
gradle :bootRun
gradle -t :classes
```

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

### Deployment

Production runs on the ogrodje-one Kubernetes cluster, namespace `metaos-prod`: the app at `https://metaos.ogrodje.si`
and its Keycloak at `https://metaos-auth.ogrodje.si`. Pushes to `master` deploy through
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml); [`k8s/README.md`](k8s/README.md) has the manifests,
the one-time setup and the `just` recipes for deploying by hand. [`DEPLOY.md`](DEPLOY.md) explains the setup, the
decisions behind it and what is left to do.

## Authors

- [Oto Brglez](https://github.com/otobrglez)

[Ogrodje]: https://ogrodje.si
[Nix]: https://nixos.org/
[devenv]: https://devenv.sh/
[Keycloak]: https://www.keycloak.org/



