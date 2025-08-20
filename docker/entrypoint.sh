#!/usr/bin/env bash
set -euo pipefail

# Defaults (override via env)
: "${KEYCLOAK_ADMIN:=admin}"
: "${KEYCLOAK_ADMIN_PASSWORD:=admin}"

: "${KC_IMPORT_REALM_NAME:=heartauth}"
: "${KC_IMPORT_REALM_DISPLAY_NAME:=HeartAuth Showcase}"
: "${KC_LOGIN_THEME:=heartAuth}"
: "${KC_BROWSER_FLOW:=heartAuth-browser}"
: "${KC_REGISTRATION_FLOW:=heartAuth-register}"

# Client-related (comma-separated lists for URIs/origins)
: "${KC_CLIENT_ID:=APP-ID}"
: "${KC_PUBLIC_CLIENT:=true}"
: "${KC_REDIRECT_URIS:=*}"
: "${KC_WEB_ORIGINS:=*}"
: "${KC_PKCE_METHOD:=S256}"

# Custom authenticators config
: "${KC_ORCH_BASE_URL:=https://orchestrator.example.com}"
: "${KC_TOKEN_TTL:=300}"

# Demo user (optional, disable by setting KC_CREATE_DEMO_USER=false)
: "${KC_CREATE_DEMO_USER:=true}"
: "${KC_DEMO_USERNAME:=Nervarien}"
: "${KC_DEMO_PASSWORD:=Nervarien}"
: "${KC_DEMO_EMAIL:=nervarien@example.com}"
: "${KC_DEMO_FIRST_NAME:=Jan}"
: "${KC_DEMO_LAST_NAME:=Nowak}"
: "${KC_CLIENT_SECRET:=APP-SECRET}"


# Security headers
: "${KC_XFRAME_OPTIONS:=SAMEORIGIN}"
: "${KC_CSP:=frame-ancestors *; frame-src 'self'; object-src 'none';}"

# Convert env (CSV or JSON) to a JSON array using jq only
to_json_array() {
  v="${1:-*}"
  # If it looks like JSON already (starts with '['), pass through
  case "$v" in
    \[*\]) printf '%s' "$v" ;;
    *)     printf '%s' "$v" | jq -R -s 'split(",") | map(gsub("^\\s+|\\s+$";""))' ;;
  esac
}

redirect_json=$(to_json_array "${KC_REDIRECT_URIS:-*}")
origins_json=$(to_json_array  "${KC_WEB_ORIGINS:-*}")


# Generate realm.json from template using jq patches
tmp_realm="/opt/keycloak/data/import/realm.json"
mkdir -p /opt/keycloak/data/import

jq --arg realm "$KC_IMPORT_REALM_NAME" \
   --arg displayName "$KC_IMPORT_REALM_DISPLAY_NAME" \
   --arg loginTheme "$KC_LOGIN_THEME" \
   --arg browserFlow "$KC_BROWSER_FLOW" \
   --arg registrationFlow "$KC_REGISTRATION_FLOW" \
   --arg clientId "$KC_CLIENT_ID" \
   --argjson publicClient $( [[ "$KC_PUBLIC_CLIENT" == "true" ]] && echo true || echo false ) \
   --argjson redirectUris "$redirect_json" \
   --argjson webOrigins "$origins_json" \
   --arg pkceMethod "$KC_PKCE_METHOD" \
   --arg orchBaseUrl "$KC_ORCH_BASE_URL" \
   --arg tokenTtl "$KC_TOKEN_TTL" \
   --arg xfo "$KC_XFRAME_OPTIONS" \
   --arg csp "$KC_CSP" \
   --arg clientSecret "$KC_CLIENT_SECRET" \
   '
   .realm = $realm
 | .displayName = $displayName
 | .loginTheme = $loginTheme
 | .browserFlow = $browserFlow
 | .registrationFlow = $registrationFlow
 | .clients = (
     .clients
     | map(
         if .clientId == $clientId then
           .publicClient = $publicClient
         | .redirectUris = $redirectUris
         | .webOrigins   = $webOrigins
         | ( if $publicClient
             then del(.secret)
             else .secret = $clientSecret
             end )
         | ( if ($publicClient and ($pkceMethod|length>0))  # only set PKCE for public client with a non-empty method
             then .attributes["pkce.code.challenge.method"] = $pkceMethod
             else (if .attributes then del(.attributes["pkce.code.challenge.method"]) else . end)
             end )
         else .
         end
       )
   )
 | .browserSecurityHeaders.xFrameOptions = $xfo
 | .browserSecurityHeaders.contentSecurityPolicy = $csp
 | .authenticatorConfig = (
     .authenticatorConfig
     | map(
         if .alias == "register-device-config"
           then .config.orchBaseUrl = $orchBaseUrl
                | .config.tokenTtl    = $tokenTtl
           else .
           end
       )
   )
 ' /opt/keycloak/realm-template.json > "$tmp_realm"
 
