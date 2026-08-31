package ai.qubere.agent.evaluation;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGoldenDatasetRepository implements GoldenDatasetRepository {

    private final Map<String, GoldenDataset> datasets = new ConcurrentHashMap<>();

    @Override
    public GoldenDataset save(GoldenDataset dataset) {
        validate(dataset);
        datasets.put(dataset.name(), dataset);
        return dataset;
    }

    @Override
    public Optional<GoldenDataset> find(String datasetName) {
        return Optional.ofNullable(datasets.get(datasetName));
    }

    @Override
    public Collection<GoldenDataset> list() {
        Map<String, GoldenDataset> ordered = new LinkedHashMap<>();
        datasets.values().stream()
                .sorted(Comparator.comparing(GoldenDataset::name))
                .forEach(dataset -> ordered.put(dataset.name(), dataset));
        return ordered.values();
    }

    private void validate(GoldenDataset dataset) {
        if (dataset == null || dataset.name() == null || dataset.name().isBlank()) {
            throw new IllegalArgumentException("dataset name is required");
        }
    }
}
