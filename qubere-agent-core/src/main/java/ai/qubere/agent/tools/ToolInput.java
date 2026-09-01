package ai.qubere.agent.tools;

import java.util.Map;

public record ToolInput(
        String executionId,
        String tenantId,
        String actorId,
        Map<String, Object> arguments
) {
    public ToolInput {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
