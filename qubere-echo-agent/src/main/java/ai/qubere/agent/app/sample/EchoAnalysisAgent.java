package ai.qubere.agent.app.sample;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentDecisionDraft;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.core.ResolvedAgentPolicy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class EchoAnalysisAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            "generic.echo-analysis",
            "Echo Analysis Agent",
            "0.1.0",
            "Reference agent that validates the runtime contract by echoing caller input.",
            AgentRiskLevel.LOW,
            Set.of("analysis", "diagnostics")
    );

    @Override
    public AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
        ResolvedAgentPolicy policy = context.attributes().get("resolvedPolicy") instanceof ResolvedAgentPolicy resolved
                ? resolved
                : ResolvedAgentPolicy.defaults();
        Map<String, Object> resultValues = new java.util.LinkedHashMap<>();
        resultValues.put("executionId", context.executionId());
        resultValues.put("tenantId", context.tenantId());
        resultValues.put("received", input.values());
        resultValues.put("policy", Map.of(
                "mode", policy.mode().name(),
                "modelProvider", policy.modelProvider(),
                "modelName", policy.modelName(),
                "promptVersion", policy.promptVersion(),
                "memoryEnabled", policy.memoryEnabled(),
                "maxToolCalls", policy.maxToolCalls(),
                "timeoutSeconds", policy.timeoutSeconds(),
                "allowedTools", policy.allowedTools()
        ));
        return new AgentResult<>(
                resultValues,
                new AgentDecisionDraft("ECHO_ACCEPTED", "Input accepted by the generic runtime.", 1.0d),
                List.of(),
                Map.of("agentId", DESCRIPTOR.id())
        );
    }
}
