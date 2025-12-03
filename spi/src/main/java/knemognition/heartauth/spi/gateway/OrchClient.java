package knemognition.heartauth.spi.gateway;

import knemognition.heartauth.orchestrator.ApiClient;
import knemognition.heartauth.orchestrator.ApiException;
import knemognition.heartauth.orchestrator.api.ChallengeApi;
import knemognition.heartauth.orchestrator.api.PairingApi;
import knemognition.heartauth.orchestrator.model.*;
import knemognition.heartauth.spi.config.HaConfig;
import knemognition.heartauth.spi.config.HaConstants;
import knemognition.heartauth.spi.config.HaRealmSettings;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.keycloak.models.RealmModel;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;


public final class OrchClient {

    private static final Logger LOG = Logger.getLogger(OrchClient.class);

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
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            }
        };

        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(HaConstants.TRANSPORT_TIMEOUT)
                .proxy(noProxy);

        ApiClient apiClient = new ApiClient()
                .setHttpClientBuilder(http)
                .setConnectTimeout(HaConstants.TRANSPORT_TIMEOUT)
                .setReadTimeout(HaConstants.TRANSPORT_TIMEOUT);

        apiClient.updateBaseUri(cfg.orchestratorBaseUri());

        apiClient.setRequestInterceptor(b -> {
            String routeId = (String) MDC.get(HaConstants.MDC_ROUTE_ID);
            if (routeId == null) routeId = UUID.randomUUID()
                    .toString();
            b.header(HaConstants.AUTH_HEADER, cfg.apiKey());
            b.header(HaConstants.REQUEST_ID_HEADER, routeId);
            b.timeout(HaConstants.TRANSPORT_TIMEOUT);
        });

        this.challengeApi = new ChallengeApi(apiClient);
        this.pairingApi = new PairingApi(apiClient);
        this.pairingTtlSeconds = cfg.pairingTtlSeconds();
        this.challengeTtlSeconds = cfg.challengeTtlSeconds();
    }

    public CreateChallengeResponseDto createChallenge(UUID userId) throws ApiException {
        String routeId = createRouteId();
        try {
            LOG.infof("routeId=%s Sent Create Challenge Request", routeId);
            CreateChallengeRequestDto req = CreateChallengeRequestDto.builder()
                    .userId(userId)
                    .ttlSeconds(challengeTtlSeconds)
                    .build();

            CreateChallengeResponseDto resp = challengeApi.createChallenge(req);
            LOG.infof("routeId=%s Received Response to Challenge Create", routeId);
            return resp;
        } finally {
            MDC.remove(HaConstants.MDC_ROUTE_ID);
        }
    }


    public CreatePairingResponseDto createPairing(UUID userId, String username) throws ApiException {
        String routeId = createRouteId();
        try {
            LOG.infof("routeId=%s Sent Create Pairing Request", routeId);
            CreatePairingRequestDto req = CreatePairingRequestDto.builder()
                    .userId(userId)
                    .ttlSeconds(pairingTtlSeconds)
                    .username(username)
                    .build();

            CreatePairingResponseDto resp = pairingApi.createPairing(req);
            LOG.infof("routeId=%s Received Response to Pairing Create", routeId);
            return resp;
        } finally {
            MDC.remove(HaConstants.MDC_ROUTE_ID);
        }
    }

    public StatusResponseDto getChallengeStatus(UUID challengeId) throws ApiException {
        String routeId = createRouteId();
        try {
            LOG.infof("routeId=%s Sent Get Challenge Status Request", routeId);
            StatusResponseDto resp = challengeApi.getChallengeStatus(challengeId);
            LOG.infof("routeId=%s Received Response to Challenge Status", routeId);
            return resp;
        } finally {
            MDC.remove(HaConstants.MDC_ROUTE_ID);
        }
    }

    public StatusResponseDto getPairingStatus(UUID pairingId) throws ApiException {
        String routeId = createRouteId();
        try {
            LOG.infof("routeId=%s Sent Get Pairing Status Request", routeId);
            StatusResponseDto resp = pairingApi.getPairingStatus(pairingId);
            LOG.infof("routeId=%s Received Response to Pairing Status", routeId);
            return resp;
        } finally {
            MDC.remove(HaConstants.MDC_ROUTE_ID);
        }
    }


    private static String createRouteId() {
        String routeId = UUID.randomUUID()
                .toString();
        MDC.put(HaConstants.MDC_ROUTE_ID, routeId);
        return routeId;
    }
}
