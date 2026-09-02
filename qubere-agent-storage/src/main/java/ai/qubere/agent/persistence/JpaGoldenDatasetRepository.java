package ai.qubere.agent.persistence;

import ai.qubere.agent.evaluation.GoldenDataset;
import ai.qubere.agent.evaluation.GoldenDatasetRepository;
import ai.qubere.agent.evaluation.GoldenExample;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed {@link GoldenDatasetRepository}, so evaluation datasets are versionable and
 * queryable in the database rather than only loadable from the classpath.
 * <p>
 * Classpath datasets remain the right choice for datasets that should ship and version with the
 * code, and remain the framework default. This store is for datasets that are curated
 * operationally — grown from production failures, edited by domain reviewers, or promoted between
 * environments — where redeploying the application to change a test case is not acceptable.
 * <p>
 * Opt in through {@code agent-platform.evaluation.dataset-provider}. It is deliberately not a
 * {@code @Component}: auto-registering it would silently displace classpath dataset loading for
 * every application that adds {@code qubere-agent-storage}.
 */
public class JpaGoldenDatasetRepository implements GoldenDatasetRepository {

    private final AgentEvaluationDatasetRepository repository;
    private final ObjectMapper objectMapper;

    public JpaGoldenDatasetRepository(AgentEvaluationDatasetRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public GoldenDataset save(GoldenDataset dataset) {
        if (dataset == null || dataset.name() == null || dataset.name().isBlank()) {
            throw new IllegalArgumentException("Golden dataset requires a name");
        }
        AgentEvaluationDatasetEntity entity = repository.findById(dataset.name())
                .orElseGet(AgentEvaluationDatasetEntity::new);
        entity.setDatasetName(dataset.name());
        entity.setDescription(dataset.description());
        entity.setExamplesJson(toJson(dataset.examples()));
        entity.setMetadataJson(toJson(dataset.metadata()));
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return dataset;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GoldenDataset> find(String datasetName) {
        if (datasetName == null || datasetName.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(datasetName).map(this::toDataset);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<GoldenDataset> list() {
        return repository.findAll().stream().map(this::toDataset).toList();
    }

    private GoldenDataset toDataset(AgentEvaluationDatasetEntity entity) {
        return new GoldenDataset(
                entity.getDatasetName(),
                entity.getDescription(),
                readList(entity.getExamplesJson()),
                readMap(entity.getMetadataJson())
        );
    }

    private List<GoldenExample> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<GoldenExample>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to deserialize golden dataset examples", ex);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to deserialize golden dataset metadata", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to serialize golden dataset payload", ex);
        }
    }
}
