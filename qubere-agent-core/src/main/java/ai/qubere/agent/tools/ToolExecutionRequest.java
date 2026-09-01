package ai.qubere.agent.tools;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.ResolvedAgentPolicy;

import java.util.Map;

public record ToolExecutionRequest(
        String toolName,
        AgentExecutionContext context,
        ResolvedAgentPolicy policy,
        Map<String, Object> arguments
) {
    public ToolExecutionRequest {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
