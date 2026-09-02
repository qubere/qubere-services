package ai.qubere.agent.tools;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.redaction.AgentRedactionService;
import ai.qubere.agent.redaction.DefaultAgentRedactionService;
import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.agent.runtime.AgentRunBudget;
import ai.qubere.agent.runtime.AgentWorkflowBudget;
import ai.qubere.agent.runtime.AgentWorkflowContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ToolExecutionService {

    private final ToolRegistry registry;
    private final ToolApprovalPolicy approvalPolicy;
    private final ToolAuditService auditService;
    private final ToolApprovalRequestSink approvalRequestSink;
    private final ToolCallRecorder toolCallRecorder;
    private final AgentRedactionService redactionService;
    private final AgentResilienceGateway resilienceGateway;

    public ToolExecutionService(Collection<AgentTool> tools) {
        this(new ToolRegistry(tools), ToolApprovalPolicy.defaultPolicy(), ToolAuditService.noop(), ToolApprovalRequestSink.noop(), ToolCallRecorder.noop());
    }

    public ToolExecutionService(ToolRegistry registry, ToolApprovalPolicy approvalPolicy, ToolAuditService auditService) {
        this(registry, approvalPolicy, auditService, ToolApprovalRequestSink.noop(), ToolCallRecorder.noop());
    }

    public ToolExecutionService(ToolRegistry registry, ToolApprovalPolicy approvalPolicy, ToolAuditService auditService, ToolApprovalRequestSink approvalRequestSink) {
        this(registry, approvalPolicy, auditService, approvalRequestSink, ToolCallRecorder.noop());
    }

    public ToolExecutionService(
            ToolRegistry registry,
            ToolApprovalPolicy approvalPolicy,
            ToolAuditService auditService,
            ToolApprovalRequestSink approvalRequestSink,
            ToolCallRecorder toolCallRecorder
    ) {
        this(registry, approvalPolicy, auditService, approvalRequestSink, toolCallRecorder, new DefaultAgentRedactionService());
    }

    public ToolExecutionService(
            ToolRegistry registry,
            ToolApprovalPolicy approvalPolicy,
            ToolAuditService auditService,
            ToolApprovalRequestSink approvalRequestSink,
            ToolCallRecorder toolCallRecorder,
            AgentRedactionService redactionService
    ) {
        this(registry, approvalPolicy, auditService, approvalRequestSink, toolCallRecorder, redactionService, AgentResilienceGateway.noop());
    }

    public ToolExecutionService(
            ToolRegistry registry,
            ToolApprovalPolicy approvalPolicy,
            ToolAuditService auditService,
            ToolApprovalRequestSink approvalRequestSink,
            ToolCallRecorder toolCallRecorder,
            AgentRedactionService redactionService,
            AgentResilienceGateway resilienceGateway
    ) {
        this.registry = registry;
        this.approvalPolicy = approvalPolicy == null ? ToolApprovalPolicy.defaultPolicy() : approvalPolicy;
        this.auditService = auditService == null ? ToolAuditService.noop() : auditService;
        this.approvalRequestSink = approvalRequestSink == null ? ToolApprovalRequestSink.noop() : approvalRequestSink;
        this.toolCallRecorder = toolCallRecorder == null ? ToolCallRecorder.noop() : toolCallRecorder;
        this.redactionService = redactionService == null ? new DefaultAgentRedactionService() : redactionService;
        this.resilienceGateway = resilienceGateway == null ? AgentResilienceGateway.noop() : resilienceGateway;
    }

    public ToolResult execute(ToolExecutionRequest request) {
        return doExecute(request, null, false);
    }

    public ToolResult executeApproved(ToolExecutionRequest request, String approvalId) {
        return doExecute(request, approvalId, true);
    }

    private ToolResult doExecute(ToolExecutionRequest request, String approvedApprovalId, boolean approvalAlreadyGranted) {
        AgentTool tool = registry.findTool(request.toolName())
                .orElseThrow(() -> new AgentExecutionException(
                        AgentErrorCode.TOOL_NOT_FOUND,
                        "Tool is not registered: " + request.toolName()
                ));
        ToolDescriptor descriptor = tool.descriptor();
        String callId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();

        try {
            ensureToolAllowedByPolicy(descriptor, request, callId, startedAt);
            ensureDryRunSafety(descriptor, request, callId, startedAt);
            ensurePermissions(descriptor, request, callId, startedAt);
            if (!approvalAlreadyGranted) {
                ensureApproval(descriptor, request, callId, startedAt);
            }
            consumeRunBudget(descriptor, request, callId, startedAt);

            recordCall(request, descriptor, callId, ToolCallStatus.STARTED, null, null, approvedApprovalId, startedAt, null);
            record(request, ToolAuditStatus.STARTED, approvalAlreadyGranted ? "Approved tool execution started" : "Tool execution started", approvedApprovalId == null ? Map.of() : Map.of("approvalId", approvedApprovalId));
            recordInvokedToolForRedTeam(request, descriptor);
            ToolResult result = resilienceGateway.execute("tool:" + descriptor.name(), () -> tool.execute(new ToolInput(
                    request.context().executionId(),
                    request.context().tenantId(),
                    request.context().actorId(),
                    request.arguments(),
                    request.context()
            )));
            ToolCallStatus status = result.success() ? ToolCallStatus.SUCCEEDED : ToolCallStatus.FAILED;
            recordCall(request, descriptor, callId, status, summarizeResult(request, result.values()), result.errorMessage(), approvedApprovalId, startedAt, Instant.now());
            record(request, result.success() ? ToolAuditStatus.SUCCEEDED : ToolAuditStatus.FAILED, result.errorMessage(), approvedApprovalId == null ? Map.of() : Map.of("approvalId", approvedApprovalId));
            return result;
        } catch (AgentExecutionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordCall(request, descriptor, callId, ToolCallStatus.FAILED, null, ex.getMessage(), approvedApprovalId, startedAt, Instant.now());
            record(request, ToolAuditStatus.FAILED, ex.getMessage(), approvedApprovalId == null ? Map.of() : Map.of("approvalId", approvedApprovalId));
            throw new AgentExecutionException(AgentErrorCode.TOOL_FAILED, "Tool execution failed: " + descriptor.name(), ex);
        }
    }

    private void ensureToolAllowedByPolicy(ToolDescriptor descriptor, ToolExecutionRequest request, String callId, Instant startedAt) {
        if (request.policy() == null || !request.policy().allowToolCalls()) {
            reject(request, descriptor, callId, startedAt, "Tool calls are disabled for this run: " + descriptor.name());
        }
        Set<String> allowedTools = request.policy().allowedTools();
        if (!allowedTools.isEmpty() && !allowedTools.contains(descriptor.name())) {
            reject(request, descriptor, callId, startedAt, "Tool is not allowed for this run: " + descriptor.name());
        }
    }

    private void ensureDryRunSafety(ToolDescriptor descriptor, ToolExecutionRequest request, String callId, Instant startedAt) {
        if (request.policy() == null || !request.policy().dryRun()) {
            return;
        }
        boolean safeInDryRun = descriptor.sideEffects().isEmpty()
                || descriptor.sideEffects().stream().allMatch(sideEffect -> sideEffect == ToolSideEffect.NONE || sideEffect == ToolSideEffect.READ_EXTERNAL);
        if (!safeInDryRun) {
            reject(request, descriptor, callId, startedAt, "Dry-run mode blocks side-effecting tool: " + descriptor.name());
        }
    }

    private void consumeRunBudget(ToolDescriptor descriptor, ToolExecutionRequest request, String callId, Instant startedAt) {
        // Workflow-wide ceiling is checked first so an orchestration that has already exhausted
        // its aggregate tool budget is rejected regardless of the individual agent's remaining
        // per-run allowance.
        AgentWorkflowBudget workflowBudget = AgentWorkflowContext.workflowBudget(request.context());
        if (workflowBudget != null) {
            try {
                workflowBudget.consumeToolCall(descriptor.name());
            } catch (AgentExecutionException ex) {
                reject(request, descriptor, callId, startedAt, ex.getMessage());
            }
        }
        Object budget = request.context().attributes().get("agentRunBudget");
        if (!(budget instanceof AgentRunBudget agentRunBudget)) {
            return;
        }
        try {
            agentRunBudget.consumeToolCall(descriptor.name());
        } catch (AgentExecutionException ex) {
            reject(request, descriptor, callId, startedAt, ex.getMessage());
        }
    }

    /**
     * Records the tool name into the red-team scratch set when a red-team run is in progress, so
     * an adversarial case can assert the agent never reached for a forbidden tool even if its
     * final output looks harmless. No-op outside red-team runs.
     */
    @SuppressWarnings("unchecked")
    private void recordInvokedToolForRedTeam(ToolExecutionRequest request, ToolDescriptor descriptor) {
        Object invoked = request.context().attributes().get("redTeamInvokedTools");
        if (invoked instanceof java.util.Set<?> set) {
            ((java.util.Set<String>) set).add(descriptor.name());
        }
    }

    private void ensurePermissions(ToolDescriptor descriptor, ToolExecutionRequest request, String callId, Instant startedAt) {
        if (descriptor.requiredPermissions().isEmpty()) {
            return;
        }
        Object permissionsAttribute = request.context().attributes().getOrDefault("permissions", Set.of());
        Set<?> permissions = permissionsAttribute instanceof Set<?> permissionSet ? permissionSet : Set.of();
        if (!permissions.containsAll(descriptor.requiredPermissions())) {
            reject(request, descriptor, callId, startedAt, "Caller is missing required permissions for tool: " + descriptor.name());
        }
    }

    private void ensureApproval(ToolDescriptor descriptor, ToolExecutionRequest request, String callId, Instant startedAt) {
        ToolApprovalDecision decision = approvalPolicy.evaluate(descriptor, request);
        if (decision.allowed()) {
            return;
        }
        String approvalId = decision.approvalRequired()
                ? approvalRequestSink.requestApproval(descriptor, request, decision).orElse(null)
                : null;
        ToolCallStatus status = decision.approvalRequired() ? ToolCallStatus.APPROVAL_REQUIRED : ToolCallStatus.REJECTED;
        recordCall(request, descriptor, callId, status, null, decision.reason(), approvalId, startedAt, Instant.now());
        record(
                request,
                decision.approvalRequired() ? ToolAuditStatus.APPROVAL_REQUIRED : ToolAuditStatus.REJECTED,
                decision.reason(),
                approvalId == null ? Map.of() : Map.of("approvalId", approvalId)
        );
        if (decision.approvalRequired()) {
            String message = approvalId == null ? decision.reason() : decision.reason() + " approvalId=" + approvalId;
            throw new ToolApprovalRequiredException(message, approvalId);
        }
        throw new AgentExecutionException(AgentErrorCode.TOOL_NOT_ALLOWED, decision.reason());
    }

    private void reject(ToolExecutionRequest request, ToolDescriptor descriptor, String callId, Instant startedAt, String reason) {
        recordCall(request, descriptor, callId, ToolCallStatus.REJECTED, null, reason, null, startedAt, Instant.now());
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
                redactionService.redactText(message),
                redactionService.redactMap(metadata),
                null
        ));
    }

    private void recordCall(
            ToolExecutionRequest request,
            ToolDescriptor descriptor,
            String callId,
            ToolCallStatus status,
            String outputSummary,
            String errorMessage,
            String approvalId,
            Instant startedAt,
            Instant completedAt
    ) {
        toolCallRecorder.record(new ToolCallRecord(
                callId,
                request.context().executionId(),
                request.context().tenantId(),
                request.context().actorId(),
                request.context().correlationId(),
                descriptor.name(),
                descriptor.riskLevel(),
                descriptor.sideEffects(),
                status,
                redactionService.redactMap(request.arguments()),
                redactionService.redactText(outputSummary),
                redactionService.redactText(errorMessage),
                approvalId,
                startedAt,
                completedAt,
                latencyMs(startedAt, completedAt)
        ));
    }

    private Long latencyMs(Instant startedAt, Instant completedAt) {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return Duration.between(startedAt, completedAt).toMillis();
    }

    private String summarizeResult(ToolExecutionRequest request, Map<String, Object> values) {
        if (request.policy() == null || !request.policy().logToolResults()) {
            return "Tool result logging disabled";
        }
        return summarize(redactionService.redactMap(values));
    }

    private String summarize(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String summary = values.toString();
        return summary.length() <= 1000 ? summary : summary.substring(0, 1000);
    }
}
