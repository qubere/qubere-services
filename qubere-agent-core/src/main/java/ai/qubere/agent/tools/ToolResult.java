package ai.qubere.agent.tools;

import java.util.Map;

public record ToolResult(
        boolean success,
        Map<String, Object> values,
        String errorMessage
) {
    public ToolResult {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static ToolResult success(Map<String, Object> values) {
        return new ToolResult(true, values, null);
    }

    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, Map.of(), errorMessage);
    }
}
