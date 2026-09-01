package ai.qubere.agent.runtime;

import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.ResolvedAgentPolicy;

public interface AgentPolicyResolver {

    ResolvedAgentPolicy resolve(String agentId, AgentRunOptions requestedOptions);
}
