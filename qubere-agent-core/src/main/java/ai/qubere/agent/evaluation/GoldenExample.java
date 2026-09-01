package ai.qubere.agent.evaluation;

import ai.qubere.agent.core.AgentRunOptions;

import java.util.Map;

public record GoldenExample(
        String id,
        String agentId,
        String agentVersion,
        Map<String, Object> input,
        Map<String, Object> expectedOutput,
        AgentRunOptions options
) {
    public GoldenExample {
        input = input == null ? Map.of() : Map.copyOf(input);
        expectedOutput = expectedOutput == null ? Map.of() : Map.copyOf(expectedOutput);
    }
}
