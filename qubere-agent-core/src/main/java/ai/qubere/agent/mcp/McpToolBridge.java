package ai.qubere.agent.mcp;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.tools.ToolDescriptor;
import ai.qubere.agent.tools.ToolExecutionRequest;
import ai.qubere.agent.tools.ToolExecutionService;
import ai.qubere.agent.tools.ToolRegistry;
import ai.qubere.agent.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Exposes the framework's governed tools to external Model Context Protocol clients, and routes
 * inbound MCP tool calls back through {@link ToolExecutionService}.
 * <p>
 * The value of doing this in the framework rather than in each application is that governance is
 * preserved: an MCP client does not get a side door around the tool allow-list, permission checks,
 * approval policy, dry-run safety, audit events, {@code agent_tool_call} records, resilience, or
 * budget accounting. An MCP call is just another governed tool invocation.
 * <p>
 * <b>Scope.</b> This is the bridge and governance layer. The MCP transport itself (stdio, SSE, or
 * HTTP JSON-RPC framing) is deliberately left to the deployed application, which knows how it is
 * hosted and can adopt whichever MCP SDK version it needs. The reference application exposes this
 * bridge over authenticated HTTP endpoints as one concrete example.
 * <p>
 * <b>Security.</b> An MCP client is an untrusted caller. The caller identity and tenant scope
 * passed to {@link #callTool} must come from the application's authenticated boundary, exactly as
 * with inbound REST; the bridge never derives identity from the MCP payload itself.
 */
public class McpToolBridge {

    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;
    private final AgentPlatformProperties properties;
    private final ObjectMapper objectMapper;

    public McpToolBridge(
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            AgentPlatformProperties properties,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.toolExecutionService = toolExecutionService;
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * Lists tools in MCP shape, honoring the configured exposure allow-list.
     * <p>
     * Only tools named in {@code agent-platform.mcp.exposed-tools} are returned when that list is
     * non-empty. An empty list exposes every registered tool, which is convenient in development
     * but should be narrowed in production so adding an internal tool does not silently publish it
     * to external MCP clients.
     */
    public List<McpToolDescriptor> listTools() {
        Set<String> exposed = properties.getMcp().getExposedTools();
        return toolRegistry.listTools().stream()
                .filter(descriptor -> exposed.isEmpty() || exposed.contains(descriptor.name()))
                .map(this::toMcpDescriptor)
                .toList();
    }

    /**
     * Executes an MCP tool call through the governed tool path.
     *
     * @param toolName  tool requested by the MCP client
     * @param arguments arguments supplied by the MCP client
     * @param context   execution context built from the application's authenticated boundary
     * @param policy    resolved policy to apply; callers typically pass a policy whose
     *                  {@code allowedTools} reflects what this MCP client may invoke
     */
    public McpToolCallResult callTool(
            String toolName,
            Map<String, Object> arguments,
            AgentExecutionContext context,
            ResolvedAgentPolicy policy
    ) {
        if (toolName == null || toolName.isBlank()) {
            return McpToolCallResult.error("MCP tools/call requires a tool name");
        }
        Set<String> exposed = properties.getMcp().getExposedTools();
        if (!exposed.isEmpty() && !exposed.contains(toolName)) {
            // Refuse before reaching the registry so an unexposed tool is indistinguishable from
            // an unknown one to an external client.
            return McpToolCallResult.error("Tool is not exposed over MCP: " + toolName);
        }
        try {
            ToolResult result = toolExecutionService.execute(new ToolExecutionRequest(
                    toolName,
                    context,
                    policy == null ? ResolvedAgentPolicy.defaults() : policy,
                    arguments == null ? Map.of() : arguments
            ));
            if (!result.success()) {
                return McpToolCallResult.error(result.errorMessage() == null ? "Tool execution failed" : result.errorMessage());
            }
            return McpToolCallResult.text(toJson(result.values()));
        } catch (AgentExecutionException ex) {
            // Governance decisions (not allowed, approval required, budget exceeded) are surfaced
            // as MCP tool errors rather than transport failures, so the calling model can react.
            return McpToolCallResult.error(ex.errorCode().name() + ": " + ex.getMessage());
        }
    }

    private McpToolDescriptor toMcpDescriptor(ToolDescriptor descriptor) {
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", descriptor.inputSchema());

        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("riskLevel", descriptor.riskLevel().name());
        annotations.put("sideEffects", descriptor.sideEffects().stream().map(Enum::name).toList());
        annotations.put("requiredPermissions", descriptor.requiredPermissions());
        annotations.put("humanApprovalRequired", descriptor.humanApprovalRequired());
        // MCP's convention: a read-only tool is safe to call speculatively, a destructive one is not.
        annotations.put("readOnlyHint", descriptor.sideEffects().isEmpty());

        return new McpToolDescriptor(
                descriptor.name(),
                descriptor.description(),
                inputSchema,
                annotations
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
