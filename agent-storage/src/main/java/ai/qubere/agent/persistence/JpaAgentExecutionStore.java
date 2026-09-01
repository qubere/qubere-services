package ai.qubere.agent.persistence;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;

import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAgentExecutionStore implements AgentExecutionStore {

    private final AgentExecutionRecordRepository repository;
    private final ObjectMapper objectMapper;

    public JpaAgentExecutionStore(AgentExecutionRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
        upsertExecution(context, descriptor, input, AgentRunStatus.RUNNING, null, null);
    }

    @Override
    @Transactional
    public void markQueued(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
        upsertExecution(context, descriptor, input, AgentRunStatus.QUEUED, null, null);
    }

    @Override
    @Transactional
    public void markQueued(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input, String idempotencyKey) {
        upsertExecution(context, descriptor, input, AgentRunStatus.QUEUED, null, idempotencyKey);
    }

    @Override
    @Transactional
    public void markWaitingForApproval(String executionId, String approvalId, String reason) {
        AgentExecutionRecordEntity entity = repository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution record not found: " + executionId));
        entity.setStatus(AgentRunStatus.WAITING_FOR_APPROVAL);
        entity.setErrorMessage(reason);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Override
    @Transactional
    public void markCancelled(String executionId, String reason) {
        repository.findById(executionId).ifPresent(entity -> {
            entity.setStatus(AgentRunStatus.CANCELLED);
            entity.setErrorMessage(reason);
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        });
    }

    private void upsertExecution(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input, AgentRunStatus status, String errorMessage, String idempotencyKey) {
        Instant now = Instant.now();
        AgentExecutionRecordEntity entity = repository.findById(context.executionId())
                .orElseGet(() -> {
                    AgentExecutionRecordEntity newEntity = new AgentExecutionRecordEntity();
                    newEntity.setExecutionId(context.executionId());
                    newEntity.setCreatedAt(now);
                    return newEntity;
                });
        entity.setExecutionId(context.executionId());
        entity.setAgentId(descriptor.id());
        entity.setAgentVersion(descriptor.version());
        entity.setTenantId(context.tenantId());
        entity.setActorId(context.actorId());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            entity.setIdempotencyKey(idempotencyKey.trim());
        }
        entity.setStatus(status);
        entity.setInputJson(toJson(input));
        entity.setErrorMessage(errorMessage);
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    @Override
    @Transactional
    public void markCompleted(String executionId, AgentOutput output) {
        AgentExecutionRecordEntity entity = repository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution record not found: " + executionId));
        entity.setStatus(AgentRunStatus.SUCCEEDED);
        entity.setOutputJson(toJson(output));
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Override
    @Transactional
    public void markFailed(String executionId, Throwable failure) {
        repository.findById(executionId).ifPresent(entity -> {
            entity.setStatus(AgentRunStatus.FAILED);
            entity.setErrorMessage(failure.getMessage());
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentExecutionRecord> findByExecutionId(String executionId) {
        return repository.findById(executionId).map(this::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentExecutionRecord> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findFirstByTenantIdAndIdempotencyKeyOrderByCreatedAtDesc(tenantId, idempotencyKey.trim())
                .map(this::toRecord);
    }

    private AgentExecutionRecord toRecord(AgentExecutionRecordEntity entity) {
        return new AgentExecutionRecord(
                entity.getExecutionId(),
                entity.getAgentId(),
                entity.getAgentVersion(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getStatus(),
                entity.getInputJson(),
                entity.getOutputJson(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getIdempotencyKey()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize agent payload", ex);
        }
    }
}

