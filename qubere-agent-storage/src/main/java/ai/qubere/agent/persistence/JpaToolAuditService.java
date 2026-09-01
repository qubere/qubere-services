package ai.qubere.agent.persistence;

import ai.qubere.agent.tools.ToolAuditEvent;
import ai.qubere.agent.tools.ToolAuditService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaToolAuditService implements ToolAuditService {

    private final AgentToolAuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public JpaToolAuditService(AgentToolAuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void record(ToolAuditEvent event) {
        AgentToolAuditEventEntity entity = new AgentToolAuditEventEntity();
        entity.setExecutionId(event.executionId());
        entity.setTenantId(event.tenantId());
        entity.setActorId(event.actorId());
        entity.setToolName(event.toolName());
        entity.setStatus(event.status());
        entity.setMessage(event.message());
        entity.setMetadataJson(toJson(event.metadata()));
        entity.setOccurredAt(event.occurredAt());
        repository.save(entity);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize tool audit metadata", ex);
        }
    }
}
