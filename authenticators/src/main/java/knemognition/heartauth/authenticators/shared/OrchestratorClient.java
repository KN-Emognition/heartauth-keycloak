package knemognition.heartauth.authenticators.shared;

import knemognition.heartauth.orchestrator.api.ChallengeApi;
import knemognition.heartauth.orchestrator.api.PairingApi;
import knemognition.heartauth.orchestrator.invoker.ApiClient;
import knemognition.heartauth.orchestrator.invoker.ApiException;
import knemognition.heartauth.orchestrator.model.ChallengeCreateRequest;
import knemognition.heartauth.orchestrator.model.ChallengeStatusResponse;
import knemognition.heartauth.orchestrator.model.PairingStatusResponse;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jboss.logging.Logger;

import java.net.Proxy;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class OrchestratorClient {
    private static final Logger LOG = Logger.getLogger(OrchestratorClient.class);

    private final ApiClient apiClient;       // shared
    private final ChallengeApi challengeApi;
    private final PairingApi pairingApi;

    public OrchestratorClient(String baseUrl, String apiKey, Duration timeout) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is null or blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is null or blank");
        }
        Objects.requireNonNull(timeout, "timeout");

        OkHttpClient.Builder ok = new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);

        Interceptor addApiKey = chain -> {
            Request r = chain.request().newBuilder()
                    .header("X-Api-Key", apiKey)
                    .build();
            return chain.proceed(r);
        };
        Interceptor addRequestId = chain -> {
            Request r = chain.request().newBuilder()
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .build();
            return chain.proceed(r);
        };
        ok.addInterceptor(addApiKey);
        ok.addInterceptor(addRequestId);

        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(baseUrl);
        this.apiClient.setHttpClient(ok.build());

        this.challengeApi = new ChallengeApi(apiClient);
        this.pairingApi = new PairingApi(apiClient);

    }

    public ChallengeApi challenge() {
        return challengeApi;
    }

    public PairingApi pairing() {
        return pairingApi;
    }

    public ApiClient apiClient() {
        return apiClient;
    }

    public UUID createChallenge(UUID userId, Integer ttlSeconds) throws ApiException {
        var req = new ChallengeCreateRequest();
        req.setUserId(userId);
        if (ttlSeconds != null) req.setTtlSeconds(ttlSeconds);
        return challengeApi.internalChallengeCreate(req).getChallengeId();
    }

    public ChallengeStatusResponse getChallengeStatus(UUID challengeId, String kcSession) throws ApiException {
        return challengeApi.internalChallengeStatus(challengeId, kcSession);
    }


    public PairingStatusResponse getPairingStatus(UUID pairingId, String kcSession) throws ApiException {
        return pairingApi.internalPairingStatus(pairingId, kcSession);
    }

}
