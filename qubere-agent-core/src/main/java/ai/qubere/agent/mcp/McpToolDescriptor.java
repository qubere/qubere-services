package ai.qubere.agent.mcp;

import java.util.Map;

/**
 * A framework tool described in Model Context Protocol shape, as returned by an MCP
 * {@code tools/list} call.
 * <p>
 * Deliberately a plain record rather than a type from an MCP SDK: the framework owns the mapping
 * from its governed {@link ai.qubere.agent.tools.ToolDescriptor} to MCP's wire shape, while
 * applications remain free to adopt whichever MCP SDK version and transport (stdio, SSE, HTTP)
 * they need without the framework pinning them to one.
 *
 * @param name        tool name as exposed to MCP clients
 * @param description human-readable description shown to the calling model
 * @param inputSchema JSON-Schema-shaped description of accepted arguments
 * @param annotations non-standard hints carrying the framework's governance metadata (risk level,
 *                    side effects, whether human approval is required) so an MCP client can
 *                    surface the real consequences of invoking the tool
 */
public record McpToolDescriptor(
        String name,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> annotations
) {
    public McpToolDescriptor {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
