package ai.qubere.agent.async;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentPolicyResolver;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.RegisteredAgent;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AgentAsyncRuntimeService {

    private final AgentRuntimeService runtimeService;
    private final AgentRegistry registry;
    private final AgentPolicyResolver policyResolver;
    private final AgentExecutionStore executionStore;
    private final AgentAsyncQueue queue;
    private final AgentApprovalStore approvalStore;
    private final AgentPendingCommandStore pendingCommandStore;
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
        this.runtimeService = runtimeService;
        this.registry = registry;
        this.policyResolver = policyResolver;
        this.executionStore = executionStore;
        this.queue = queue;
        this.approvalStore = approvalStore;
        this.pendingCommandStore = pendingCommandStore;
        this.callbackDispatcher = callbackDispatcher == null ? AgentCallbackDispatcher.noop() : callbackDispatcher;
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
    }

    public AgentAsyncRunHandle submit(String agentId, String agentVersion, AgentInput input, AgentExecutionContext context, AgentRunOptions options, String callbackUrl) {
        RegisteredAgent registeredAgent = resolve(agentId, agentVersion);
        ResolvedAgentPolicy policy = policyResolver.resolve(agentId, options);
        AgentExecutionContext runContext = ensureExecutionId(context);
        executionStore.markQueued(runContext, registeredAgent.descriptor(), input);

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
        if (isExpired(existing)) {
            AgentApprovalRequest expired = approvalStore.expire(approvalId);
            executionStore.markCancelled(expired.executionId(), "Approval expired");
            pendingCommandStore.delete(expired.executionId());
            return new AgentAsyncRunHandle(expired.executionId(), AgentRunStatus.CANCELLED, expired.approvalId());
        }
        AgentApprovalRequest approval = approvalStore.approve(approvalId, decidedBy);
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
        AgentApprovalRequest approval = approvalStore.reject(approvalId, decidedBy);
        executionStore.markCancelled(approval.executionId(), "Approval rejected by " + decidedBy);
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
        pendingCommandStore.delete(approval.executionId());
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
        } catch (RuntimeException ex) {
            dispatch(command, AgentRunStatus.FAILED, ex.getMessage());
            throw ex;
        }
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

    private static boolean isExpired(AgentApprovalRequest approval) {
        return approval.expiresAt() != null && approval.expiresAt().isBefore(Instant.now());
    }

    private static Map<String, Object> mergeAttributes(AgentExecutionContext context, Map<String, Object> additions) {
        java.util.LinkedHashMap<String, Object> attributes = new java.util.LinkedHashMap<>(context.attributes());
        attributes.putAll(additions);
        return Map.copyOf(attributes);
    }
}
