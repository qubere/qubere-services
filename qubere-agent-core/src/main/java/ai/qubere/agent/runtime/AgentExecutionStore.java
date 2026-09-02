package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;

import java.util.List;
import java.util.Optional;

public interface AgentExecutionStore {

    default void markQueued(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
        markStarted(context, descriptor, input);
    }

    default void markQueued(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input, String idempotencyKey) {
        markQueued(context, descriptor, input);
    }

    void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input);

    void markCompleted(String executionId, AgentOutput output);

    void markFailed(String executionId, Throwable failure);

    default void markWaitingForApproval(String executionId, String approvalId, String reason) {
    }

    default void markCancelled(String executionId, String reason) {
    }

    Optional<AgentExecutionRecord> findByExecutionId(String executionId);

    default Optional<AgentExecutionRecord> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return Optional.empty();
    }

    /**
     * Returns every execution belonging to one orchestrated workflow, oldest first, so callers
     * can reconstruct the full call tree of a multi-agent run. Implementations that do not
     * persist workflow linkage return an empty list.
     *
     * @param workflowId workflow id shared by all executions in the orchestration
     * @param limit      maximum number of records to return; implementations must bound results
     */
    default List<AgentExecutionRecord> findByWorkflowId(String workflowId, int limit) {
        return List.of();
    }
}
