# Keycloak for MetaOS: an optimized build with realm "metaos" baked in for --import-realm.
# Keep the version in step with compose.yml.
FROM quay.io/keycloak/keycloak:26.8.1 AS builder
# Build-time options only; hostname, database and credentials are runtime env (k8s/base/deployment-keycloak.yaml)
ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.1
COPY --from=builder /opt/keycloak/ /opt/keycloak/
# Imported on first start only - an existing realm is left untouched (see README, "Authentication")
COPY keycloak/metaos-realm.json /opt/keycloak/data/import/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
