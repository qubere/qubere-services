package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;

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
}
