package ai.qubere.agent.api;

import java.util.Set;

public record AgentDescriptor(
        String id,
        String name,
        String version,
        String description,
        AgentRiskLevel riskLevel,
        Set<String> capabilities
) {
    public AgentDescriptor {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
