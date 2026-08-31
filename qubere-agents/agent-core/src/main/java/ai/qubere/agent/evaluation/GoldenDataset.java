package ai.qubere.agent.evaluation;

import java.util.List;
import java.util.Map;

public record GoldenDataset(
        String name,
        String description,
        List<GoldenExample> examples,
        Map<String, Object> metadata
) {
    public GoldenDataset {
        examples = examples == null ? List.of() : List.copyOf(examples);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
