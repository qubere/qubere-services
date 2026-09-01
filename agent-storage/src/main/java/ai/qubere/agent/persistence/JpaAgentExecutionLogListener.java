package ai.qubere.agent.persistence;

import ai.qubere.agent.runtime.AgentPipelineEvent;
import ai.qubere.agent.runtime.AgentPipelineListener;
import ai.qubere.agent.runtime.AgentPipelineStep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAgentExecutionLogListener implements AgentPipelineListener {

    private final AgentExecutionLogRepository repository;
    private final ObjectMapper objectMapper;

    public JpaAgentExecutionLogListener(AgentExecutionLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEvent(AgentPipelineEvent event) {
        AgentExecutionLogEntity entity = new AgentExecutionLogEntity();
        entity.setExecutionId(event.context().executionId());
        entity.setAgentId(event.descriptor().id());
        entity.setAgentVersion(event.descriptor().version());
        entity.setTenantId(event.context().tenantId());
        entity.setActorId(event.context().actorId());
        entity.setCorrelationId(event.context().correlationId());
        entity.setStep(event.step());
        entity.setLogLevel(logLevel(event.step()));
        entity.setMessage(event.message());
        entity.setAttributesJson(toJson(event.attributes()));
        entity.setOccurredAt(event.occurredAt());
        repository.save(entity);
    }

    private String logLevel(AgentPipelineStep step) {
        return step == AgentPipelineStep.EXECUTION_FAILED ? "ERROR" : "INFO";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize agent execution log attributes", ex);
        }
    }
}
