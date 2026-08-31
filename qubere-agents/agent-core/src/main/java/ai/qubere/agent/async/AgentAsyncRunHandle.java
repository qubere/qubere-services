package ai.qubere.agent.async;

import ai.qubere.agent.core.AgentRunStatus;

public record AgentAsyncRunHandle(
        String executionId,
        AgentRunStatus status,
        String approvalId
) {
    public AgentAsyncRunHandle(String executionId, AgentRunStatus status) {
        this(executionId, status, null);
    }
}
