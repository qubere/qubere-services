package ai.qubere.agent.persistence;

import ai.qubere.agent.evaluation.EvaluationStatus;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_evaluation_result")
public class AgentEvaluationResultEntity {

    @Id
    @Column(name = "evaluation_id", length = 64, nullable = false)
    private String evaluationId;

    @Column(name = "dataset_name", length = 256, nullable = false)
    private String datasetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private EvaluationStatus status;

    @Column(name = "total_count", nullable = false)
    private int total;

    @Column(name = "passed_count", nullable = false)
    private int passed;

    @Column(name = "failed_count", nullable = false)
    private int failed;

    @Lob
    @Column(name = "cases_json")
    private String casesJson;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    public String getEvaluationId() {
        return evaluationId;
    }

    public void setEvaluationId(String evaluationId) {
        this.evaluationId = evaluationId;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public EvaluationStatus getStatus() {
        return status;
    }

    public void setStatus(EvaluationStatus status) {
        this.status = status;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPassed() {
        return passed;
    }

    public void setPassed(int passed) {
        this.passed = passed;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public String getCasesJson() {
        return casesJson;
    }

    public void setCasesJson(String casesJson) {
        this.casesJson = casesJson;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
