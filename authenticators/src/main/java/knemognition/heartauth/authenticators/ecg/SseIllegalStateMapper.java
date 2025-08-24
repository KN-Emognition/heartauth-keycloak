package knemognition.heartauth.authenticators.ecg;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Suppress the harmless SSE race where the client disconnects right as
 * the server is about to send the initial SSE bytes.
 */
@Provider
public class SseIllegalStateMapper implements ExceptionMapper<IllegalStateException> {
    @Override
    public Response toResponse(IllegalStateException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("Response has already been written")) {
            // Treat as a clean close; browser will reconnect if it wants.
            return Response.noContent().build(); // 204
        }
        // Let other IllegalStateExceptions propagate to the default handler
        return Response.serverError().build();
    }
}
