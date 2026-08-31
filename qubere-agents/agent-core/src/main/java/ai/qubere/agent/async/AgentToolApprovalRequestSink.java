package ai.qubere.agent.async;

import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.tools.ToolApprovalDecision;
import ai.qubere.agent.tools.ToolApprovalRequestSink;
import ai.qubere.agent.tools.ToolDescriptor;
import ai.qubere.agent.tools.ToolExecutionRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AgentToolApprovalRequestSink implements ToolApprovalRequestSink {

    private final AgentApprovalStore approvalStore;
    private final AgentExecutionStore executionStore;
    private final AgentPlatformProperties properties;

    public AgentToolApprovalRequestSink(AgentApprovalStore approvalStore, AgentExecutionStore executionStore, AgentPlatformProperties properties) {
        this.approvalStore = approvalStore;
        this.executionStore = executionStore;
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
    }

    @Override
    public Optional<String> requestApproval(ToolDescriptor descriptor, ToolExecutionRequest request, ToolApprovalDecision decision) {
        String approvalId = UUID.randomUUID().toString();
        AgentApprovalRequest approval = new AgentApprovalRequest(
                approvalId,
                request.context().executionId(),
                asString(request.context().attributes().get("agentId")),
                asString(request.context().attributes().get("agentVersion")),
                request.context().tenantId(),
                request.context().actorId(),
                AgentApprovalStatus.PENDING,
                decision.reason(),
                Map.of(
                        "approvalType", "TOOL_EXECUTION",
                        "toolName", descriptor.name(),
                        "toolRiskLevel", descriptor.riskLevel().name()
                ),
                Instant.now(),
                Instant.now().plus(properties.getAsync().getApprovalExpirationMinutes(), ChronoUnit.MINUTES),
                null,
                null
        );
        approvalStore.create(approval);
        executionStore.markWaitingForApproval(request.context().executionId(), approvalId, decision.reason());
        return Optional.of(approvalId);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
