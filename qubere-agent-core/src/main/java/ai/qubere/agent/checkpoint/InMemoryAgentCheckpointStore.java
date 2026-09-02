package ai.qubere.agent.checkpoint;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AgentCheckpointStore} for local development and tests.
 * <p>
 * Not suitable for production resume: checkpoints are lost on restart, and an approval-blocked
 * execution that resumes after a restart would re-execute already-completed steps. Deployed
 * applications should use the JPA-backed store from {@code qubere-agent-storage}.
 */
public class InMemoryAgentCheckpointStore implements AgentCheckpointStore {

    private final Map<String, Map<String, AgentCheckpoint>> checkpoints = new ConcurrentHashMap<>();

    @Override
    public void save(AgentCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.executionId() == null || checkpoint.stepName() == null) {
            return;
        }
        checkpoints
                .computeIfAbsent(checkpoint.executionId(), ignored -> new ConcurrentHashMap<>())
                .put(checkpoint.stepName(), checkpoint);
    }

    @Override
    public Optional<AgentCheckpoint> find(String executionId, String stepName) {
        if (executionId == null || stepName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(checkpoints.getOrDefault(executionId, Map.of()).get(stepName));
    }

    @Override
    public List<AgentCheckpoint> findByExecutionId(String executionId) {
        if (executionId == null) {
            return List.of();
        }
        return checkpoints.getOrDefault(executionId, Map.of()).values().stream()
                .sorted(Comparator.comparingInt(AgentCheckpoint::stepIndex))
                .toList();
    }

    @Override
    public void deleteByExecutionId(String executionId) {
        if (executionId != null) {
            checkpoints.remove(executionId);
        }
    }
}
