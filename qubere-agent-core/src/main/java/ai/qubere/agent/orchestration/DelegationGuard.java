package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.runtime.AgentWorkflowContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural safety checks applied before one agent delegates to another.
 * <p>
 * These are deliberately separate from budgets. A budget bounds how <em>much</em> a workflow may
 * consume; these checks reject delegation shapes that are wrong regardless of budget. Relying on
 * the budget alone to stop a cycle means the failure arrives late, after real spend, and reports
 * "budget exhausted" rather than the actual mistake.
 * <p>
 * Shared by {@link AgentCallTool} (LLM-driven delegation) and {@link AgentOrchestrator}
 * (code-declared delegation) so both paths are guarded identically.
 */
public final class DelegationGuard {

    private DelegationGuard() {
    }

    /**
     * Verifies that invoking {@code targetAgentId} from {@code callerContext} neither closes a
     * delegation cycle nor exceeds {@code maxDelegationDepth}.
     *
     * @param maxDelegationDepth maximum hops below the workflow root; {@code 0} disables the depth
     *                           check. Cycle detection always applies.
     * @throws AgentExecutionException with {@link AgentErrorCode#TOOL_NOT_ALLOWED} when the
     *                                 delegation is rejected
     */
    public static void check(AgentExecutionContext callerContext, String targetAgentId, int maxDelegationDepth) {
        if (callerContext == null || targetAgentId == null || targetAgentId.isBlank()) {
            return;
        }
        List<String> callerPath = AgentWorkflowContext.agentPath(callerContext);
        Object callerAgentId = callerContext.attributes().get("agentId");

        // Comparing against the whole ancestor path, not just the immediate caller, is what
        // catches A -> B -> A. An immediate-caller check alone lets mutual recursion run until
        // the budget or the JVM stack is exhausted.
        if (targetAgentId.equals(callerAgentId) || callerPath.contains(targetAgentId)) {
            List<String> cyclePath = new ArrayList<>(callerPath);
            if (callerAgentId != null
                    && (cyclePath.isEmpty() || !cyclePath.get(cyclePath.size() - 1).equals(callerAgentId.toString()))) {
                cyclePath.add(callerAgentId.toString());
            }
            cyclePath.add(targetAgentId);
            throw new AgentExecutionException(
                    AgentErrorCode.TOOL_NOT_ALLOWED,
                    "Delegation would create a cycle: " + String.join(" -> ", cyclePath)
            );
        }

        int childDepth = AgentWorkflowContext.delegationDepth(callerContext) + 1;
        if (maxDelegationDepth > 0 && childDepth > maxDelegationDepth) {
            throw new AgentExecutionException(
                    AgentErrorCode.TOOL_NOT_ALLOWED,
                    "Delegation exceeded the maximum depth of " + maxDelegationDepth
                            + " (attempted depth " + childDepth + " invoking '" + targetAgentId + "')"
            );
        }
    }
}
