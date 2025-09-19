# docker build -t keycloak-iam .
# --- Build SPI (Java) ---
FROM maven:3.9.6-eclipse-temurin-17 AS build-auth
WORKDIR /src
COPY spi/pom.xml spi/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -f spi/pom.xml dependency:go-offline
COPY spi/ spi/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -f spi/pom.xml package

# --- Build Keycloakify theme (Node) ---
FROM node:20-alpine AS build-theme
# keycloakify needs Java + Maven to package the theme jar
RUN apk add --no-cache openjdk17-jdk maven
WORKDIR /src/keycloakify

# install deps with cache
COPY keycloakify/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

# build with both npm and maven caches
COPY keycloakify/ ./
RUN --mount=type=cache,target=/root/.npm \
    --mount=type=cache,target=/root/.m2 \
    npm run build-keycloak-theme

# --- Final Keycloak image ---
FROM quay.io/keycloak/keycloak:26.3
WORKDIR /opt/keycloak

# Add providers/themes built above
COPY --chown=1000:0 --from=build-auth  /src/spi/target/*.jar              /opt/keycloak/providers/
COPY --chown=1000:0 --from=build-theme /src/keycloakify/dist_keycloak/*.jar /opt/keycloak/providers/
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_HTTP_RELATIVE_PATH=/keycloak \
    KC_HTTP_MANAGEMENT_RELATIVE_PATH=/
    
RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
# Use normal mode in K8s, not start-dev
CMD ["start", "--optimized", "--import-realm", "--import-realm-override=true"]

