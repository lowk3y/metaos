# MetaOS

MetaOS is a autonomous, agentic framework for managing and operating media organisations.

MetaOS was designed for scaling, optimising and helping grow the [Ogrodje] organisation.

## Installing MCP

```bash
claude mcp add --transport http ogrodje https://metaos.ogrodje.si/mcp
```

## Development && Infrastructure notes

- This project uses [Nix] and [devenv] for dependency isolation and reproducability.
- The project is written in Kotlin and uses Spring Boot at its core.
- Services and infrastructure is deployed via Kubernetes ([`k8s`](./k8s))

## Authors

- [Oto Brglez](https://github.com/otobrglez)

[Ogrodje]: https://ogrodje.si
[Nix]: https://nixos.org/
[devenv]: https://devenv.sh/
[Keycloak]: https://www.keycloak.org/



