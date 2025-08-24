# docker build -t keycloak-iam .
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /src
COPY authenticators/pom.xml authenticators/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -f authenticators/pom.xml dependency:go-offline
COPY authenticators/ authenticators/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests \
        -f authenticators/pom.xml package
FROM quay.io/keycloak/keycloak:26.3
COPY --chown=1000:0 --from=build /src/authenticators/target/*.jar /opt/keycloak/providers/
 COPY --chown=1000:0 themes/ /opt/keycloak/themes/
RUN /opt/keycloak/bin/kc.sh build
USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev", "--import-realm", "--import-realm-override=true"]
