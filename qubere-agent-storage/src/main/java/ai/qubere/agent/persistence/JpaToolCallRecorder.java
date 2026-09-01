package ai.qubere.agent.persistence;

import ai.qubere.agent.tools.ToolCallRecord;
import ai.qubere.agent.tools.ToolCallRecorder;
import ai.qubere.agent.tools.ToolSideEffect;

import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaToolCallRecorder implements ToolCallRecorder {

    private final AgentToolCallRepository repository;
    private final ObjectMapper objectMapper;

    public JpaToolCallRecorder(AgentToolCallRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void record(ToolCallRecord record) {
        AgentToolCallEntity entity = repository.findById(record.callId())
                .orElseGet(() -> {
                    AgentToolCallEntity newEntity = new AgentToolCallEntity();
                    newEntity.setCallId(record.callId());
                    return newEntity;
                });
        entity.setExecutionId(record.executionId());
        entity.setTenantId(record.tenantId());
        entity.setActorId(record.actorId());
        entity.setCorrelationId(record.correlationId());
        entity.setToolName(record.toolName());
        entity.setToolRiskLevel(record.riskLevel());
        entity.setSideEffects(record.sideEffects().stream()
                .map(ToolSideEffect::name)
                .sorted()
                .collect(Collectors.joining(",")));
        entity.setStatus(record.status());
        entity.setInputJson(toJson(record.inputArguments()));
        entity.setOutputSummary(record.outputSummary());
        entity.setErrorMessage(record.errorMessage());
        entity.setApprovalId(record.approvalId());
        entity.setStartedAt(record.startedAt());
        entity.setCompletedAt(record.completedAt());
        entity.setLatencyMs(record.latencyMs());
        repository.save(entity);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize tool call input", ex);
        }
    }
}