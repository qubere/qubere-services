package ai.qubere.agent.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_evaluation_dataset")
public class AgentEvaluationDatasetEntity {

    @Id
    @Column(name = "dataset_name", length = 200, nullable = false)
    private String datasetName;

    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Examples and metadata are stored as JSON rather than normalized child tables: a golden
     * dataset is read and written as a whole unit by the evaluation runner, and keeping it in one
     * row avoids N+1 loading and lets dataset shape evolve without schema migrations.
     */
    @Lob
    @Column(name = "examples_json")
    private String examplesJson;

    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExamplesJson() {
        return examplesJson;
    }

    public void setExamplesJson(String examplesJson) {
        this.examplesJson = examplesJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
