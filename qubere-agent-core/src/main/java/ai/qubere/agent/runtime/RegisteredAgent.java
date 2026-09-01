package ai.qubere.agent.runtime;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;

public record RegisteredAgent(
        AgentDescriptor descriptor,
        Agent<?, ?> agent,
        boolean defaultVersion
) {
}
