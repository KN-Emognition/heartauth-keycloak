package knemognition.heartauth.spi.config;

import java.time.Duration;

public final class HaConstants {
    private HaConstants() {
    }

    public static final String AUTH_HEADER = "X-API-Key";
    public static final String REQUEST_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_ROUTE_ID = "correlationId";
    public static final Duration TRANSPORT_TIMEOUT = Duration.ofSeconds(30);
}
