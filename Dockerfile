# docker build -t keycloak-iam .
FROM maven:3.9.6-eclipse-temurin-17 AS build-auth
WORKDIR /src
COPY spi/pom.xml spi/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -f spi/pom.xml dependency:go-offline
COPY spi/ spi/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -f spi/pom.xml package

FROM node:20-alpine AS build-theme
RUN apk add --no-cache openjdk17-jdk maven
WORKDIR /src/keycloakify

COPY keycloakify/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY keycloakify/ ./
RUN --mount=type=cache,target=/root/.npm \
    --mount=type=cache,target=/root/.m2 \
    npm run build-keycloak-theme

FROM quay.io/keycloak/keycloak:26.3
WORKDIR /opt/keycloak

COPY --chown=1000:0 --from=build-auth  /src/spi/target/*.jar              /opt/keycloak/providers/
COPY --chown=1000:0 --from=build-theme /src/keycloakify/dist_keycloak/*.jar /opt/keycloak/providers/
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_HTTP_RELATIVE_PATH=/keycloak \
    KC_HTTP_MANAGEMENT_RELATIVE_PATH=/
    
RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm", "--import-realm-override=true"]

