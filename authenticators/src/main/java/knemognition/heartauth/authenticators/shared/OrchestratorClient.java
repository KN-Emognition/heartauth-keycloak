package knemognition.heartauth.authenticators.shared;

import knemognition.heartauth.orchestrator.api.ChallengeApi;
import knemognition.heartauth.orchestrator.invoker.ApiClient;
import knemognition.heartauth.orchestrator.invoker.ApiException;
import knemognition.heartauth.orchestrator.model.ChallengeCreateRequest;
import knemognition.heartauth.orchestrator.model.ChallengeCreateResponse;
import knemognition.heartauth.orchestrator.model.ChallengeStatusResponse;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jboss.logging.Logger;

import java.net.Proxy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class OrchestratorClient {
    private static final Logger LOG = Logger.getLogger(OrchestratorClient.class);
    private final ChallengeApi challengeApi;

    public OrchestratorClient(String baseUrl, String apiKey, Duration timeout) {
        OkHttpClient.Builder ok = new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);

        // Add X-Api-Key header (never log the value)
        if (apiKey != null && !apiKey.isBlank()) {
            Interceptor addApiKey = chain -> {
                Request r = chain.request().newBuilder()
                        .header("X-Api-Key", apiKey)
                        .build();
                return chain.proceed(r);
            };
            ok.addInterceptor(addApiKey);
        }

        // Correlation ID + timing/logging interceptor
        Interceptor logging = chain -> {
            String rid = UUID.randomUUID().toString();
            Request req = chain.request().newBuilder()
                    .header("X-Request-Id", rid)
                    .build();

            long startNs = System.nanoTime();
            try {
                okhttp3.Response resp = chain.proceed(req);
                long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                // Do not log headers/body to avoid secrets; log essentials only.
                LOG.debugf("HTTP %s %s -> %d (%d ms) rid=%s",
                        req.method(), req.url(), resp.code(), tookMs, rid);
                return resp;
            } catch (Exception ex) {
                long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                LOG.warnf(ex, "HTTP %s %s FAILED after %d ms rid=%s",
                        req.method(), req.url(), tookMs, rid);
                throw ex;
            }
        };
        ok.addInterceptor(logging);

        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(baseUrl);
        apiClient.setHttpClient(ok.build());

        this.challengeApi = new ChallengeApi(apiClient);

        LOG.debugf("OrchestratorClient init: baseUrl=%s timeout=%s apiKeyPresent=%s",
                baseUrl, timeout, (apiKey != null && !apiKey.isBlank()));
    }

    public UUID createChallenge(UUID userId, Integer ttlSeconds) throws ApiException {
        var req = new ChallengeCreateRequest();
        req.setUserId(userId);
        if (ttlSeconds != null) req.setTtlSeconds(ttlSeconds);

        long t0 = System.nanoTime();
        LOG.infof("createChallenge -> userId=%s ttlSeconds=%s", userId, ttlSeconds);
        try {
            ChallengeCreateResponse resp = challengeApi.internalChallengeCreate(req);
            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            LOG.infof("createChallenge OK: id=%s (%d ms)", resp.getChallengeId(), tookMs);
            return resp.getChallengeId();
        } catch (ApiException e) {
            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            // ApiException typically carries HTTP status and body from upstream.
            LOG.warnf(e, "createChallenge FAILED: status=%s body=%s (%d ms)",
                    e.getCode(), truncate(e.getResponseBody()), tookMs);
            throw e;
        }
    }

    public ChallengeStatusResponse getStatus(UUID challengeId, String kcSession) throws ApiException {
        long t0 = System.nanoTime();
        LOG.debugf("getStatus -> id=%s kcSession=%s", challengeId, kcSession);
        try {
            ChallengeStatusResponse resp = challengeApi.internalChallengeStatus(challengeId, kcSession);
            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            LOG.debugf("getStatus OK: id=%s state=%s (%d ms)", challengeId, resp.getStatus(), tookMs);
            return resp;
        } catch (ApiException e) {
            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            LOG.warnf(e, "getStatus FAILED: id=%s status=%s body=%s (%d ms)",
                    challengeId, e.getCode(), truncate(e.getResponseBody()), tookMs);
            throw e;
        }
    }

    // Avoid dumping huge/error bodies into logs
    private static String truncate(String s) {
        if (s == null) return null;
        final int MAX = 512;
        return (s.length() <= MAX) ? s : s.substring(0, MAX) + "…";
    }
}