package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.ResolvedAgentPolicy;

/**
 * Phase 4 governance hook for cross-cutting production controls such as
 * rate limits, cost budgets, tenant quotas, and execution admission checks.
 */
public interface AgentGovernanceService {

    default void beforeRun(AgentExecutionContext context, AgentDescriptor descriptor, ResolvedAgentPolicy policy) {
    }

    default void afterRun(AgentExecutionContext context, AgentDescriptor descriptor, AgentOutput output) {
    }
}
