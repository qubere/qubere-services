package ai.qubere.agent.app.sample;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentDecisionDraft;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.tools.ToolExecutionRequest;
import ai.qubere.agent.tools.ToolExecutionService;
import ai.qubere.agent.tools.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ToolBackedEchoAnalysisAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            "generic.tool-echo-analysis",
            "Tool-backed Echo Analysis Agent",
            "0.1.0",
            "Reference agent that exercises the framework tool execution, audit, and persistence path.",
            AgentRiskLevel.LOW,
            Set.of("analysis", "diagnostics", "tools")
    );

    private final ToolExecutionService toolExecutionService;

    public ToolBackedEchoAnalysisAgent(ToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    @Override
    public AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
        ResolvedAgentPolicy policy = context.attributes().get("resolvedPolicy") instanceof ResolvedAgentPolicy resolved
                ? resolved
                : ResolvedAgentPolicy.defaults();
        String message = input.values().getOrDefault("message", "").toString();
        ToolResult toolResult = toolExecutionService.execute(new ToolExecutionRequest(
                EchoLookupTool.TOOL_NAME,
                context,
                policy,
                Map.of("message", message)
        ));

        return new AgentResult<>(
                Map.of(
                        "executionId", context.executionId(),
                        "tenantId", context.tenantId(),
                        "received", input.values(),
                        "tool", Map.of(
                                "name", EchoLookupTool.TOOL_NAME,
                                "success", toolResult.success(),
                                "result", toolResult.values()
                        ),
                        "policy", Map.of(
                                "allowToolCalls", policy.allowToolCalls(),
                                "allowedTools", policy.allowedTools(),
                                "maxToolCalls", policy.maxToolCalls()
                        )
                ),
                new AgentDecisionDraft("TOOL_ECHO_ACCEPTED", "Input accepted after read-only tool lookup.", 1.0d),
                List.of(),
                Map.of("agentId", DESCRIPTOR.id(), "toolName", EchoLookupTool.TOOL_NAME)
        );
    }
}