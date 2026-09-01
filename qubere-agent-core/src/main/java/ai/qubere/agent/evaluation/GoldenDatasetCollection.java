package ai.qubere.agent.evaluation;

import java.util.List;

public record GoldenDatasetCollection(
        List<GoldenDataset> datasets
) {
    public GoldenDatasetCollection {
        datasets = datasets == null ? List.of() : List.copyOf(datasets);
    }
}
