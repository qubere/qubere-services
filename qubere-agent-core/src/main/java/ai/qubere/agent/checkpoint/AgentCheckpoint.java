package ai.qubere.agent.checkpoint;

import java.time.Instant;

/**
 * Durable record of one completed step within an agent execution.
 *
 * @param executionId execution the step belongs to
 * @param stepName    caller-assigned step name, unique within the execution
 * @param stepIndex   monotonically increasing order in which the step completed
 * @param resultJson  serialized step result, replayed instead of re-executing the step on resume
 * @param completedAt when the step completed
 */
public record AgentCheckpoint(
        String executionId,
        String stepName,
        int stepIndex,
        String resultJson,
        Instant completedAt
) {
    public AgentCheckpoint {
        completedAt = completedAt == null ? Instant.now() : completedAt;
    }
}
