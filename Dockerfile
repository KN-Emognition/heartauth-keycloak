FROM quay.io/keycloak/keycloak:24.0.2 as builder
COPY providers/ /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:24.0.2
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY realms/ /opt/keycloak/data/import/
ENV KEYCLOAK_ADMIN=admin KEYCLOAK_ADMIN_PASSWORD=admin
CMD ["start", "--http-enabled=true", "--http-port=8080", "--import-realm"]
