package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.AgentWorkflowContext;
import ai.qubere.agent.tools.AgentTool;
import ai.qubere.agent.tools.ToolDescriptor;
import ai.qubere.agent.tools.ToolInput;
import ai.qubere.agent.tools.ToolResult;
import ai.qubere.agent.tools.ToolRiskLevel;
import ai.qubere.agent.tools.ToolSideEffect;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exposes agent invocation as a governed {@link AgentTool}, so an orchestrator agent — or an
 * LLM driving that orchestrator through tool calling — can delegate to registered sub-agents
 * without bypassing the framework's tool governance path.
 * <p>
 * Routing sub-agent calls through the tool layer rather than calling
 * {@link AgentRuntimeService#run} directly inside agent code means every delegation automatically
 * gets allow-list enforcement, permission checks, approval policy, dry-run safety, audit events,
 * durable {@code agent_tool_call} rows, resilience, and workflow budget consumption.
 * <p>
 * The invoked sub-agent runs under a child {@link AgentExecutionContext} produced by
 * {@link AgentWorkflowContext#childOf}, so its {@code agent_execution_record} row is linked to
 * the orchestration through {@code workflow_id}/{@code parent_execution_id} and it shares the
 * caller's tenant, actor, correlation id, and aggregate workflow budget.
 * <p>
 * Tool arguments:
 * <ul>
 *   <li>{@code agentId} (required) — id of the sub-agent to invoke</li>
 *   <li>{@code agentVersion} (optional) — specific version; defaults to the registry's default</li>
 *   <li>{@code input} (optional) — map passed through as the sub-agent's {@link GenericAgentInput}</li>
 * </ul>
 * The tool is declared {@link ToolRiskLevel#MEDIUM} with {@link ToolSideEffect#WRITE_INTERNAL}
 * because the invoked sub-agent may itself perform internal writes; a deployment that only
 * delegates to read-only analysis agents can register its own descriptor with a lower risk level.
 */
public class AgentCallTool implements AgentTool {

    public static final String TOOL_NAME = "agent.call";

    public static final String ARG_AGENT_ID = "agentId";
    public static final String ARG_AGENT_VERSION = "agentVersion";
    public static final String ARG_INPUT = "input";

    /**
     * Default ceiling on delegation hops below the workflow root. Chosen well above realistic
     * orchestration depth so it never fires for a legitimate plan, while still bounding a runaway
     * chain long before it exhausts the stack.
     */
    public static final int DEFAULT_MAX_DELEGATION_DEPTH = 8;

    private final AgentRuntimeService runtimeService;
    private final AgentRegistry registry;
    private final ToolDescriptor descriptor;
    private final int maxDelegationDepth;

    public AgentCallTool(AgentRuntimeService runtimeService, AgentRegistry registry) {
        this(runtimeService, registry, defaultDescriptor(), DEFAULT_MAX_DELEGATION_DEPTH);
    }

    public AgentCallTool(AgentRuntimeService runtimeService, AgentRegistry registry, ToolDescriptor descriptor) {
        this(runtimeService, registry, descriptor, DEFAULT_MAX_DELEGATION_DEPTH);
    }

    /**
     * @param maxDelegationDepth maximum number of delegation hops below the workflow root;
     *                           {@code 0} disables the depth guard (cycle detection still applies)
     */
    public AgentCallTool(
            AgentRuntimeService runtimeService,
            AgentRegistry registry,
            ToolDescriptor descriptor,
            int maxDelegationDepth
    ) {
        this.runtimeService = runtimeService;
        this.registry = registry;
        this.descriptor = descriptor == null ? defaultDescriptor() : descriptor;
        this.maxDelegationDepth = Math.max(0, maxDelegationDepth);
    }

    private static ToolDescriptor defaultDescriptor() {
        return new ToolDescriptor(
                TOOL_NAME,
                "Invoke another registered agent as a sub-agent of the current orchestration. "
                        + "Arguments: agentId (required), agentVersion (optional), input (optional map).",
                ToolRiskLevel.MEDIUM,
                Set.of(ToolSideEffect.WRITE_INTERNAL),
                Set.of(),
                Map.of(
                        ARG_AGENT_ID, "string (required)",
                        ARG_AGENT_VERSION, "string (optional)",
                        ARG_INPUT, "object (optional)"
                ),
                Map.of(
                        "executionId", "string",
                        "agentId", "string",
                        "output", "object"
                ),
                Duration.ofSeconds(120),
                false
        );
    }

    @Override
    public ToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ToolResult execute(ToolInput input) {
        String agentId = stringArgument(input, ARG_AGENT_ID);
        if (agentId == null) {
            throw new AgentExecutionException(
                    AgentErrorCode.VALIDATION_FAILED,
                    "agent.call requires an '" + ARG_AGENT_ID + "' argument"
            );
        }
        AgentExecutionContext callerContext = input.callerContext();
        if (callerContext == null) {
            throw new AgentExecutionException(
                    AgentErrorCode.VALIDATION_FAILED,
                    "agent.call requires the invoking agent execution context to establish workflow linkage"
            );
        }
        String agentVersion = stringArgument(input, ARG_AGENT_VERSION);

        AgentDescriptor targetDescriptor = registry.findRegisteredAgent(agentId, agentVersion)
                .or(() -> agentVersion == null ? registry.findRegisteredAgent(agentId) : java.util.Optional.empty())
                .map(registered -> registered.descriptor())
                .orElseThrow(() -> new AgentExecutionException(
                        AgentErrorCode.AGENT_NOT_FOUND,
                        "agent.call target agent is not registered: " + agentId
                ));

        // Reject delegation cycles and runaway nesting before any work is done. Shared with the
        // code-declared orchestration path so both are guarded identically.
        DelegationGuard.check(callerContext, targetDescriptor.id(), maxDelegationDepth);

        String childExecutionId = UUID.randomUUID().toString();
        AgentExecutionContext childContext =
                AgentWorkflowContext.childOf(callerContext, childExecutionId, targetDescriptor.id());

        AgentOutput output = runtimeService.run(
                agentId,
                agentVersion,
                new GenericAgentInput(mapArgument(input, ARG_INPUT)),
                childContext,
                null
        );

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("executionId", childExecutionId);
        values.put("agentId", targetDescriptor.id());
        values.put("agentVersion", targetDescriptor.version());
        values.put("workflowId", AgentWorkflowContext.workflowId(childContext));
        values.put("output", output instanceof AgentResult<?> result ? result.value() : output);
        return ToolResult.success(values);
    }

    private String stringArgument(ToolInput input, String key) {
        Object value = input.arguments().get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapArgument(ToolInput input, String key) {
        Object value = input.arguments().get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
