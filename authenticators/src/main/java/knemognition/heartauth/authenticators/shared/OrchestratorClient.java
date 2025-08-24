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

        if (apiKey != null && !apiKey.isBlank()) {
            Interceptor addApiKey = chain -> {
                Request r = chain.request().newBuilder()
                        .header("X-Api-Key", apiKey)
                        .build();
                return chain.proceed(r);
            };
            ok.addInterceptor(addApiKey);
        }

        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(baseUrl);          // <- base URL
        apiClient.setHttpClient(ok.build());     // <- inject OkHttpClient

        this.challengeApi = new ChallengeApi(apiClient);

        LOG.debugf("OrchestratorClient init: baseUrl=%s timeout=%s apiKeyPresent=%s",
                baseUrl, timeout, (apiKey != null && !apiKey.isBlank()));
    }

    public UUID createChallenge(UUID userId, Integer ttlSeconds) throws ApiException {
        var req = new ChallengeCreateRequest();
        req.setUserId(userId);
        if (ttlSeconds != null) req.setTtlSeconds(ttlSeconds);

        LOG.infof("createChallenge: userId=%s ttlSeconds=%s", userId, ttlSeconds);
        ChallengeCreateResponse resp = challengeApi.internalChallengeCreate(req);
        LOG.infof("createChallenge: got id=%s", resp.getChallengeId());
        return resp.getChallengeId();
    }

    public ChallengeStatusResponse getStatus(UUID challengeId, String kcSession) throws ApiException {
        LOG.debugf("getStatus: id=%s kcSession=%s", challengeId, kcSession);
        return challengeApi.internalChallengeStatus(challengeId, kcSession);
    }
}
