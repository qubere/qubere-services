package ai.qubere.agent.async;

import ai.qubere.agent.core.AgentRunStatus;

import java.time.Instant;

public record AgentRunCallback(
        String callbackUrl,
        String executionId,
        String agentId,
        AgentRunStatus status,
        String message,
        Instant occurredAt
) {
}
