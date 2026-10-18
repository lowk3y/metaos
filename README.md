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

## Authors

- [Oto Brglez](https://github.com/otobrglez)

[Ogrodje]: https://ogrodje.si
[Nix]: https://nixos.org/
[devenv]: https://devenv.sh/



