package ai.qubere.agent.async;

import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.tools.ToolApprovalDecision;
import ai.qubere.agent.tools.ToolApprovalRequestSink;
import ai.qubere.agent.tools.ToolDescriptor;
import ai.qubere.agent.tools.ToolExecutionRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AgentToolApprovalRequestSink implements ToolApprovalRequestSink {

    public static final String APPROVAL_TYPE_TOOL_EXECUTION = "TOOL_EXECUTION";

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
                metadata(descriptor, request),
                Instant.now(),
                Instant.now().plus(properties.getAsync().getApprovalExpirationMinutes(), ChronoUnit.MINUTES),
                null,
                null
        );
        approvalStore.create(approval);
        executionStore.markWaitingForApproval(request.context().executionId(), approvalId, decision.reason());
        return Optional.of(approvalId);
    }

    private Map<String, Object> metadata(ToolDescriptor descriptor, ToolExecutionRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("approvalType", APPROVAL_TYPE_TOOL_EXECUTION);
        metadata.put("toolName", descriptor.name());
        metadata.put("toolRiskLevel", descriptor.riskLevel().name());
        metadata.put("toolSideEffects", descriptor.sideEffects().stream().map(Enum::name).toList());
        metadata.put("toolArguments", request.arguments());
        metadata.put("correlationId", request.context().correlationId());
        Object permissions = request.context().attributes().get("permissions");
        if (permissions != null) {
            metadata.put("permissions", permissions);
        }
        ResolvedAgentPolicy policy = request.policy();
        if (policy != null) {
            metadata.put("logToolResults", policy.logToolResults());
            metadata.put("dryRun", policy.dryRun());
        }
        return metadata;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
