package ai.qubere.agent.ai;

import java.util.Map;

public record AgentPrompt(
        String system,
        String user,
        Map<String, Object> variables
) {
    public AgentPrompt {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
