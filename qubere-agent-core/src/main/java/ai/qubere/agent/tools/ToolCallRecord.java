package ai.qubere.agent.tools;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record ToolCallRecord(
        String callId,
        String executionId,
        String tenantId,
        String actorId,
        String correlationId,
        String toolName,
        ToolRiskLevel riskLevel,
        Set<ToolSideEffect> sideEffects,
        ToolCallStatus status,
        Map<String, Object> inputArguments,
        String outputSummary,
        String errorMessage,
        String approvalId,
        Instant startedAt,
        Instant completedAt,
        Long latencyMs
) {
    public ToolCallRecord {
        sideEffects = sideEffects == null ? Set.of() : Set.copyOf(sideEffects);
        inputArguments = inputArguments == null ? Map.of() : Map.copyOf(inputArguments);
    }
}