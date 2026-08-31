package ai.qubere.agent.runtime;

import ai.qubere.agent.core.AgentRunStatus;

import java.time.Instant;

public record AgentExecutionRecord(
        String executionId,
        String agentId,
        String agentVersion,
        String tenantId,
        String actorId,
        AgentRunStatus status,
        String inputJson,
        String outputJson,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