# Optionally inject demo user
if [[ "${KC_CREATE_DEMO_USER}" == "true" ]]; then
  jq --arg user "$KC_DEMO_USERNAME" \
     --arg pass "$KC_DEMO_PASSWORD" \
     --arg email "$KC_DEMO_EMAIL" \
     --arg fn "$KC_DEMO_FIRST_NAME" \
     --arg ln "$KC_DEMO_LAST_NAME" \
     '
     .users = [
       {
         "username": $user,
         "enabled": true,
         "email": $email,
         "emailVerified": true,
         "firstName": $fn,
         "lastName": $ln,
         "credentials": [ { "type": "password", "value": $pass, "temporary": false } ],
         "requiredActions": []
       }
     ]
     ' "$tmp_realm" > "${tmp_realm}.tmp" && mv "${tmp_realm}.tmp" "$tmp_realm"
fi

HOSTNAME_ARGS=()
if [[ -n "${KC_HOSTNAME_URL:-}" ]]; then
  scheme="$(echo "$KC_HOSTNAME_URL" | sed -E 's#^([a-zA-Z][a-zA-Z0-9+.-]*)://.*#\1#')"
  host="$(echo "$KC_HOSTNAME_URL"   | sed -E 's#^[a-zA-Z][a-zA-Z0-9+.-]*://([^/:]+).*#\1#')"
  port="$(echo "$KC_HOSTNAME_URL"   | sed -E 's#^[a-zA-Z][a-zA-Z0-9+.-]*://[^/:]+:([0-9]+).*#\1#')"

  HOSTNAME_ARGS+=( --hostname="${host}" )

  if [[ "$scheme" == "http" ]]; then
    # Use provided port or default to 8080 in dev
    HOSTNAME_ARGS+=( --http-enabled=true )
    HOSTNAME_ARGS+=( --http-port="${port:-${KC_HTTP_PORT:-8080}}" )
  else
    # HTTPS in dev needs certs; if you *do* have them, uncomment & set paths:
    # HOSTNAME_ARGS+=( --https-port="${port:-8443}" )
    # HOSTNAME_ARGS+=( --https-certificate-file=/path/to/cert.pem )
    # HOSTNAME_ARGS+=( --https-certificate-key-file=/path/to/key.pem )
    echo "Warning: HTTPS hostname URL detected but no cert flags provided; prefer HTTP in dev." >&2
  fi
else
  HOSTNAME_ARGS+=( --hostname="${KC_HOSTNAME:-127.0.0.1}" )
  HOSTNAME_ARGS+=( --http-enabled=true )
  HOSTNAME_ARGS+=( --http-port="${KC_HTTP_PORT:-8080}" )
fi
exec /opt/keycloak/bin/kc.sh start-dev \
  --hostname-strict="${KC_HOSTNAME_STRICT:-false}" \
  --hostname-backchannel-dynamic="${KC_HOSTNAME_BACKCHANNEL_DYNAMIC:-true}" \
  --import-realm \
  --import-realm-override="${KC_IMPORT_REALM_OVERRIDE:-true}" \
  "${HOSTNAME_ARGS[@]}"