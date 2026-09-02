package ai.qubere.agent.mcp;

import java.util.List;
import java.util.Map;

/**
 * Result of an MCP {@code tools/call}, in MCP's content-block shape.
 *
 * @param content list of content blocks; the framework emits a single {@code text} block whose
 *                text is the JSON-encoded tool result
 * @param isError whether the call failed, mirroring MCP's {@code isError} flag so a client can
 *                distinguish a tool-level failure from a transport error
 */
public record McpToolCallResult(
        List<Map<String, Object>> content,
        boolean isError
) {
    public McpToolCallResult {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static McpToolCallResult text(String text) {
        return new McpToolCallResult(List.of(Map.of("type", "text", "text", text == null ? "" : text)), false);
    }

    public static McpToolCallResult error(String message) {
        return new McpToolCallResult(List.of(Map.of("type", "text", "text", message == null ? "" : message)), true);
    }
}
