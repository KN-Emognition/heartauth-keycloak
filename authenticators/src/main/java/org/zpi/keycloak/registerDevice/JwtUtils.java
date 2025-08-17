package org.zpi.keycloak.registerDevice;

import org.keycloak.common.util.Time;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.models.KeyManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.Urls;
import org.keycloak.urls.UrlType;
import org.keycloak.util.JsonSerialization;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class JwtUtils {

    private JwtUtils() {
    }

    public static String mintJwt(KeycloakSession session,
                                 RealmModel realm,
                                 String alg,
                                 Duration ttl,
                                 Map<String, Object> custom) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(realm, "realm");
        if (alg == null || alg.isBlank()) alg = Algorithm.RS256;

        KeyManager keys = session.keys();
        KeyWrapper key = keys.getActiveKey(realm, KeyUse.SIG, alg);
        if (key == null) {
            throw new IllegalStateException("No active realm signing key for alg=" + alg);
        }

        long iat = Time.currentTime();
        long exp = iat + ttl.toSeconds();

        String issuer = Urls.realmIssuer(
                session.getContext().getUri(UrlType.FRONTEND).getBaseUri(),
                realm.getName()
        ).toString();

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("iat", iat);
        claims.put("exp", exp);
        if (custom != null) claims.putAll(custom);

        try {
            return new JWSBuilder()
                    .kid(key.getKid())
                    .type("JWT")
                    .jsonContent(claims)
                    .sign(new AsymmetricSignatureSignerContext(key));
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    public static String mintPairingJwt(KeycloakSession session,
                                        RealmModel realm,
                                        String jti,
                                        String audience,
                                        String subjectUserIdOrNull,
                                        Duration ttl) {
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("aud", audience);
        custom.put("jti", jti);
        if (subjectUserIdOrNull != null && !subjectUserIdOrNull.isBlank()) {
            custom.put("sub", subjectUserIdOrNull);
        }
        return mintJwt(session, realm, Algorithm.RS256, ttl, custom);
    }
}
