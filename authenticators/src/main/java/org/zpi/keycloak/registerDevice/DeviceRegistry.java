package org.zpi.keycloak.registerDevice;
import org.keycloak.models.UserModel;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class DeviceRegistry {
    private static final String PREFIX = "ecgDevice."; // ecgDevice.<id>.*

    public static record Device(String id, String pubKey, long addedAt, String status) {
    }

    public static void upsert(UserModel user, String id, String pubKey) {
        String base = PREFIX + id + ".";
        user.setSingleAttribute(base + "pk", pubKey);
        user.setSingleAttribute(base + "addedAt", String.valueOf(Instant.now().getEpochSecond()));
        user.setSingleAttribute(base + "status", "active");
    }

    public static List<Device> list(UserModel user) {
        return user.getAttributes().entrySet().stream()
                .filter(e -> e.getKey().startsWith(PREFIX) && e.getKey().endsWith(".pk"))
                .map(e -> e.getKey().substring(PREFIX.length(), e.getKey().length() - 3))
                .map(id -> {
                    String base = PREFIX + id + ".";
                    String pk = user.getFirstAttribute(base + "pk");
                    String at = user.getFirstAttribute(base + "addedAt");
                    String st = user.getFirstAttribute(base + "status");
                    return new Device(id, pk, at == null ? 0L : Long.parseLong(at), st == null ? "active" : st);
                })
                .filter(d -> d.pubKey != null)
                .collect(Collectors.toList());
    }
}
