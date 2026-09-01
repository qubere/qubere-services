package ai.qubere.agent.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ModelUsageRecord(
        String usageId,
        String executionId,
        String tenantId,
        String actorId,
        String correlationId,
        String agentId,
        String agentVersion,
        String modelProvider,
        String modelName,
        String promptVersion,
        ModelUsageStatus status,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        BigDecimal estimatedCostUsd,
        Long latencyMs,
        String errorMessage,
        Map<String, Object> metadata,
        Instant startedAt,
        Instant completedAt
) {
    public ModelUsageRecord {
        status = status == null ? ModelUsageStatus.STARTED : status;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}