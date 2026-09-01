package ai.qubere.agent.async;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentDecisionDraft;
import ai.qubere.agent.core.AgentRunMode;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentPolicyResolver;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.RegisteredAgent;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.tools.ToolApprovalRequiredException;
import ai.qubere.agent.tools.ToolExecutionRequest;
import ai.qubere.agent.tools.ToolExecutionService;
import ai.qubere.agent.tools.ToolResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AgentAsyncRuntimeService {

    private final AgentRuntimeService runtimeService;
    private final AgentRegistry registry;
    private final AgentPolicyResolver policyResolver;
    private final AgentExecutionStore executionStore;
    private final AgentAsyncQueue queue;
    private final AgentApprovalStore approvalStore;
    private final AgentPendingCommandStore pendingCommandStore;
    private final ToolExecutionService toolExecutionService;
    private final AgentCallbackDispatcher callbackDispatcher;
    private final AgentPlatformProperties properties;

    public AgentAsyncRuntimeService(
            AgentRuntimeService runtimeService,
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentExecutionStore executionStore,
            AgentAsyncQueue queue,
            AgentApprovalStore approvalStore,
            AgentPendingCommandStore pendingCommandStore,
            AgentCallbackDispatcher callbackDispatcher,
            AgentPlatformProperties properties
    ) {
        this(runtimeService, registry, policyResolver, executionStore, queue, approvalStore, pendingCommandStore, null, callbackDispatcher, properties);
    }

    public AgentAsyncRuntimeService(
            AgentRuntimeService runtimeService,
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentExecutionStore executionStore,
            AgentAsyncQueue queue,
            AgentApprovalStore approvalStore,
            AgentPendingCommandStore pendingCommandStore,
            ToolExecutionService toolExecutionService,
            AgentCallbackDispatcher callbackDispatcher,
            AgentPlatformProperties properties
    ) {
        this.runtimeService = runtimeService;
        this.registry = registry;
        this.policyResolver = policyResolver;
        this.executionStore = executionStore;
        this.queue = queue;
        this.approvalStore = approvalStore;
        this.pendingCommandStore = pendingCommandStore;
        this.toolExecutionService = toolExecutionService;
        this.callbackDispatcher = callbackDispatcher == null ? AgentCallbackDispatcher.noop() : callbackDispatcher;
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
    }

    public AgentAsyncRunHandle submit(String agentId, String agentVersion, AgentInput input, AgentExecutionContext context, AgentRunOptions options, String callbackUrl) {
        return submit(agentId, agentVersion, input, context, options, callbackUrl, null);
    }

    public AgentAsyncRunHandle submit(String agentId, String agentVersion, AgentInput input, AgentExecutionContext context, AgentRunOptions options, String callbackUrl, String idempotencyKey) {
        RegisteredAgent registeredAgent = resolve(agentId, agentVersion);
        ResolvedAgentPolicy policy = policyResolver.resolve(agentId, options);
        AgentExecutionContext runContext = ensureExecutionId(context);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            Optional<AgentExecutionRecord> existing = executionStore.findByIdempotencyKey(runContext.tenantId(), normalizedIdempotencyKey);
            if (existing.isPresent()) {
                return handleFor(existing.get());
            }
        }
        executionStore.markQueued(runContext, registeredAgent.descriptor(), input, normalizedIdempotencyKey);

        AgentRunCommand command = new AgentRunCommand(agentId, agentVersion, input, runContext, options, callbackUrl);
        if (policy.requireHumanApproval()) {
            pendingCommandStore.save(command);
            AgentApprovalRequest approval = new AgentApprovalRequest(
                    UUID.randomUUID().toString(),
                    runContext.executionId(),
                    agentId,
                    agentVersion,
                    runContext.tenantId(),
                    runContext.actorId(),
                    AgentApprovalStatus.PENDING,
                    "Agent run requires human approval before execution",
                    Map.of("callbackUrl", callbackUrl == null ? "" : callbackUrl),
                    Instant.now(),
                    Instant.now().plus(properties.getAsync().getApprovalExpirationMinutes(), ChronoUnit.MINUTES),
                    null,
                    null
            );
            approvalStore.create(approval);
            executionStore.markWaitingForApproval(runContext.executionId(), approval.approvalId(), approval.reason());
            return new AgentAsyncRunHandle(runContext.executionId(), AgentRunStatus.WAITING_FOR_APPROVAL, approval.approvalId());
        }

        queue.enqueue(command);
        return new AgentAsyncRunHandle(runContext.executionId(), AgentRunStatus.QUEUED);
    }

    public Optional<AgentOutput> processNext() {
        return queue.poll().map(this::execute);
    }

    public AgentAsyncRunHandle resumeApproved(String approvalId, String decidedBy) {
        AgentApprovalRequest existing = approvalStore.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + approvalId));
        if (existing.status() == AgentApprovalStatus.APPROVED) {
            return handleFor(existing.executionId(), existing.approvalId());
        }
        if (existing.status() == AgentApprovalStatus.REJECTED || existing.status() == AgentApprovalStatus.EXPIRED) {
            throw new IllegalStateException("Approval request was already decided as " + existing.status() + ": " + approvalId);
        }
        if (isExpired(existing)) {
            AgentApprovalRequest expired = approvalStore.expire(approvalId);
            executionStore.markCancelled(expired.executionId(), "Approval expired");
            pendingCommandStore.delete(expired.executionId());
            return new AgentAsyncRunHandle(expired.executionId(), AgentRunStatus.CANCELLED, expired.approvalId());
        }
        AgentApprovalRequest approval = approvalStore.approve(approvalId, decidedBy);
        if (isToolApproval(approval)) {
            return resumeApprovedTool(approval, decidedBy);
        }
        AgentRunCommand original = pendingCommandStore.findByExecutionId(approval.executionId())
                .orElseThrow(() -> new IllegalStateException("Pending command not found for execution: " + approval.executionId()));
        AgentRunCommand command = original.withContext(new AgentExecutionContext(
                approval.executionId(),
                approval.tenantId(),
                original.context().actorId(),
                original.context().correlationId(),
                Instant.now(),
                mergeAttributes(original.context(), Map.of("resumedFromApprovalId", approvalId))
        ));
        queue.enqueue(command);
        executionStore.markQueued(command.context(), resolve(approval.agentId(), approval.agentVersion()).descriptor(), command.input());
        pendingCommandStore.delete(approval.executionId());
        return new AgentAsyncRunHandle(approval.executionId(), AgentRunStatus.QUEUED);
    }

    public AgentAsyncRunHandle reject(String approvalId, String decidedBy) {
        AgentApprovalRequest existing = approvalStore.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + approvalId));
        if (existing.status() == AgentApprovalStatus.REJECTED) {
            return handleFor(existing.executionId(), existing.approvalId());
        }
        if (existing.status() == AgentApprovalStatus.APPROVED || existing.status() == AgentApprovalStatus.EXPIRED) {
            throw new IllegalStateException("Approval request was already decided as " + existing.status() + ": " + approvalId);
        }
        AgentApprovalRequest approval = approvalStore.reject(approvalId, decidedBy);
        executionStore.markCancelled(approval.executionId(), "Approval rejected by " + decidedBy);
        if (!isToolApproval(approval)) {
            pendingCommandStore.delete(approval.executionId());
        }
        callbackDispatcher.dispatch(new AgentRunCallback(
                asString(approval.metadata().get("callbackUrl")),
                approval.executionId(),
                approval.agentId(),
                AgentRunStatus.CANCELLED,
                "Approval rejected",
                Instant.now()
        ));
        return new AgentAsyncRunHandle(approval.executionId(), AgentRunStatus.CANCELLED, approval.approvalId());
    }

    public AgentAsyncRunHandle expire(String approvalId) {
        AgentApprovalRequest approval = approvalStore.expire(approvalId);
        executionStore.markCancelled(approval.executionId(), "Approval expired");
        if (!isToolApproval(approval)) {
            pendingCommandStore.delete(approval.executionId());
        }
        callbackDispatcher.dispatch(new AgentRunCallback(
                asString(approval.metadata().get("callbackUrl")),
                approval.executionId(),
                approval.agentId(),
                AgentRunStatus.CANCELLED,
                "Approval expired",
                Instant.now()
        ));
        return new AgentAsyncRunHandle(approval.executionId(), AgentRunStatus.CANCELLED, approval.approvalId());
    }

    private AgentOutput execute(AgentRunCommand command) {
        try {
            AgentOutput output = runtimeService.run(command.agentId(), command.agentVersion(), command.input(), command.context(), command.options());
            dispatch(command, AgentRunStatus.SUCCEEDED, "Agent execution completed");
            return output;
        } catch (ToolApprovalRequiredException ex) {
            return null;
        } catch (RuntimeException ex) {
            dispatch(command, AgentRunStatus.FAILED, ex.getMessage());
            throw ex;
        }
    }

    private AgentAsyncRunHandle resumeApprovedTool(AgentApprovalRequest approval, String decidedBy) {
        if (toolExecutionService == null) {
            throw new IllegalStateException("Tool execution service is not available for approval: " + approval.approvalId());
        }
        String toolName = requireMetadataText(approval, "toolName");
        Map<String, Object> arguments = metadataMap(approval.metadata().get("toolArguments"));
        AgentExecutionContext context = new AgentExecutionContext(
                approval.executionId(),
                approval.tenantId(),
                approval.requestedBy(),
                asString(approval.metadata().get("correlationId")),
                Instant.now(),
                toolResumeAttributes(approval, toolName)
        );
        ResolvedAgentPolicy policy = toolResumePolicy(toolName, approval.metadata());
        try {
            ToolResult toolResult = toolExecutionService.executeApproved(new ToolExecutionRequest(toolName, context, policy, arguments), approval.approvalId());
            AgentOutput output = new AgentResult<>(
                    Map.of(
                            "executionId", approval.executionId(),
                            "approvalId", approval.approvalId(),
                            "approvedBy", decidedBy == null ? "" : decidedBy,
                            "tool", Map.of(
                                    "name", toolName,
                                    "success", toolResult.success(),
                                    "result", toolResult.values(),
                                    "errorMessage", toolResult.errorMessage() == null ? "" : toolResult.errorMessage()
                            )
                    ),
                    new AgentDecisionDraft(toolResult.success() ? "TOOL_APPROVED_EXECUTED" : "TOOL_APPROVED_FAILED", "Approved tool execution completed.", toolResult.success() ? 1.0d : 0.0d),
                    List.of(),
                    Map.of("approvalId", approval.approvalId(), "approvalType", AgentToolApprovalRequestSink.APPROVAL_TYPE_TOOL_EXECUTION, "toolName", toolName)
            );
            if (toolResult.success()) {
                executionStore.markCompleted(approval.executionId(), output);
                callbackDispatcher.dispatch(new AgentRunCallback(null, approval.executionId(), approval.agentId(), AgentRunStatus.SUCCEEDED, "Approved tool execution completed", Instant.now()));
                return new AgentAsyncRunHandle(approval.executionId(), AgentRunStatus.SUCCEEDED, approval.approvalId());
            }
            executionStore.markFailed(approval.executionId(), new IllegalStateException(toolResult.errorMessage()));
            return new AgentAsyncRunHandle(approval.executionId(), AgentRunStatus.FAILED, approval.approvalId());
        } catch (RuntimeException ex) {
            executionStore.markFailed(approval.executionId(), ex);
            throw ex;
        }
    }

    private AgentAsyncRunHandle handleFor(AgentExecutionRecord record) {
        String approvalId = approvalStore.findPendingByExecutionId(record.executionId())
                .map(AgentApprovalRequest::approvalId)
                .orElse(null);
        return new AgentAsyncRunHandle(record.executionId(), record.status(), approvalId);
    }

    private AgentAsyncRunHandle handleFor(String executionId, String fallbackApprovalId) {
        return executionStore.findByExecutionId(executionId)
                .map(record -> {
                    String approvalId = approvalStore.findPendingByExecutionId(record.executionId())
                            .map(AgentApprovalRequest::approvalId)
                            .orElse(fallbackApprovalId);
                    return new AgentAsyncRunHandle(record.executionId(), record.status(), approvalId);
                })
                .orElseGet(() -> new AgentAsyncRunHandle(executionId, AgentRunStatus.QUEUED, fallbackApprovalId));
    }

    private RegisteredAgent resolve(String agentId, String agentVersion) {
        return (agentVersion == null || agentVersion.isBlank()
                ? registry.findRegisteredAgent(agentId)
                : registry.findRegisteredAgent(agentId, agentVersion))
                .orElseThrow(() -> new IllegalArgumentException("No agent registered for async run: " + agentId));
    }

    private AgentExecutionContext ensureExecutionId(AgentExecutionContext context) {
        if (context != null && context.executionId() != null && !context.executionId().isBlank()) {
            return context;
        }
        return new AgentExecutionContext(UUID.randomUUID().toString(), null, null, null, Instant.now(), Map.of());
    }

    private void dispatch(AgentRunCommand command, AgentRunStatus status, String message) {
        if (command.callbackUrl() == null || command.callbackUrl().isBlank()) {
            return;
        }
        callbackDispatcher.dispatch(new AgentRunCallback(command.callbackUrl(), command.context().executionId(), command.agentId(), status, message, Instant.now()));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isExpired(AgentApprovalRequest approval) {
        return approval.expiresAt() != null && approval.expiresAt().isBefore(Instant.now());
    }

    private static boolean isToolApproval(AgentApprovalRequest approval) {
        return AgentToolApprovalRequestSink.APPROVAL_TYPE_TOOL_EXECUTION.equals(asString(approval.metadata().get("approvalType")));
    }

    private static String requireMetadataText(AgentApprovalRequest approval, String key) {
        String value = asString(approval.metadata().get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Approval metadata is missing " + key + ": " + approval.approvalId());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadataMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return Map.copyOf(result);
        }
        return Map.of();
    }

    private static Map<String, Object> toolResumeAttributes(AgentApprovalRequest approval, String toolName) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("agentId", approval.agentId());
        attributes.put("agentVersion", approval.agentVersion());
        attributes.put("resumedFromApprovalId", approval.approvalId());
        attributes.put("approvedToolName", toolName);
        Object permissions = approval.metadata().get("permissions");
        if (permissions instanceof Iterable<?> iterable) {
            java.util.LinkedHashSet<Object> set = new java.util.LinkedHashSet<>();
            iterable.forEach(set::add);
            attributes.put("permissions", Set.copyOf(set));
        } else if (permissions != null) {
            attributes.put("permissions", permissions);
        }
        return Map.copyOf(attributes);
    }

    private static ResolvedAgentPolicy toolResumePolicy(String toolName, Map<String, Object> metadata) {
        boolean logToolResults = Boolean.parseBoolean(asString(metadata.get("logToolResults")));
        boolean dryRun = Boolean.parseBoolean(asString(metadata.get("dryRun")));
        return new ResolvedAgentPolicy(
                true,
                dryRun,
                false,
                false,
                true,
                true,
                true,
                5,
                1,
                120,
                0,
                BigDecimal.ZERO,
                "openai",
                "default",
                "latest",
                AgentRunMode.RECOMMEND,
                1,
                0.2d,
                2048,
                true,
                false,
                false,
                logToolResults,
                Set.of(toolName),
                "SUMMARY",
                "NORMAL"
        );
    }

    private static Map<String, Object> mergeAttributes(AgentExecutionContext context, Map<String, Object> additions) {
        java.util.LinkedHashMap<String, Object> attributes = new java.util.LinkedHashMap<>(context.attributes());
        attributes.putAll(additions);
        return Map.copyOf(attributes);
    }
}
