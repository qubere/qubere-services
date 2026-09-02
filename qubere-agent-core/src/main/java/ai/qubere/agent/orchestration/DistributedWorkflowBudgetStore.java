package ai.qubere.agent.orchestration;

import java.math.BigDecimal;

/**
 * Durable, shared accounting for a workflow's aggregate consumption.
 * <p>
 * {@link ai.qubere.agent.runtime.AgentWorkflowBudget} is an in-process object: it correctly bounds
 * a workflow that runs entirely inside one JVM, but it cannot bound one that spans services,
 * because each service would hold its own independent counters. This port moves the counters into
 * shared storage so a distributed workflow enforces one ceiling in total rather than one ceiling
 * per service.
 * <p>
 * Implementations must make {@link #tryConsume} atomic with respect to concurrent callers across
 * processes — typically a conditional/atomic database update rather than a read-then-write — since
 * sub-agents in different services can consume the same budget simultaneously.
 */
public interface DistributedWorkflowBudgetStore {

    /**
     * Atomically attempts to consume workflow budget.
     *
     * @param workflowId       workflow whose shared budget is being consumed
     * @param agentInvocations agent invocations to consume (0 for none)
     * @param toolCalls        tool calls to consume (0 for none)
     * @param costUsd          model spend to consume ({@code null}/zero for none)
     * @param limits           ceilings to enforce for this workflow
     * @return the decision, including current usage for diagnostics
     */
    WorkflowBudgetDecision tryConsume(
            String workflowId,
            int agentInvocations,
            int toolCalls,
            BigDecimal costUsd,
            WorkflowBudgetLimits limits
    );

    /**
     * Releases a workflow's budget record once it reaches a terminal state.
     */
    void release(String workflowId);

    /**
     * Ceilings applied to one workflow. A non-positive value disables that dimension, matching the
     * convention used elsewhere in {@code agent-platform} limits.
     */
    record WorkflowBudgetLimits(int maxAgentInvocations, int maxToolCalls, BigDecimal maxEstimatedCostUsd) {
        public WorkflowBudgetLimits {
            maxEstimatedCostUsd = maxEstimatedCostUsd == null ? BigDecimal.ZERO : maxEstimatedCostUsd;
        }
    }

    /**
     * Outcome of a consumption attempt.
     *
     * @param allowed              whether the consumption was permitted and applied
     * @param reason               human-readable rejection reason when {@code allowed} is false
     * @param usedAgentInvocations agent invocations consumed by the workflow so far
     * @param usedToolCalls        tool calls consumed by the workflow so far
     * @param usedCostUsd          model spend consumed by the workflow so far
     */
    record WorkflowBudgetDecision(
            boolean allowed,
            String reason,
            int usedAgentInvocations,
            int usedToolCalls,
            BigDecimal usedCostUsd
    ) {
        public WorkflowBudgetDecision {
            usedCostUsd = usedCostUsd == null ? BigDecimal.ZERO : usedCostUsd;
        }

        public static WorkflowBudgetDecision allow(int usedAgentInvocations, int usedToolCalls, BigDecimal usedCostUsd) {
            return new WorkflowBudgetDecision(true, null, usedAgentInvocations, usedToolCalls, usedCostUsd);
        }

        public static WorkflowBudgetDecision deny(String reason, int usedAgentInvocations, int usedToolCalls, BigDecimal usedCostUsd) {
            return new WorkflowBudgetDecision(false, reason, usedAgentInvocations, usedToolCalls, usedCostUsd);
        }
    }
}
