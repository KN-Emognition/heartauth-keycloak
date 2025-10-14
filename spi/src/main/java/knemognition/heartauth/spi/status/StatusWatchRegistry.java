package knemognition.heartauth.spi.status;

import jakarta.ws.rs.sse.SseEventSink;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active SSE sinks for authentication flows so they can be closed
 * explicitly when the user abandons a screen or a flow is restarted.
 */
public final class StatusWatchRegistry {

    public static final String TYPE_ECG = "ecg";
    public static final String TYPE_PAIRING = "pairing";

    private static final ConcurrentHashMap<String, WatchEntry> SINKS = new ConcurrentHashMap<>();
    private static final Set<String> CLOSE_REQUESTS = ConcurrentHashMap.newKeySet();

    private StatusWatchRegistry() {
    }

    public static void register(String type,
                                String rootId,
                                String tabId,
                                String entityId,
                                SseEventSink sink) {
        String key = buildKey(type, rootId, tabId, entityId);
        if (key.isEmpty() || sink == null) return;
        CLOSE_REQUESTS.remove(key);
        SINKS.compute(key, (k, existing) -> {
            WatchEntry entry = existing;
            if (entry == null) {
                entry = new WatchEntry();
            }
            entry.sinks.add(sink);
            return entry;
        });
    }

    public static void unregister(String type,
                                  String rootId,
                                  String tabId,
                                  String entityId,
                                  SseEventSink sink) {
        String key = buildKey(type, rootId, tabId, entityId);
        if (key.isEmpty() || sink == null) return;
        SINKS.computeIfPresent(key, (k, entry) -> {
            entry.sinks.remove(sink);
            return entry.sinks.isEmpty() ? null : entry;
        });
    }

    public static void close(String type, String rootId, String tabId, String entityId) {
        String key = buildKey(type, rootId, tabId, entityId);
        if (key.isEmpty()) return;
        CLOSE_REQUESTS.add(key);
        WatchEntry entry = SINKS.remove(key);
        if (entry == null || entry.sinks.isEmpty()) {
            return;
        }
        entry.sinks.forEach(StatusWatchRegistry::safeClose);
    }

    public static void closePairing(AuthenticationSessionModel session, String pairingId) {
        if (session == null) return;
        close(TYPE_PAIRING, getRootId(session), session.getTabId(), pairingId);
    }

    public static void closeEcg(AuthenticationSessionModel session, String challengeId) {
        if (session == null) return;
        close(TYPE_ECG, getRootId(session), session.getTabId(), challengeId);
    }

    public static boolean isCloseRequested(String type, String rootId, String tabId, String entityId) {
        String key = buildKey(type, rootId, tabId, entityId);
        return !key.isEmpty() && CLOSE_REQUESTS.contains(key);
    }

    public static void markFinished(String type, String rootId, String tabId, String entityId) {
        String key = buildKey(type, rootId, tabId, entityId);
        if (!key.isEmpty()) {
            CLOSE_REQUESTS.remove(key);
        }
    }

    public static String buildKey(String type, String rootId, String tabId, String entityId) {
        String safeType = type != null ? type : "";
        String safeRoot = rootId != null ? rootId : "";
        String safeTab = tabId != null ? tabId : "";
        String safeEntity = entityId != null ? entityId : "";
        if (safeRoot.isBlank() || safeTab.isBlank() || safeEntity.isBlank()) {
            return "";
        }
        return String.join(":", safeType, safeRoot, safeTab, safeEntity);
    }

    private static final class WatchEntry {
        private final Set<SseEventSink> sinks = ConcurrentHashMap.newKeySet();
    }

    private static void safeClose(SseEventSink sink) {
        if (sink == null) return;
        try {
            if (!sink.isClosed()) {
                sink.close();
            }
        } catch (Exception ignored) {
            // Ignore close errors to avoid leaking threads here.
        }
    }

    private static String getRootId(AuthenticationSessionModel session) {
        return session.getParentSession() != null ? session.getParentSession().getId() : null;
    }
}
