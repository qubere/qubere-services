package ai.qubere.agent.evaluation;

import ai.qubere.agent.core.AgentRunOptions;

import java.util.Map;

public record AgentReplayRequest(
        String sourceExecutionId,
        String agentVersion,
        Map<String, Object> inputOverride,
        AgentRunOptions options
) {
    public AgentReplayRequest {
        inputOverride = inputOverride == null ? Map.of() : Map.copyOf(inputOverride);
    }
}
