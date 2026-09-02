package ai.qubere.agent.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_checkpoint")
@IdClass(AgentCheckpointEntity.AgentCheckpointId.class)
public class AgentCheckpointEntity {

    @Id
    @Column(name = "execution_id", length = 64, nullable = false)
    private String executionId;

    @Id
    @Column(name = "step_name", length = 200, nullable = false)
    private String stepName;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Lob
    @Column(name = "result_json")
    private String resultJson;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Composite key: a checkpoint is uniquely identified by its execution and step name, which is
     * what makes {@code save} idempotent per step and keeps a retried save from duplicating rows.
     */
    public static class AgentCheckpointId implements java.io.Serializable {
        private String executionId;
        private String stepName;

        public AgentCheckpointId() {
        }

        public AgentCheckpointId(String executionId, String stepName) {
            this.executionId = executionId;
            this.stepName = stepName;
        }

        public String getExecutionId() {
            return executionId;
        }

        public void setExecutionId(String executionId) {
            this.executionId = executionId;
        }

        public String getStepName() {
            return stepName;
        }

        public void setStepName(String stepName) {
            this.stepName = stepName;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AgentCheckpointId that)) {
                return false;
            }
            return java.util.Objects.equals(executionId, that.executionId)
                    && java.util.Objects.equals(stepName, that.stepName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(executionId, stepName);
        }
    }
}
