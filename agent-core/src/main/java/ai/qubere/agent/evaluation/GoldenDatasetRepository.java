package ai.qubere.agent.evaluation;

import java.util.Collection;
import java.util.Optional;

public interface GoldenDatasetRepository {

    GoldenDataset save(GoldenDataset dataset);

    Optional<GoldenDataset> find(String datasetName);

    Collection<GoldenDataset> list();
}
