package ai.qubere.agent.runtime;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.orchestration.DistributedWorkflowBudgetStore;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aggregate ceiling shared by every execution in one orchestrated multi-agent workflow.
 * <p>
 * {@link AgentRunBudget} bounds a single execution. That is not sufficient once an orchestrator
 * can spawn sub-agents: five sub-agents each staying under their own per-run limits can still
 * multiply total tool calls and spend far beyond what the operator intended for the workflow as
 * a whole. This budget is created once for the root execution, carried through the
 * {@link AgentWorkflowContext#WORKFLOW_BUDGET} context attribute, and consumed by every
 * descendant execution.
 * <p>
 * Two modes:
 * <ul>
 *   <li><b>In-process</b> (default) — counters live in this object. Correct and cheap for a
 *       workflow that runs entirely inside one JVM.</li>
 *   <li><b>Distributed</b> — when constructed with a {@link DistributedWorkflowBudgetStore} and a
 *       workflow id, consumption is delegated to shared storage so a workflow spanning multiple
 *       services enforces one ceiling in total rather than one per service.</li>
 * </ul>
 * Limits of {@code 0} (or a non-positive cost cap) disable that dimension, matching the
 * convention used by {@code agent-platform.governance} limiters.
 */
public class AgentWorkflowBudget {

    private final int maxAgentInvocations;
    private final int maxToolCalls;
    private final BigDecimal maxEstimatedCostUsd;

    private final AtomicInteger usedAgentInvocations = new AtomicInteger();
    private final AtomicInteger usedToolCalls = new AtomicInteger();
    private final AtomicReference<BigDecimal> usedCostUsd = new AtomicReference<>(BigDecimal.ZERO);

    private final DistributedWorkflowBudgetStore distributedStore;
    private final String workflowId;

    public AgentWorkflowBudget(int maxAgentInvocations, int maxToolCalls, BigDecimal maxEstimatedCostUsd) {
        this(maxAgentInvocations, maxToolCalls, maxEstimatedCostUsd, null, null);
    }

    /**
     * Creates a budget whose counters live in shared storage, so the ceiling holds across service
     * boundaries. Falls back to in-process counting when {@code distributedStore} is {@code null}.
     */
    public AgentWorkflowBudget(
            int maxAgentInvocations,
            int maxToolCalls,
            BigDecimal maxEstimatedCostUsd,
            DistributedWorkflowBudgetStore distributedStore,
            String workflowId
    ) {
        this.maxAgentInvocations = Math.max(0, maxAgentInvocations);
        this.maxToolCalls = Math.max(0, maxToolCalls);
        this.maxEstimatedCostUsd = maxEstimatedCostUsd == null ? BigDecimal.ZERO : maxEstimatedCostUsd;
        this.distributedStore = workflowId == null ? null : distributedStore;
        this.workflowId = workflowId;
    }

    /**
     * Whether this budget is enforced through shared storage rather than in-process counters.
     */
    public boolean isDistributed() {
        return distributedStore != null;
    }

    /**
     * Records one agent invocation within the workflow (the root run plus every sub-agent call).
     *
     * @throws AgentExecutionException with {@link AgentErrorCode#GOVERNANCE_LIMIT_EXCEEDED} when
     *         the workflow would exceed its configured agent-invocation ceiling
     */
    public int consumeAgentInvocation(String agentId) {
        if (distributedStore != null) {
            DistributedWorkflowBudgetStore.WorkflowBudgetDecision decision =
                    distributedStore.tryConsume(workflowId, 1, 0, null, limits());
            if (!decision.allowed()) {
                throw new AgentExecutionException(
                        AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                        "Workflow budget rejected agent invocation %s: %s".formatted(agentId, decision.reason())
                );
            }
            return decision.usedAgentInvocations();
        }
        int used = usedAgentInvocations.incrementAndGet();
        if (maxAgentInvocations > 0 && used > maxAgentInvocations) {
            throw new AgentExecutionException(
                    AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                    "Workflow exceeded maxAgentInvocations=" + maxAgentInvocations + " while invoking agent " + agentId
            );
        }
        return used;
    }

    /**
     * Records one tool call anywhere in the workflow, across all participating agents.
     */
    public int consumeToolCall(String toolName) {
        if (distributedStore != null) {
            DistributedWorkflowBudgetStore.WorkflowBudgetDecision decision =
                    distributedStore.tryConsume(workflowId, 0, 1, null, limits());
            if (!decision.allowed()) {
                throw new AgentExecutionException(
                        AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                        "Workflow budget rejected tool call %s: %s".formatted(toolName, decision.reason())
                );
            }
            return decision.usedToolCalls();
        }
        int used = usedToolCalls.incrementAndGet();
        if (maxToolCalls > 0 && used > maxToolCalls) {
            throw new AgentExecutionException(
                    AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                    "Workflow exceeded maxToolCalls=" + maxToolCalls + " while executing tool " + toolName
            );
        }
        return used;
    }

    /**
     * Adds model spend accumulated anywhere in the workflow and fails once the aggregate cap is
     * reached. A {@code null} or non-positive cost is ignored.
     */
    public BigDecimal consumeCost(BigDecimal costUsd) {
        if (costUsd == null || costUsd.signum() <= 0) {
            return usedCostUsd();
        }
        if (distributedStore != null) {
            DistributedWorkflowBudgetStore.WorkflowBudgetDecision decision =
                    distributedStore.tryConsume(workflowId, 0, 0, costUsd, limits());
            if (!decision.allowed()) {
                throw new AgentExecutionException(
                        AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                        "Workflow budget rejected model spend: " + decision.reason()
                );
            }
            return decision.usedCostUsd();
        }
        BigDecimal updated = usedCostUsd.updateAndGet(existing -> existing.add(costUsd));
        if (maxEstimatedCostUsd.signum() > 0 && updated.compareTo(maxEstimatedCostUsd) > 0) {
            throw new AgentExecutionException(
                    AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                    "Workflow exceeded maxEstimatedCostUsd=%s USD (accumulated %s USD)".formatted(
                            maxEstimatedCostUsd.stripTrailingZeros().toPlainString(),
                            updated.stripTrailingZeros().toPlainString()
                    )
            );
        }
        return updated;
    }

    private DistributedWorkflowBudgetStore.WorkflowBudgetLimits limits() {
        return new DistributedWorkflowBudgetStore.WorkflowBudgetLimits(maxAgentInvocations, maxToolCalls, maxEstimatedCostUsd);
    }

    public int usedAgentInvocations() {
        return usedAgentInvocations.get();
    }

    public int usedToolCalls() {
        return usedToolCalls.get();
    }

    public BigDecimal usedCostUsd() {
        return usedCostUsd.get();
    }
}
