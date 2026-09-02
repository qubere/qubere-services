package ai.qubere.agent.mcp;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentRunMode;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.tools.AgentTool;
import ai.qubere.agent.tools.ToolDescriptor;
import ai.qubere.agent.tools.ToolExecutionService;
import ai.qubere.agent.tools.ToolInput;
import ai.qubere.agent.tools.ToolRegistry;
import ai.qubere.agent.tools.ToolResult;
import ai.qubere.agent.tools.ToolRiskLevel;
import ai.qubere.agent.tools.ToolSideEffect;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolBridgeTest {

    private final AgentExecutionContext context = new AgentExecutionContext(
            "exec-mcp", "tenant-1", "actor-1", "corr-1", Instant.now(), Map.of()
    );

    @Test
    void listsRegisteredToolsInMcpShapeWithGovernanceAnnotations() {
        McpToolBridge bridge = bridge(new AgentPlatformProperties(), readOnlyTool(), destructiveTool());

        List<McpToolDescriptor> tools = bridge.listTools();

        assertThat(tools).hasSize(2);
        McpToolDescriptor readOnly = tools.stream().filter(t -> t.name().equals("lookup")).findFirst().orElseThrow();
        assertThat(readOnly.inputSchema()).containsEntry("type", "object");
        assertThat(readOnly.annotations())
                .containsEntry("riskLevel", "READ_ONLY")
                .containsEntry("humanApprovalRequired", false)
                .containsEntry("readOnlyHint", true);

        McpToolDescriptor destructive = tools.stream().filter(t -> t.name().equals("purge")).findFirst().orElseThrow();
        assertThat(destructive.annotations())
                .containsEntry("riskLevel", "DESTRUCTIVE")
                .containsEntry("readOnlyHint", false);
    }

    @Test
    void exposureAllowListLimitsWhichToolsExternalClientsCanDiscover() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getMcp().setExposedTools(Set.of("lookup"));
        McpToolBridge bridge = bridge(properties, readOnlyTool(), destructiveTool());

        assertThat(bridge.listTools()).extracting(McpToolDescriptor::name).containsExactly("lookup");
    }

    @Test
    void refusesToInvokeToolsThatAreNotExposed() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getMcp().setExposedTools(Set.of("lookup"));
        McpToolBridge bridge = bridge(properties, readOnlyTool(), destructiveTool());

        McpToolCallResult result = bridge.callTool("purge", Map.of(), context, permissivePolicy());

        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("not exposed over MCP");
    }

    @Test
    void executesExposedToolAndReturnsJsonContentBlock() {
        McpToolBridge bridge = bridge(new AgentPlatformProperties(), readOnlyTool());

        McpToolCallResult result = bridge.callTool("lookup", Map.of("recordId", "r-1"), context, permissivePolicy());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).containsEntry("type", "text");
        assertThat(textOf(result)).contains("\"found\":true");
    }

    @Test
    void governanceDenialSurfacesAsMcpToolErrorRatherThanEscapingAsAnException() {
        McpToolBridge bridge = bridge(new AgentPlatformProperties(), readOnlyTool());

        // A policy whose allow-list excludes the tool must block the MCP call, proving an MCP
        // client cannot bypass the framework's tool allow-list.
        ResolvedAgentPolicy restrictive = new ResolvedAgentPolicy(
                AgentRunMode.RECOMMEND, 8, 0.2d, 2048, true, false, Set.of("some-other-tool")
        );

        McpToolCallResult result = bridge.callTool("lookup", Map.of(), context, restrictive);

        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("TOOL_NOT_ALLOWED");
    }

    @Test
    void dryRunPolicyBlocksSideEffectingToolsInvokedOverMcp() {
        McpToolBridge bridge = bridge(new AgentPlatformProperties(), destructiveTool());

        ResolvedAgentPolicy dryRun = new ResolvedAgentPolicy(
                AgentRunMode.DRY_RUN, 8, 0.2d, 2048, true, false, Set.of()
        );

        McpToolCallResult result = bridge.callTool("purge", Map.of(), context, dryRun);

        assertThat(result.isError()).isTrue();
    }

    @Test
    void toolFailureIsReportedAsErrorNotSuccess() {
        McpToolBridge bridge = bridge(new AgentPlatformProperties(), failingTool());

        McpToolCallResult result = bridge.callTool("broken", Map.of(), context, permissivePolicy());

        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("downstream unavailable");
    }

    @Test
    void missingToolNameIsRejected() {
        McpToolBridge bridge = bridge(new AgentPlatformProperties(), readOnlyTool());

        assertThat(bridge.callTool(null, Map.of(), context, permissivePolicy()).isError()).isTrue();
        assertThat(bridge.callTool("  ", Map.of(), context, permissivePolicy()).isError()).isTrue();
    }

    private McpToolBridge bridge(AgentPlatformProperties properties, AgentTool... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools));
        return new McpToolBridge(registry, new ToolExecutionService(List.of(tools)), properties, new ObjectMapper());
    }

    private ResolvedAgentPolicy permissivePolicy() {
        return ResolvedAgentPolicy.defaults();
    }

    private String textOf(McpToolCallResult result) {
        return result.content().isEmpty() ? "" : String.valueOf(result.content().get(0).get("text"));
    }

    private AgentTool readOnlyTool() {
        return new AgentTool() {
            @Override
            public ToolDescriptor descriptor() {
                return new ToolDescriptor("lookup", "Look up a record", ToolRiskLevel.READ_ONLY,
                        Set.of(), Set.of(), Map.of("recordId", "string"), Map.of(), Duration.ofSeconds(5), false);
            }

            @Override
            public ToolResult execute(ToolInput input) {
                return ToolResult.success(Map.of("found", true));
            }
        };
    }

    private AgentTool destructiveTool() {
        return new AgentTool() {
            @Override
            public ToolDescriptor descriptor() {
                return new ToolDescriptor("purge", "Purge records", ToolRiskLevel.DESTRUCTIVE,
                        Set.of(ToolSideEffect.DESTRUCTIVE), Set.of(), Map.of(), Map.of(), Duration.ofSeconds(5), false);
            }

            @Override
            public ToolResult execute(ToolInput input) {
                return ToolResult.success(Map.of("purged", true));
            }
        };
    }

    private AgentTool failingTool() {
        return new AgentTool() {
            @Override
            public ToolDescriptor descriptor() {
                return new ToolDescriptor("broken", "Always fails", ToolRiskLevel.READ_ONLY,
                        Set.of(), Set.of(), Map.of(), Map.of(), Duration.ofSeconds(5), false);
            }

            @Override
            public ToolResult execute(ToolInput input) {
                return ToolResult.failure("downstream unavailable");
            }
        };
    }
}
