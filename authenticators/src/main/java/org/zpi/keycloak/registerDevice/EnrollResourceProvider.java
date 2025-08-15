package org.zpi.keycloak.registerDevice;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("")
public class EnrollResourceProvider implements RealmResourceProvider {
    private final KeycloakSession session;
    public EnrollResourceProvider(KeycloakSession session) { this.session = session; }
    @Override public Object getResource() { return this; }

    public static class ConfirmPayload { public String code; public String device; public String pubkey; }

    @POST
    @Path("confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirm(ConfirmPayload p) {
        if (p == null || p.code == null || p.code.isBlank()) return Response.status(400).entity("missing code").build();
        String deviceId = (p.device == null || p.device.isBlank()) ? ("device:" + System.currentTimeMillis()) : p.device;
        String pubKey = (p.pubkey == null) ? "" : p.pubkey;
        boolean ok = PairingStore.getInstance().confirm(p.code, deviceId, pubKey);
        if (!ok) return Response.status(404).entity("invalid or expired code").build();
        return Response.ok("{\"status\":\"confirmed\"}").build();
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(@QueryParam("code") String code) {
        PairingStore.Status st = PairingStore.getInstance().status(code);
        String json = "{\"status\":\"" + (st == null ? "unknown" : st.name().toLowerCase()) + "\"}";
        return Response.ok(json).build();
    }

    @Override public void close() {}
}
