package ai.qubere.agent.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for agent execution checkpoints.
 * <p>
 * Checkpoints make an approval-interrupted multi-step agent resumable: on resume the agent is
 * re-invoked from the top, but every step already recorded here returns its stored result instead
 * of executing again, so side effects are not repeated and execution effectively continues from
 * the first incomplete step.
 */
public interface AgentCheckpointStore {

    /**
     * Records a completed step. Implementations must be idempotent per
     * {@code (executionId, stepName)} so a retried save does not duplicate or corrupt history.
     */
    void save(AgentCheckpoint checkpoint);

    Optional<AgentCheckpoint> find(String executionId, String stepName);

    /**
     * Returns all checkpoints for an execution in completion order.
     */
    List<AgentCheckpoint> findByExecutionId(String executionId);

    /**
     * Removes all checkpoints for an execution once it reaches a terminal state, so completed
     * runs do not accumulate storage indefinitely.
     */
    void deleteByExecutionId(String executionId);

    static AgentCheckpointStore noop() {
        return new AgentCheckpointStore() {
            @Override
            public void save(AgentCheckpoint checkpoint) {
            }

            @Override
            public Optional<AgentCheckpoint> find(String executionId, String stepName) {
                return Optional.empty();
            }

            @Override
            public List<AgentCheckpoint> findByExecutionId(String executionId) {
                return List.of();
            }

            @Override
            public void deleteByExecutionId(String executionId) {
            }
        };
    }
}
