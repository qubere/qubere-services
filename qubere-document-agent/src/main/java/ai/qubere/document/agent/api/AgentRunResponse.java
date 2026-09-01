package ai.qubere.document.agent.api;

import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentRunStatus;

public record AgentRunResponse(
        String executionId,
        AgentRunStatus status,
        String approvalId,
        AgentOutput output
) {
    public AgentRunResponse(String executionId, AgentOutput output) {
        this(executionId, AgentRunStatus.SUCCEEDED, null, output);
    }
}

