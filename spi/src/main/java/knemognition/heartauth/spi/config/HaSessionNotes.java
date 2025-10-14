package knemognition.heartauth.spi.config;

/**
 * Shared keys for data stored in Keycloak authentication session notes.
 */
public final class HaSessionNotes {

    private HaSessionNotes() {
    }

    public static final String ECG_CHALLENGE_ID = "ecg.challengeId";
    public static final String PAIRING_JTI = "ecg.pair.jti";
    public static final String PAIRING_JWT = "ecg.pair.jwt";
}

