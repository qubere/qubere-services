package ai.qubere.agent.persistence;

import ai.qubere.agent.ai.ModelUsageRecord;
import ai.qubere.agent.ai.ModelUsageRecorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaModelUsageRecorder implements ModelUsageRecorder {

    private final AgentModelUsageRepository repository;
    private final ObjectMapper objectMapper;

    public JpaModelUsageRecorder(AgentModelUsageRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void record(ModelUsageRecord record) {
        AgentModelUsageEntity entity = repository.findById(record.usageId())
                .orElseGet(() -> {
                    AgentModelUsageEntity newEntity = new AgentModelUsageEntity();
                    newEntity.setUsageId(record.usageId());
                    return newEntity;
                });
        entity.setExecutionId(record.executionId());
        entity.setTenantId(record.tenantId());
        entity.setActorId(record.actorId());
        entity.setCorrelationId(record.correlationId());
        entity.setAgentId(record.agentId());
        entity.setAgentVersion(record.agentVersion());
        entity.setModelProvider(record.modelProvider());
        entity.setModelName(record.modelName());
        entity.setPromptVersion(record.promptVersion());
        entity.setStatus(record.status());
        entity.setInputTokens(record.inputTokens());
        entity.setOutputTokens(record.outputTokens());
        entity.setTotalTokens(record.totalTokens());
        entity.setEstimatedCostUsd(record.estimatedCostUsd());
        entity.setLatencyMs(record.latencyMs());
        entity.setErrorMessage(record.errorMessage());
        entity.setMetadataJson(toJson(record.metadata()));
        entity.setStartedAt(record.startedAt());
        entity.setCompletedAt(record.completedAt());
        repository.save(entity);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize model usage metadata", ex);
        }
    }
}