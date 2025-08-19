# docker build -t keycloak-iam .
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /src
COPY authenticators/ ./authenticators/
RUN mvn -B -q -DskipTests -f authenticators/pom.xml dependency:go-offline
ARG SKIP_OPENAPI=false
ARG ORCH_SPEC=/src/authenticators/api/orc/orchestrator.yml
RUN if [ "$SKIP_OPENAPI" = "true" ]; then \
      mvn -B -q -DskipTests -Dskip.openapi=true -f authenticators/pom.xml package ; \
    else \
      mvn -B -q -DskipTests -Dorchestrator.spec=${ORCH_SPEC} -f authenticators/pom.xml package ; \
    fi
FROM ghcr.io/jqlang/jq:1.7 AS jqstage
FROM quay.io/keycloak/keycloak:24.0.2
COPY --chown=1000:0 --from=build /src/authenticators/target/*.jar /opt/keycloak/providers/
COPY --from=jqstage /jq /usr/local/bin/jq
ENV PATH="/usr/local/bin:${PATH}"
COPY --chown=1000:0 themes/ /opt/keycloak/themes/
COPY --chown=1000:0 realms/realm-template.json /opt/keycloak/realm-template.json
COPY --chown=1000:0 --chmod=0755 docker/entrypoint.sh /opt/keycloak/entrypoint.sh
RUN /opt/keycloak/bin/kc.sh build

USER 1000
ENTRYPOINT ["/opt/keycloak/entrypoint.sh"]
