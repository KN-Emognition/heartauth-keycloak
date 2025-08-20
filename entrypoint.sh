#!/usr/bin/env bash
set -euo pipefail

: "${KEYCLOAK_ADMIN:=admin}"
: "${KEYCLOAK_ADMIN_PASSWORD:=admin}"
: "${KC_REDIRECT_URIS_JSON:='["*"]'}"
: "${KC_WEB_ORIGINS_JSON:='["*"]'}"
: "${KC_CLIENT_ID:=APP-ID}"
: "${KC_CLIENT_SECRET:=APP-SECRET}"         # used only if your client is confidential
: "${KC_REALM:=heartauth}"
: "${KC_REALM_DISPLAY_NAME:=HeartAuth Showcase}"
: "${KC_ORCH_BASE_URL:=https://orchestrator.example.com}"
: "${KC_TOKEN_TTL:=300}"
: "${KC_PKCE_METHOD:=S256}"
: "${KC_DEMO_USERNAME:=Nervarien}"
: "${KC_DEMO_PASSWORD:=Nervarien}"
: "${KC_DEMO_EMAIL:=nervarien@example.com}"
: "${KC_DEMO_FIRST_NAME:=Jan}"
: "${KC_DEMO_LAST_NAME:=Nowak}"
: "${KC_IMPORT_REALM_OVERRIDE:=true}"
mkdir -p /opt/keycloak/data/import
export KC_REDIRECT_URIS_JSON KC_WEB_ORIGINS_JSON \
       KC_CLIENT_ID KC_CLIENT_SECRET \
       KC_REALM KC_REALM_DISPLAY_NAME KC_ORCH_BASE_URL KC_TOKEN_TTL KC_PKCE_METHOD \
       KC_DEMO_USERNAME KC_DEMO_PASSWORD KC_DEMO_EMAIL KC_DEMO_FIRST_NAME KC_DEMO_LAST_NAME
envsubst < /opt/keycloak/realm-template.json > /opt/keycloak/data/import/realm.json
HOST_OPTS=()
if [[ -n "${KC_HOSTNAME_URL:-}" ]]; then
  HOST_OPTS+=( "--hostname-url=${KC_HOSTNAME_URL}" )
fi
exec /opt/keycloak/bin/kc.sh start-dev \
  --import-realm \
  --import-realm-override="${KC_IMPORT_REALM_OVERRIDE}" \
  --hostname-strict="${KC_HOSTNAME_STRICT:-false}" \
  --hostname-backchannel-dynamic="${KC_HOSTNAME_BACKCHANNEL_DYNAMIC:-true}" \
  "${HOST_OPTS[@]}"
