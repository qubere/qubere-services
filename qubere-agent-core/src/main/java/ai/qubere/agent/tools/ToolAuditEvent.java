package ai.qubere.agent.tools;

import java.time.Instant;
import java.util.Map;

public record ToolAuditEvent(
        String executionId,
        String tenantId,
        String actorId,
        String toolName,
        ToolAuditStatus status,
        String message,
        Map<String, Object> metadata,
        Instant occurredAt
) {
    public ToolAuditEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
