package org.zpi.keycloak.registerDevice;


import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PairingStore {
    public enum Status { PENDING, CONFIRMED }

    public static class Entry {
        public String userId;
        public String deviceId;
        public String pubKey; // base64url encoded
        public Status status = Status.PENDING;
        public long expiresAt;
    }

    private static final PairingStore INSTANCE = new PairingStore();
    public static PairingStore getInstance() { return INSTANCE; }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final long ttlSeconds = 180; // 3 minutes

    public void create(String code, String userId) {
        Entry e = new Entry();
        e.userId = userId;
        e.expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        map.put(code, e);
    }

    public boolean confirm(String code, String deviceId, String pubKey) {
        Entry e = map.get(code);
        if (e == null) return false;
        if (Instant.now().getEpochSecond() > e.expiresAt) { map.remove(code); return false; }
        e.status = Status.CONFIRMED;
        e.deviceId = deviceId;
        e.pubKey = pubKey;
        return true;
    }

    public Status status(String code) {
        Entry e = map.get(code);
        if (e == null) return null;
        if (Instant.now().getEpochSecond() > e.expiresAt) { map.remove(code); return null; }
        return e.status;
    }

    public Entry get(String code) { return map.get(code); }
    public void consume(String code) { map.remove(code); }
}
