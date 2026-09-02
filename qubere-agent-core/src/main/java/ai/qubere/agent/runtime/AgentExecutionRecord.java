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
        Instant updatedAt,
        String idempotencyKey,
        String workflowId,
        String parentExecutionId
) {
    public AgentExecutionRecord(
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
        this(executionId, agentId, agentVersion, tenantId, actorId, status, inputJson, outputJson, errorMessage, createdAt, updatedAt, null, null, null);
    }

    public AgentExecutionRecord(
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
            Instant updatedAt,
            String idempotencyKey
    ) {
        this(executionId, agentId, agentVersion, tenantId, actorId, status, inputJson, outputJson, errorMessage, createdAt, updatedAt, idempotencyKey, null, null);
    }

    /**
     * Whether this execution is the root of its workflow, i.e. it was invoked directly by a
     * caller rather than by another agent.
     */
    public boolean isWorkflowRoot() {
        return parentExecutionId == null;
    }
}
