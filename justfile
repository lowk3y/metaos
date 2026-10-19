# MetaOS images and their deploy (k8s/README.md). The registry and the cluster are on the tailnet, so these need
# Tailscale up. Images are tagged with `git describe`, as .github/workflows/deploy.yml does.

registry := "registry.folk-decibel.ts.net"
app_image := registry / "metaos/metaos"
keycloak_image := registry / "metaos/keycloak"
ns := "metaos-prod"

# The boot jar docker/Dockerfile copies.
jar:
    ./gradlew bootJar

# linux/amd64 is what the cluster runs.
# Build both images locally.
images: jar
    #!/usr/bin/env bash
    set -euo pipefail
    TAG=$(git describe --tags --always --dirty)
    docker buildx build --platform linux/amd64 -f docker/Dockerfile \
      -t "{{app_image}}:latest" -t "{{app_image}}:$TAG" --load .
    docker buildx build --platform linux/amd64 -f docker/keycloak.Dockerfile \
      -t "{{keycloak_image}}:latest" -t "{{keycloak_image}}:$TAG" --load .

# Build and push both images, and point the kustomization at the new tag.
push: jar
    #!/usr/bin/env bash
    set -euo pipefail
    TAG=$(git describe --tags --always --dirty)
    echo "Building and pushing $TAG"
    docker buildx build --platform linux/amd64 -f docker/Dockerfile \
      -t "{{app_image}}:latest" -t "{{app_image}}:$TAG" --push .
    docker buildx build --platform linux/amd64 -f docker/keycloak.Dockerfile \
      -t "{{keycloak_image}}:latest" -t "{{keycloak_image}}:$TAG" --push .
    cd k8s/base && kustomize edit set image "{{app_image}}:$TAG" "{{keycloak_image}}:$TAG"

# Apply the manifests and wait for the rollout.
deploy:
    kubectl apply -k k8s/base/
    kubectl rollout status deployment/keycloak -n {{ns}} --timeout=5m
    kubectl rollout status deployment/metaos -n {{ns}} --timeout=5m

# What the cluster would apply, without applying it.
manifests:
    kubectl kustomize k8s/base/

# The app's logs, followed.
logs *ARGS:
    kubectl logs -n {{ns}} deployment/metaos --follow --tail=100 {{ARGS}}
