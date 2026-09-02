package ai.qubere.document.agent.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> details
) {
    public ApiErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, Map.of());
    }

    public static ApiErrorResponse of(int status, String code, String message, String path, Map<String, String> details) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, details);
    }
}
