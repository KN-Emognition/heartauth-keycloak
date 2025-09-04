package knemognition.heartauth.authenticators.shared;

import knemognition.heartauth.orchestrator.api.ChallengeApi;
import knemognition.heartauth.orchestrator.api.PairingApi;
import knemognition.heartauth.orchestrator.invoker.ApiClient;
import knemognition.heartauth.orchestrator.invoker.ApiException;
import knemognition.heartauth.orchestrator.model.ChallengeCreateRequest;

import knemognition.heartauth.orchestrator.model.PairingCreateRequest;
import knemognition.heartauth.orchestrator.model.PairingCreateResponse;
import knemognition.heartauth.orchestrator.model.StatusResponse;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.keycloak.models.RealmModel;

import java.net.Proxy;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class OrchestratorClient {

    private final ChallengeApi challengeApi;
    private final PairingApi pairingApi;

    public static String rAttr(RealmModel realm, String key, String def) {
        String v = realm.getAttribute(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static int rAttrInt(RealmModel realm, String key, int def) {
        try {
            String v = realm.getAttribute(key);
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }

    public static OrchestratorClient client(RealmModel realm) {
        String baseUrl = rAttr(realm, "status.base-url", "");
        String apiKey = rAttr(realm, "status.api-key", "");
        int timeoutMs = rAttrInt(realm, "status.timeout-ms", 5000);

        if (baseUrl.isEmpty()) throw new IllegalStateException("Missing realm attribute: status.base-url");
        if (apiKey.isEmpty()) throw new IllegalStateException("Missing realm attribute: status.api-key");

        return new OrchestratorClient(baseUrl, apiKey, Duration.ofMillis(timeoutMs));
    }

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

        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(baseUrl);
        apiClient.setHttpClient(ok.build());

        this.challengeApi = new ChallengeApi(apiClient);
        this.pairingApi = new PairingApi(apiClient);

    }


    public UUID createChallenge(UUID userId, Integer ttlSeconds) throws ApiException {
        var req = new ChallengeCreateRequest();
        req.setUserId(userId);
        if (ttlSeconds != null) req.setTtlSeconds(ttlSeconds);
        return challengeApi.internalChallengeCreate(req).getChallengeId();
    }

    public PairingCreateResponse createPairing(UUID userId) throws ApiException {
        var req = new PairingCreateRequest();
        req.setUserId(userId);
        return pairingApi.internalPairingCreate(req);
    }

    public StatusResponse getChallengeStatus(UUID challengeId, String kcSession) throws ApiException {
        return challengeApi.internalChallengeStatus(challengeId, kcSession);
    }


    public StatusResponse getPairingStatus(UUID pairingId, String kcSession) throws ApiException {
        return pairingApi.internalPairingStatus(pairingId, kcSession);
    }

}
