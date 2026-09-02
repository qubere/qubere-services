package ai.qubere.agent.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        // Metadata legitimately contains null values (e.g. unresolved temperature/maxOutputTokens),
        // so an unmodifiable copy is used here instead of Map.copyOf/Map.of, which reject null values.
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
