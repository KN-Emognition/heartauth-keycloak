package knemognition.heartauth.authenticators.status;

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
            return Response.noContent().build(); // 204
        }
        return Response.serverError().build();
    }
}
