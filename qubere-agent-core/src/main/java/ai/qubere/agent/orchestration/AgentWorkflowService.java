package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentWorkflowBudget;
import ai.qubere.agent.runtime.AgentWorkflowContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for orchestrated multi-agent runs: seeds a root execution context with workflow
 * identity and an aggregate budget, and rolls up the resulting executions into an
 * {@link AgentWorkflowSummary}.
 */
public class AgentWorkflowService {

    private static final int DEFAULT_SUMMARY_LIMIT = 200;

    private final AgentExecutionStore executionStore;
    private final DistributedWorkflowBudgetStore distributedBudgetStore;

    public AgentWorkflowService(AgentExecutionStore executionStore) {
        this(executionStore, null);
    }

    /**
     * @param distributedBudgetStore when supplied, workflows started by this service enforce their
     *                               aggregate budget through shared storage so the ceiling holds
     *                               across service boundaries instead of per JVM
     */
    public AgentWorkflowService(AgentExecutionStore executionStore, DistributedWorkflowBudgetStore distributedBudgetStore) {
        this.executionStore = executionStore;
        this.distributedBudgetStore = distributedBudgetStore;
    }

    /**
     * Marks {@code context} as the root of a new workflow and attaches an aggregate budget shared
     * by every sub-agent invoked beneath it.
     * <p>
     * The root execution's own id becomes the workflow id, so no separate workflow identifier has
     * to be generated or tracked by callers. Passing {@code 0} for a limit (or a non-positive
     * cost cap) disables that dimension.
     *
     * @param context             root execution context, typically built from the inbound request
     * @param maxAgentInvocations aggregate cap on agent invocations across the whole workflow
     * @param maxToolCalls        aggregate cap on tool calls across the whole workflow
     * @param maxEstimatedCostUsd aggregate model-spend cap across the whole workflow
     */
    public AgentExecutionContext startWorkflow(
            AgentExecutionContext context,
            int maxAgentInvocations,
            int maxToolCalls,
            BigDecimal maxEstimatedCostUsd
    ) {
        if (context == null) {
            throw new IllegalArgumentException("Root execution context is required to start a workflow");
        }
        Map<String, Object> attributes = new HashMap<>(context.attributes());
        String existingWorkflowId = AgentWorkflowContext.workflowId(context);
        String resolvedWorkflowId = existingWorkflowId == null ? context.executionId() : existingWorkflowId;
        attributes.put(AgentWorkflowContext.WORKFLOW_ID, resolvedWorkflowId);
        attributes.put(
                AgentWorkflowContext.WORKFLOW_BUDGET,
                new AgentWorkflowBudget(
                        maxAgentInvocations,
                        maxToolCalls,
                        maxEstimatedCostUsd,
                        distributedBudgetStore,
                        distributedBudgetStore == null ? null : resolvedWorkflowId
                )
        );
        return new AgentExecutionContext(
                context.executionId(),
                context.tenantId(),
                context.actorId(),
                context.correlationId(),
                context.requestedAt() == null ? Instant.now() : context.requestedAt(),
                attributes
        );
    }

    /**
     * Rolls up every execution belonging to {@code workflowId} into an aggregate summary.
     */
    public AgentWorkflowSummary summarize(String workflowId) {
        return summarize(workflowId, DEFAULT_SUMMARY_LIMIT);
    }

    /**
     * Rolls up executions belonging to {@code workflowId}, bounded to {@code limit} records.
     */
    public AgentWorkflowSummary summarize(String workflowId, int limit) {
        List<AgentExecutionRecord> executions = executionStore.findByWorkflowId(workflowId, limit);
        return AgentWorkflowSummary.from(workflowId, executions);
    }
}
