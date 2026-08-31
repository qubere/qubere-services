package ai.qubere.agent.tools;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class ToolExecutionService {

    private final ToolRegistry registry;
    private final ToolApprovalPolicy approvalPolicy;
    private final ToolAuditService auditService;
    private final ToolApprovalRequestSink approvalRequestSink;

    public ToolExecutionService(Collection<AgentTool> tools) {
        this(new ToolRegistry(tools), ToolApprovalPolicy.defaultPolicy(), ToolAuditService.noop(), ToolApprovalRequestSink.noop());
    }

    public ToolExecutionService(ToolRegistry registry, ToolApprovalPolicy approvalPolicy, ToolAuditService auditService) {
        this(registry, approvalPolicy, auditService, ToolApprovalRequestSink.noop());
    }

    public ToolExecutionService(ToolRegistry registry, ToolApprovalPolicy approvalPolicy, ToolAuditService auditService, ToolApprovalRequestSink approvalRequestSink) {
        this.registry = registry;
        this.approvalPolicy = approvalPolicy == null ? ToolApprovalPolicy.defaultPolicy() : approvalPolicy;
        this.auditService = auditService == null ? ToolAuditService.noop() : auditService;
        this.approvalRequestSink = approvalRequestSink == null ? ToolApprovalRequestSink.noop() : approvalRequestSink;
    }

    public ToolResult execute(ToolExecutionRequest request) {
        AgentTool tool = registry.findTool(request.toolName())
                .orElseThrow(() -> new AgentExecutionException(
                        AgentErrorCode.TOOL_NOT_FOUND,
                        "Tool is not registered: " + request.toolName()
                ));
        ToolDescriptor descriptor = tool.descriptor();
        ensureToolAllowedByPolicy(descriptor, request);
        ensurePermissions(descriptor, request);
        ensureApproval(descriptor, request);

        record(request, ToolAuditStatus.STARTED, "Tool execution started", Map.of());
        try {
            ToolResult result = tool.execute(new ToolInput(
                    request.context().executionId(),
                    request.context().tenantId(),
                    request.context().actorId(),
                    request.arguments()
            ));
            record(request, result.success() ? ToolAuditStatus.SUCCEEDED : ToolAuditStatus.FAILED, result.errorMessage(), Map.of());
            return result;
        } catch (RuntimeException ex) {
            record(request, ToolAuditStatus.FAILED, ex.getMessage(), Map.of());
            throw new AgentExecutionException(AgentErrorCode.TOOL_FAILED, "Tool execution failed: " + descriptor.name(), ex);
        }
    }

    private void ensureToolAllowedByPolicy(ToolDescriptor descriptor, ToolExecutionRequest request) {
        if (request.policy() == null || !request.policy().allowToolCalls()) {
            reject(request, "Tool calls are disabled for this run: " + descriptor.name());
        }
        Set<String> allowedTools = request.policy().allowedTools();
        if (!allowedTools.isEmpty() && !allowedTools.contains(descriptor.name())) {
            reject(request, "Tool is not allowed for this run: " + descriptor.name());
        }
    }

    private void ensurePermissions(ToolDescriptor descriptor, ToolExecutionRequest request) {
        if (descriptor.requiredPermissions().isEmpty()) {
            return;
        }
        Object permissionsAttribute = request.context().attributes().getOrDefault("permissions", Set.of());
        Set<?> permissions = permissionsAttribute instanceof Set<?> permissionSet ? permissionSet : Set.of();
        if (!permissions.containsAll(descriptor.requiredPermissions())) {
            reject(request, "Caller is missing required permissions for tool: " + descriptor.name());
        }
    }

    private void ensureApproval(ToolDescriptor descriptor, ToolExecutionRequest request) {
        ToolApprovalDecision decision = approvalPolicy.evaluate(descriptor, request);
        if (decision.allowed()) {
            return;
        }
        String approvalId = decision.approvalRequired()
                ? approvalRequestSink.requestApproval(descriptor, request, decision).orElse(null)
                : null;
        record(
                request,
                decision.approvalRequired() ? ToolAuditStatus.APPROVAL_REQUIRED : ToolAuditStatus.REJECTED,
                decision.reason(),
                approvalId == null ? Map.of() : Map.of("approvalId", approvalId)
        );
        AgentErrorCode errorCode = decision.approvalRequired()
                ? AgentErrorCode.TOOL_APPROVAL_REQUIRED
                : AgentErrorCode.TOOL_NOT_ALLOWED;
        String message = approvalId == null ? decision.reason() : decision.reason() + " approvalId=" + approvalId;
        throw new AgentExecutionException(errorCode, message);
    }

    private void reject(ToolExecutionRequest request, String reason) {
        record(request, ToolAuditStatus.REJECTED, reason, Map.of());
        throw new AgentExecutionException(AgentErrorCode.TOOL_NOT_ALLOWED, reason);
    }

    private void record(ToolExecutionRequest request, ToolAuditStatus status, String message, Map<String, Object> metadata) {
        auditService.record(new ToolAuditEvent(
                request.context().executionId(),
                request.context().tenantId(),
                request.context().actorId(),
                request.toolName(),
                status,
                message,
                metadata,
                null
        ));
    }
}
