package ai.qubere.agent.persistence;

import ai.qubere.agent.async.AgentApprovalRequest;
import ai.qubere.agent.async.AgentApprovalStatus;
import ai.qubere.agent.async.AgentApprovalStore;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAgentApprovalStore implements AgentApprovalStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentApprovalRequestRepository repository;
    private final ObjectMapper objectMapper;

    public JpaAgentApprovalStore(AgentApprovalRequestRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AgentApprovalRequest create(AgentApprovalRequest request) {
        return toRequest(repository.save(toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentApprovalRequest> findById(String approvalId) {
        return repository.findById(approvalId).map(this::toRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentApprovalRequest> findPendingByExecutionId(String executionId) {
        return repository.findFirstByExecutionIdAndStatusOrderByCreatedAtDesc(executionId, AgentApprovalStatus.PENDING)
                .map(this::toRequest);
    }

    @Override
    @Transactional
    public AgentApprovalRequest approve(String approvalId, String decidedBy) {
        return decide(approvalId, decidedBy, AgentApprovalStatus.APPROVED);
    }

    @Override
    @Transactional
    public AgentApprovalRequest reject(String approvalId, String decidedBy) {
        return decide(approvalId, decidedBy, AgentApprovalStatus.REJECTED);
    }

    @Override
    @Transactional
    public AgentApprovalRequest expire(String approvalId) {
        return decide(approvalId, "system", AgentApprovalStatus.EXPIRED);
    }

    private AgentApprovalRequest decide(String approvalId, String decidedBy, AgentApprovalStatus status) {
        AgentApprovalRequestEntity entity = repository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + approvalId));
        if (entity.getStatus() != AgentApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request is already decided: " + approvalId);
        }
        entity.setStatus(status);
        entity.setDecidedBy(decidedBy);
        entity.setDecidedAt(Instant.now());
        return toRequest(repository.save(entity));
    }

    private AgentApprovalRequestEntity toEntity(AgentApprovalRequest request) {
        AgentApprovalRequestEntity entity = new AgentApprovalRequestEntity();
        entity.setApprovalId(request.approvalId());
        entity.setExecutionId(request.executionId());
        entity.setAgentId(request.agentId());
        entity.setAgentVersion(request.agentVersion());
        entity.setTenantId(request.tenantId());
        entity.setRequestedBy(request.requestedBy());
        entity.setStatus(request.status());
        entity.setReason(request.reason());
        entity.setMetadataJson(toJson(request.metadata()));
        entity.setCreatedAt(request.createdAt());
        entity.setExpiresAt(request.expiresAt());
        entity.setDecidedAt(request.decidedAt());
        entity.setDecidedBy(request.decidedBy());
        return entity;
    }

    private AgentApprovalRequest toRequest(AgentApprovalRequestEntity entity) {
        return new AgentApprovalRequest(
                entity.getApprovalId(),
                entity.getExecutionId(),
                entity.getAgentId(),
                entity.getAgentVersion(),
                entity.getTenantId(),
                entity.getRequestedBy(),
                entity.getStatus(),
                entity.getReason(),
                fromJson(entity.getMetadataJson()),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getDecidedAt(),
                entity.getDecidedBy()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize approval metadata", ex);
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to deserialize approval metadata", ex);
        }
    }
}
