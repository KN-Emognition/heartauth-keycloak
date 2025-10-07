package knemognition.heartauth.spi.config;

public final class HaAttributes {
    private HaAttributes() {
    }

    public static final String STATUS_BASE_URL = "ha-orch.base-url";
    public static final String STATUS_API_KEY = "ha-orch.api-key";
    public static final String PAIRING_TTL_SECONDS = "ha-pairing.ttl-seconds";
    public static final String CHALLENGE_TTL_SECONDS = "ha-challenge.ttl-seconds";
}
