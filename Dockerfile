# syntax=docker/dockerfile:1.7
# docker build -t keycloak-iam .
# syntax=docker/dockerfile:1.7

########## 1) Build your providers ##########
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /src

# Cache deps
COPY authenticators/pom.xml authenticators/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -f authenticators/pom.xml dependency:go-offline

# Build
COPY authenticators/ authenticators/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests \
        -Dorchestrator.spec=/src/authenticators/api/orc/orchestrator.yml \
        -f authenticators/pom.xml package

########## 2) Final Keycloak image ##########
FROM quay.io/keycloak/keycloak:26.3

# Copy only provider jars
COPY --chown=1000:0 --from=build /src/authenticators/target/*.jar /opt/keycloak/providers/

# (Optional) If you have a custom theme folder to ship in the image, uncomment:
 COPY --chown=1000:0 themes/ /opt/keycloak/themes/

RUN /opt/keycloak/bin/kc.sh build

USER 1000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev", "--import-realm", "--import-realm-override=true"]
