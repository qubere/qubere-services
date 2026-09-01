package ai.qubere.agent.persistence;

import ai.qubere.agent.prompts.PromptStatus;
import ai.qubere.agent.prompts.PromptTemplate;
import ai.qubere.agent.prompts.PromptVersionStore;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaPromptVersionStore implements PromptVersionStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentPromptTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public JpaPromptVersionStore(AgentPromptTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PromptTemplate save(PromptTemplate template) {
        AgentPromptTemplateEntity entity = new AgentPromptTemplateEntity();
        entity.setPromptId(template.promptId());
        entity.setVersion(template.version());
        entity.setAgentId(template.agentId());
        entity.setStatus(template.status());
        entity.setSystemTemplate(template.systemTemplate());
        entity.setUserTemplate(template.userTemplate());
        entity.setMetadataJson(toJson(template.metadata()));
        entity.setCreatedAt(template.createdAt());
        entity.setUpdatedAt(template.updatedAt());
        return toTemplate(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PromptTemplate> find(String promptId, String version) {
        return repository.findById(new AgentPromptTemplateId(promptId, version)).map(this::toTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PromptTemplate> findActiveForAgent(String agentId) {
        return repository.findFirstByAgentIdAndStatusOrderByVersionDesc(agentId, PromptStatus.ACTIVE)
                .map(this::toTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<PromptTemplate> listForAgent(String agentId) {
        return repository.findByAgentIdOrderByVersionAsc(agentId).stream()
                .map(this::toTemplate)
                .toList();
    }

    private PromptTemplate toTemplate(AgentPromptTemplateEntity entity) {
        return new PromptTemplate(
                entity.getPromptId(),
                entity.getAgentId(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getSystemTemplate(),
                entity.getUserTemplate(),
                fromJson(entity.getMetadataJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize prompt metadata", ex);
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to deserialize prompt metadata", ex);
        }
    }
}
