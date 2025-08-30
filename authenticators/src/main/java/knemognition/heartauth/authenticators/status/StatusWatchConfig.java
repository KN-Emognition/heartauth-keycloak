package knemognition.heartauth.authenticators.status;

public record StatusWatchConfig(
        String baseUrl,
        String apiKey,
        int timeoutMs,
        int minPeriodMs
) {
}