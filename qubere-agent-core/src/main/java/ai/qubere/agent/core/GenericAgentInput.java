package ai.qubere.agent.core;

import ai.qubere.agent.api.AgentInput;

import java.util.Map;

public record GenericAgentInput(Map<String, Object> values) implements AgentInput {
    public GenericAgentInput {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
