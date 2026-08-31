package ai.qubere.agent.evaluation;

import java.util.List;
import java.util.Map;

public record PromptRegressionCase(
        String id,
        String promptId,
        String version,
        List<String> expectedSystemContains,
        List<String> expectedUserContains,
        Map<String, Object> metadata
) {
    public PromptRegressionCase {
        expectedSystemContains = expectedSystemContains == null ? List.of() : List.copyOf(expectedSystemContains);
        expectedUserContains = expectedUserContains == null ? List.of() : List.copyOf(expectedUserContains);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
