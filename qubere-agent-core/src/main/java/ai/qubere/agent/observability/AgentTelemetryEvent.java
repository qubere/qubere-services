package ai.qubere.agent.observability;

import java.time.Instant;
import java.util.Map;

public record AgentTelemetryEvent(
        String type,
        String name,
        String executionId,
        String tenantId,
        String actorId,
        String correlationId,
        String agentId,
        String agentVersion,
        String status,
        String message,
        Map<String, Object> attributes,
        Instant occurredAt
) {
    public AgentTelemetryEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}