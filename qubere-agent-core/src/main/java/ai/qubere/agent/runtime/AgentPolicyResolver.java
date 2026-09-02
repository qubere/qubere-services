package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.ResolvedAgentPolicy;

public interface AgentPolicyResolver {

    ResolvedAgentPolicy resolve(String agentId, AgentRunOptions requestedOptions);

    /**
     * Resolves policy with the invoked agent's declared {@link AgentRiskLevel} available so
     * implementations can apply risk-based defaults (for example, requiring human approval for
     * {@code HIGH}/{@code CRITICAL} agents). The default implementation delegates to
     * {@link #resolve(String, AgentRunOptions)} for backward compatibility with resolvers written
     * before risk-aware resolution existed.
     */
    default ResolvedAgentPolicy resolve(String agentId, AgentRunOptions requestedOptions, AgentRiskLevel descriptorRiskLevel) {
        return resolve(agentId, requestedOptions);
    }
}
