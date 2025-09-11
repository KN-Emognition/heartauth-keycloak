package knemognition.hauth.spi.config;

import org.keycloak.models.RealmModel;

import java.util.Objects;

public final class HaRealmSettings {
    private HaRealmSettings() {
    }

    public static HaConfig load(RealmModel realm) {
        Objects.requireNonNull(realm, "realm");

        String baseUri   = require(realm, HaAttributes.STATUS_BASE_URL);
        String apiKey    = require(realm, HaAttributes.STATUS_API_KEY);
        int pairingTtl   = requirePositiveInt(realm, HaAttributes.PAIRING_TTL_SECONDS);
        int challengeTtl = requirePositiveInt(realm, HaAttributes.CHALLENGE_TTL_SECONDS);

        return new HaConfig(baseUri, apiKey, pairingTtl, challengeTtl);
    }


    private static String require(RealmModel realm, String key) {
        String v = realm.getAttribute(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing realm attribute: " + key);
        }
        return v.trim();
    }

    private static int requirePositiveInt(RealmModel realm, String key) {
        String raw = realm.getAttribute(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Missing realm attribute: " + key);
        }
        String cleaned = raw.trim().replace("_", "");
        try {
            int val = Integer.parseInt(cleaned);
            if (val <= 0) {
                throw new IllegalStateException("Realm attribute '" + key + "' must be a positive integer, got: " + raw);
            }
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Realm attribute '" + key + "' must be an integer, got: " + raw, e);
        }
    }
}
