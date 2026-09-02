package ai.qubere.agent.evaluation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Layers a primary (typically database-backed) dataset repository over a fallback (typically
 * classpath) one.
 * <p>
 * Production evaluation usually needs both: datasets that ship and version with the code, plus
 * datasets curated operationally from real failures. Lookups prefer the primary so an
 * operationally-edited dataset overrides the packaged one of the same name, and writes always go
 * to the primary because classpath resources are not writable.
 */
public class CompositeGoldenDatasetRepository implements GoldenDatasetRepository {

    private final GoldenDatasetRepository primary;
    private final GoldenDatasetRepository fallback;

    public CompositeGoldenDatasetRepository(GoldenDatasetRepository primary, GoldenDatasetRepository fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public GoldenDataset save(GoldenDataset dataset) {
        return primary.save(dataset);
    }

    @Override
    public Optional<GoldenDataset> find(String datasetName) {
        Optional<GoldenDataset> fromPrimary = primary.find(datasetName);
        return fromPrimary.isPresent() ? fromPrimary : fallback.find(datasetName);
    }

    @Override
    public Collection<GoldenDataset> list() {
        // Primary entries win on name collision, matching find() precedence.
        Map<String, GoldenDataset> merged = new LinkedHashMap<>();
        fallback.list().forEach(dataset -> merged.put(dataset.name(), dataset));
        primary.list().forEach(dataset -> merged.put(dataset.name(), dataset));
        return merged.values();
    }
}
