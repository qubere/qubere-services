package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentWorkflowBudget;
import ai.qubere.agent.runtime.AgentWorkflowContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentWorkflowTest {

    @Test
    void rootExecutionIdBecomesWorkflowIdForChildren() {
        AgentExecutionContext root = context("exec-root", Map.of());

        AgentExecutionContext child = AgentWorkflowContext.childOf(root, "exec-child");

        assertThat(AgentWorkflowContext.workflowId(child)).isEqualTo("exec-root");
        assertThat(AgentWorkflowContext.parentExecutionId(child)).isEqualTo("exec-root");
    }

    @Test
    void grandchildInheritsRootWorkflowIdButPointsAtItsDirectParent() {
        AgentExecutionContext root = context("exec-root", Map.of());
        AgentExecutionContext child = AgentWorkflowContext.childOf(root, "exec-child");

        AgentExecutionContext grandchild = AgentWorkflowContext.childOf(child, "exec-grandchild");

        assertThat(AgentWorkflowContext.workflowId(grandchild)).isEqualTo("exec-root");
        assertThat(AgentWorkflowContext.parentExecutionId(grandchild)).isEqualTo("exec-child");
    }

    @Test
    void childInheritsSecurityScopeAndCorrelationFromParent() {
        AgentExecutionContext root = context("exec-root", Map.of());

        AgentExecutionContext child = AgentWorkflowContext.childOf(root, "exec-child");

        assertThat(child.tenantId()).isEqualTo("tenant-1");
        assertThat(child.actorId()).isEqualTo("actor-1");
        assertThat(child.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void childDoesNotInheritParentRuntimeScopedState() {
        AgentExecutionContext root = context("exec-root", Map.of(
                "resolvedPolicy", "parent-policy",
                "agentRunBudget", "parent-budget",
                "agentId", "agent.parent",
                "permissions", java.util.Set.of("agents.run")
        ));

        AgentExecutionContext child = AgentWorkflowContext.childOf(root, "exec-child");

        assertThat(child.attributes()).doesNotContainKeys("resolvedPolicy", "agentRunBudget", "agentId");
        // Security context must still propagate so the sub-agent is authorized consistently.
        assertThat(child.attributes()).containsKey("permissions");
    }

    @Test
    void childInheritsSharedWorkflowBudget() {
        AgentWorkflowBudget budget = new AgentWorkflowBudget(5, 10, BigDecimal.ZERO);
        AgentExecutionContext root = context("exec-root", Map.of(AgentWorkflowContext.WORKFLOW_BUDGET, budget));

        AgentExecutionContext child = AgentWorkflowContext.childOf(root, "exec-child");

        assertThat(AgentWorkflowContext.workflowBudget(child)).isSameAs(budget);
    }

    @Test
    void workflowBudgetLimitsAgentInvocationsAcrossTheWholeWorkflow() {
        AgentWorkflowBudget budget = new AgentWorkflowBudget(2, 0, BigDecimal.ZERO);

        budget.consumeAgentInvocation("agent.a");
        budget.consumeAgentInvocation("agent.b");

        assertThatThrownBy(() -> budget.consumeAgentInvocation("agent.c"))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED));
    }

    @Test
    void workflowBudgetLimitsAggregateCost() {
        AgentWorkflowBudget budget = new AgentWorkflowBudget(0, 0, new BigDecimal("1.00"));

        budget.consumeCost(new BigDecimal("0.60"));

        assertThatThrownBy(() -> budget.consumeCost(new BigDecimal("0.60")))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED));
    }

    @Test
    void zeroLimitsDisableWorkflowBudgetDimensions() {
        AgentWorkflowBudget budget = new AgentWorkflowBudget(0, 0, BigDecimal.ZERO);

        for (int i = 0; i < 100; i++) {
            budget.consumeAgentInvocation("agent." + i);
            budget.consumeToolCall("tool." + i);
            budget.consumeCost(new BigDecimal("10.00"));
        }

        assertThat(budget.usedAgentInvocations()).isEqualTo(100);
        assertThat(budget.usedToolCalls()).isEqualTo(100);
    }

    @Test
    void summaryReportsSucceededWhenEveryExecutionSucceeded() {
        AgentWorkflowSummary summary = AgentWorkflowSummary.from("wf-1", List.of(
                execution("exec-root", null, AgentRunStatus.SUCCEEDED),
                execution("exec-child", "exec-root", AgentRunStatus.SUCCEEDED)
        ));

        assertThat(summary.status()).isEqualTo(AgentWorkflowStatus.SUCCEEDED);
        assertThat(summary.totalExecutions()).isEqualTo(2);
        assertThat(summary.succeeded()).isEqualTo(2);
        assertThat(summary.isTerminal()).isTrue();
    }

    @Test
    void summaryReportsPartialFailureWhenSomeSubAgentsFailed() {
        AgentWorkflowSummary summary = AgentWorkflowSummary.from("wf-1", List.of(
                execution("exec-root", null, AgentRunStatus.SUCCEEDED),
                execution("exec-child", "exec-root", AgentRunStatus.FAILED)
        ));

        assertThat(summary.status()).isEqualTo(AgentWorkflowStatus.PARTIAL_FAILURE);
        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void inFlightExecutionsTakePrecedenceOverAlreadyFailedBranches() {
        AgentWorkflowSummary summary = AgentWorkflowSummary.from("wf-1", List.of(
                execution("exec-root", null, AgentRunStatus.RUNNING),
                execution("exec-child", "exec-root", AgentRunStatus.FAILED)
        ));

        assertThat(summary.status()).isEqualTo(AgentWorkflowStatus.RUNNING);
        assertThat(summary.isTerminal()).isFalse();
    }

    @Test
    void waitingForApprovalTakesPrecedenceOverRunning() {
        AgentWorkflowSummary summary = AgentWorkflowSummary.from("wf-1", List.of(
                execution("exec-root", null, AgentRunStatus.RUNNING),
                execution("exec-child", "exec-root", AgentRunStatus.WAITING_FOR_APPROVAL)
        ));

        assertThat(summary.status()).isEqualTo(AgentWorkflowStatus.WAITING_FOR_APPROVAL);
    }

    @Test
    void summaryIdentifiesTheWorkflowRoot() {
        AgentWorkflowSummary summary = AgentWorkflowSummary.from("wf-1", List.of(
                execution("exec-root", null, AgentRunStatus.SUCCEEDED),
                execution("exec-child", "exec-root", AgentRunStatus.SUCCEEDED)
        ));

        assertThat(summary.root()).isNotNull();
        assertThat(summary.root().executionId()).isEqualTo("exec-root");
    }

    @Test
    void emptyWorkflowIsUnknown() {
        AgentWorkflowSummary summary = AgentWorkflowSummary.from("wf-missing", List.of());

        assertThat(summary.status()).isEqualTo(AgentWorkflowStatus.UNKNOWN);
        assertThat(summary.isTerminal()).isFalse();
    }

    private AgentExecutionContext context(String executionId, Map<String, Object> attributes) {
        return new AgentExecutionContext(executionId, "tenant-1", "actor-1", "corr-1", Instant.now(), attributes);
    }

    private AgentExecutionRecord execution(String executionId, String parentExecutionId, AgentRunStatus status) {
        return new AgentExecutionRecord(
                executionId, "agent.test", "1.0.0", "tenant-1", "actor-1", status,
                "{}", "{}", null, Instant.now(), Instant.now(), null, "wf-1", parentExecutionId
        );
    }
}
