package knemognition.heartauth.authenticators.registerDevice;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class JwtUtils {

    private JwtUtils() {
    }

    private static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates HS256 JWT with claims: sub, aud, iat, exp, jti.
     */
    static String mintHs256(String secret, String sub, String aud, long iat, long exp, String jti) throws Exception {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("JWT secret is blank");

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        // Keep it simple; values are controlled and require no escaping beyond quotes
        String payloadJson = new StringBuilder(128)
                .append("{")
                .append("\"sub\":\"").append(sub).append("\",")
                .append("\"iat\":").append(iat).append(",")
                .append("\"aud\":\"").append(aud).append("\",")
                .append("\"exp\":").append(exp).append(",")
                .append("\"jti\":\"").append(jti).append("\"")
                .append("}")
                .toString();

        String header = b64url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = b64url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        String signature = b64url(hmacSha256(secret.getBytes(StandardCharsets.UTF_8), signingInput));
        return signingInput + "." + signature;
    }
}
