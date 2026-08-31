package ai.qubere.agent.async;

import java.time.Instant;
import java.util.Map;

public record AgentApprovalRequest(
        String approvalId,
        String executionId,
        String agentId,
        String agentVersion,
        String tenantId,
        String requestedBy,
        AgentApprovalStatus status,
        String reason,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant expiresAt,
        Instant decidedAt,
        String decidedBy
) {
    public AgentApprovalRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
