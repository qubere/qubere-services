package ai.qubere.agent.persistence;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.async.AgentPendingCommandStore;
import ai.qubere.agent.async.AgentRunCommand;
import ai.qubere.agent.core.AgentRunOptions;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAgentPendingCommandStore implements AgentPendingCommandStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentPendingCommandRepository repository;
    private final ObjectMapper objectMapper;

    public JpaAgentPendingCommandStore(AgentPendingCommandRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(AgentRunCommand command) {
        AgentPendingCommandEntity entity = new AgentPendingCommandEntity();
        entity.setExecutionId(command.context().executionId());
        entity.setAgentId(command.agentId());
        entity.setAgentVersion(command.agentVersion());
        entity.setInputType(command.input().getClass().getName());
        entity.setInputJson(toJson(command.input()));
        entity.setOptionsJson(toJson(command.options()));
        entity.setTenantId(command.context().tenantId());
        entity.setActorId(command.context().actorId());
        entity.setCorrelationId(command.context().correlationId());
        entity.setRequestedAt(command.context().requestedAt());
        entity.setContextAttributesJson(toJson(command.context().attributes()));
        entity.setCallbackUrl(command.callbackUrl());
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentRunCommand> findByExecutionId(String executionId) {
        return repository.findById(executionId).map(this::toCommand);
    }

    @Override
    @Transactional
    public void delete(String executionId) {
        repository.deleteById(executionId);
    }

    private AgentRunCommand toCommand(AgentPendingCommandEntity entity) {
        AgentExecutionContext context = new AgentExecutionContext(
                entity.getExecutionId(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getCorrelationId(),
                entity.getRequestedAt(),
                fromJson(entity.getContextAttributesJson())
        );
        return new AgentRunCommand(
                entity.getAgentId(),
                entity.getAgentVersion(),
                fromInputJson(entity.getInputType(), entity.getInputJson()),
                context,
                fromOptionsJson(entity.getOptionsJson()),
                entity.getCallbackUrl()
        );
    }

    private AgentInput fromInputJson(String inputType, String inputJson) {
        try {
            Class<?> inputClass = Class.forName(inputType);
            Object input = objectMapper.readValue(inputJson, inputClass);
            if (!(input instanceof AgentInput agentInput)) {
                throw new IllegalArgumentException("Stored input type does not implement AgentInput: " + inputType);
            }
            return agentInput;
        } catch (ClassNotFoundException | JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to deserialize pending agent input: " + inputType, ex);
        }
    }

    private AgentRunOptions fromOptionsJson(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank() || "null".equals(optionsJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(optionsJson, AgentRunOptions.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to deserialize pending agent run options", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize pending command", ex);
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to deserialize pending command context attributes", ex);
        }
    }
}
