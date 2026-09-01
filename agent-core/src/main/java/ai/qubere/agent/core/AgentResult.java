package ai.qubere.agent.core;

import ai.qubere.agent.api.AgentOutput;

import java.util.List;
import java.util.Map;

public record AgentResult<T>(
        T value,
        AgentDecisionDraft decision,
        List<AgentEvidenceDraft> evidence,
        Map<String, Object> metadata
) implements AgentOutput {
    public AgentResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
