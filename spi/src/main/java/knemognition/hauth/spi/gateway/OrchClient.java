package knemognition.hauth.spi.gateway;

import knemognition.hauth.spi.config.HaConfig;
import knemognition.hauth.spi.config.HaRealmSettings;
import knemognition.hauth.orchestrator.api.ChallengeApi;
import knemognition.hauth.orchestrator.api.PairingApi;
import knemognition.hauth.orchestrator.invoker.ApiClient;
import knemognition.hauth.orchestrator.invoker.ApiException;
import knemognition.hauth.orchestrator.model.*;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.keycloak.models.RealmModel;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class OrchClient {

    private static final Logger LOG = Logger.getLogger(OrchClient.class);

    private static final Duration TRANSPORT_TIMEOUT = Duration.ofSeconds(30);
    private static final String AUTH_HEADER = "X-Api-Key";
    private static final String REQUEST_ID_HEADER = "X-Route-Id";
    private static final String MDC_ROUTE_ID = "routeId";

    private final ChallengeApi challengeApi;
    private final PairingApi pairingApi;

    private final int pairingTtlSeconds;
    private final int challengeTtlSeconds;

    public static OrchClient clientFromRealm(RealmModel realm) {
        return new OrchClient(HaRealmSettings.load(realm));
    }

    private OrchClient(HaConfig cfg) {
        ProxySelector noProxy = new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
            }
        };

        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(TRANSPORT_TIMEOUT)
                .proxy(noProxy);

        ApiClient apiClient = new ApiClient()
                .setHttpClientBuilder(http)
                .setConnectTimeout(TRANSPORT_TIMEOUT)
                .setReadTimeout(TRANSPORT_TIMEOUT);

        apiClient.updateBaseUri(cfg.orchestratorBaseUri());

        apiClient.setRequestInterceptor(b -> {
            String routeId = (String) MDC.get(MDC_ROUTE_ID);
            if (routeId == null) routeId = UUID.randomUUID().toString();
            b.header(AUTH_HEADER, cfg.apiKey());
            b.header(REQUEST_ID_HEADER, routeId);
            b.timeout(TRANSPORT_TIMEOUT);
        });

        this.challengeApi = new ChallengeApi(apiClient);
        this.pairingApi = new PairingApi(apiClient);
        this.pairingTtlSeconds = cfg.pairingTtlSeconds();
        this.challengeTtlSeconds = cfg.challengeTtlSeconds();
    }

    public UUID createChallenge(UUID userId) throws ApiException {
        String routeId = createRouteId();
        try {
            LOG.infof("routeId=%s Sent Create Challenge Request", routeId);
            ChallengeCreateRequest req = new ChallengeCreateRequest()
                    .userId(userId)
                    .ttlSeconds(challengeTtlSeconds);

            ChallengeCreateResponse resp = challengeApi.internalChallengeCreate(req);
            LOG.infof("routeId=%s Received Response to Challenge Create", routeId);
            return resp.getChallengeId();
        } finally {
            MDC.remove(MDC_ROUTE_ID);
        }
    }


    public PairingCreateResponse createPairing(UUID userId) throws ApiException {
        String routeId = createRouteId();
        try {
            LOG.infof("routeId=%s Sent Create Pairing Request", routeId);
            PairingCreateRequest req = new PairingCreateRequest()
                    .userId(userId)
                    .ttlSeconds(pairingTtlSeconds);

            PairingCreateResponse resp = pairingApi.internalPairingCreate(req);
            LOG.infof("routeId=%s Received Response to Pairing Create", routeId);
            return resp;
        } finally {
            MDC.remove(MDC_ROUTE_ID);
        }
    }

    public StatusResponse getChallengeStatus(UUID challengeId, String kcSession) throws ApiException {
        String routeId = createRouteId();
        try {
            LOG.infof("routeId=%s Sent Get Challenge Status Request", routeId);
            StatusResponse resp = challengeApi.internalChallengeStatus(challengeId, kcSession);
            LOG.infof("routeId=%s Received Response to Challenge Status", routeId);
            return resp;
        } finally {
            MDC.remove(MDC_ROUTE_ID);
        }
    }

    public StatusResponse getPairingStatus(UUID pairingId, String kcSession) throws ApiException {
        String routeId = createRouteId();
        try {
            LOG.infof("routeId=%s Sent Get Pairing Status Request", routeId);
            StatusResponse resp = pairingApi.internalPairingStatus(pairingId, kcSession);
            LOG.infof("routeId=%s Received Response to Pairing Status", routeId);
            return resp;
        } finally {
            MDC.remove(MDC_ROUTE_ID);
        }
    }


    private static String createRouteId() {
        String routeId = UUID.randomUUID().toString();
        MDC.put(MDC_ROUTE_ID, routeId);
        return routeId;
    }
}
