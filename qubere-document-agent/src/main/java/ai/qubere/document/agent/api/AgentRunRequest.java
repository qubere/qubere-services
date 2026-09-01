package ai.qubere.document.agent.api;

import ai.qubere.agent.core.AgentRunOptions;

import java.util.Map;

public record AgentRunRequest(
        Map<String, Object> input,
        String agentVersion,
        AgentRunOptions options,
        Boolean async,
        String callbackUrl,
        String idempotencyKey
) {
    public AgentRunRequest {
        input = input == null ? Map.of() : Map.copyOf(input);
    }
}

