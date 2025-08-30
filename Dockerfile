# docker build -t keycloak-iam .
FROM maven:3.9.6-eclipse-temurin-17 AS build-auth
WORKDIR /src
COPY authenticators/pom.xml authenticators/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -f authenticators/pom.xml dependency:go-offline
COPY authenticators/ authenticators/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -f authenticators/pom.xml package
FROM node:20-alpine AS build-theme
RUN apk add --no-cache openjdk17 maven
WORKDIR /src/keycloakify
COPY keycloakify/package*.json ./
RUN corepack enable && corepack prepare yarn@1.22.22 --activate
RUN npm ci
COPY keycloakify/ ./
RUN npm run  build-keycloak-theme
FROM quay.io/keycloak/keycloak:26.3
WORKDIR /opt/keycloak
COPY --chown=1000:0 --from=build-auth /src/authenticators/target/*.jar /opt/keycloak/providers/
COPY --chown=1000:0 --from=build-theme /src/keycloakify/dist_keycloak/*.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev", "--import-realm", "--import-realm-override=true"]
