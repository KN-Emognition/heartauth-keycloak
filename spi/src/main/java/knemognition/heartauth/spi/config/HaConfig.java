package knemognition.heartauth.spi.config;

public record HaConfig(String orchestratorBaseUri, String apiKey, int pairingTtlSeconds,
                       int challengeTtlSeconds) {
}