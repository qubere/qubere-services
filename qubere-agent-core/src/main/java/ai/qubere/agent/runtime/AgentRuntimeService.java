package ai.qubere.agent.runtime;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.tools.ToolApprovalRequiredException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeService {

    private final AgentRegistry registry;
    private final AgentPolicyResolver policyResolver;
    private final AgentAuthorizationService authorizationService;
    private final AgentGuardrailService guardrailService;
    private final AgentExecutionStore executionStore;
    private final AgentAuditService auditService;
    private final Collection<AgentGovernanceService> governanceServices;
    private final Collection<AgentPipelineListener> pipelineListeners;
    private final Executor invocationExecutor;
    private final ai.qubere.agent.checkpoint.AgentCheckpointStore checkpointStore;
    private final com.fasterxml.jackson.databind.ObjectMapper checkpointObjectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public AgentRuntimeService(
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentAuthorizationService authorizationService,
            AgentGuardrailService guardrailService,
            AgentExecutionStore executionStore,
            AgentAuditService auditService
    ) {
        this(registry, policyResolver, authorizationService, guardrailService, executionStore, auditService, List.of(), List.of());
    }

    public AgentRuntimeService(
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentAuthorizationService authorizationService,
            AgentGuardrailService guardrailService,
            AgentExecutionStore executionStore,
            AgentAuditService auditService,
            Collection<AgentGovernanceService> governanceServices,
            Collection<AgentPipelineListener> pipelineListeners
    ) {
        this(registry, policyResolver, authorizationService, guardrailService, executionStore, auditService, governanceServices, pipelineListeners, ForkJoinPool.commonPool());
    }

    @Autowired
    public AgentRuntimeService(
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentAuthorizationService authorizationService,
            AgentGuardrailService guardrailService,
            AgentExecutionStore executionStore,
            AgentAuditService auditService,
            Collection<AgentGovernanceService> governanceServices,
            Collection<AgentPipelineListener> pipelineListeners,
            @org.springframework.beans.factory.annotation.Qualifier("agentInvocationExecutor") ObjectProvider<Executor> invocationExecutorProvider,
            ObjectProvider<ai.qubere.agent.checkpoint.AgentCheckpointStore> checkpointStoreProvider
    ) {
        this(registry, policyResolver, authorizationService, guardrailService, executionStore, auditService, governanceServices, pipelineListeners,
                invocationExecutorProvider.getIfAvailable(ForkJoinPool::commonPool),
                checkpointStoreProvider.getIfAvailable(ai.qubere.agent.checkpoint.AgentCheckpointStore::noop));
    }

    public AgentRuntimeService(
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentAuthorizationService authorizationService,
            AgentGuardrailService guardrailService,
            AgentExecutionStore executionStore,
            AgentAuditService auditService,
            Collection<AgentPipelineListener> pipelineListeners
    ) {
        this(registry, policyResolver, authorizationService, guardrailService, executionStore, auditService, List.of(), pipelineListeners);
    }

    AgentRuntimeService(
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentAuthorizationService authorizationService,
            AgentGuardrailService guardrailService,
            AgentExecutionStore executionStore,
            AgentAuditService auditService,
            Collection<AgentGovernanceService> governanceServices,
            Collection<AgentPipelineListener> pipelineListeners,
            Executor invocationExecutor
    ) {
        this(registry, policyResolver, authorizationService, guardrailService, executionStore, auditService,
                governanceServices, pipelineListeners, invocationExecutor, null);
    }

    AgentRuntimeService(
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentAuthorizationService authorizationService,
            AgentGuardrailService guardrailService,
            AgentExecutionStore executionStore,
            AgentAuditService auditService,
            Collection<AgentGovernanceService> governanceServices,
            Collection<AgentPipelineListener> pipelineListeners,
            Executor invocationExecutor,
            ai.qubere.agent.checkpoint.AgentCheckpointStore checkpointStore
    ) {
        this.registry = registry;
        this.policyResolver = policyResolver;
        this.authorizationService = authorizationService;
        this.guardrailService = guardrailService;
        this.executionStore = executionStore;
        this.auditService = auditService;
        this.governanceServices = governanceServices == null ? List.of() : List.copyOf(governanceServices);
        this.pipelineListeners = pipelineListeners == null ? List.of() : List.copyOf(pipelineListeners);
        this.invocationExecutor = invocationExecutor == null ? ForkJoinPool.commonPool() : invocationExecutor;
        this.checkpointStore = checkpointStore == null
                ? ai.qubere.agent.checkpoint.AgentCheckpointStore.noop()
                : checkpointStore;
    }

    public AgentOutput run(String agentId, AgentInput input, AgentExecutionContext context, AgentRunOptions options) {
        return run(agentId, null, input, context, options);
    }

    public AgentOutput run(String agentId, String agentVersion, AgentInput input, AgentExecutionContext context, AgentRunOptions options) {
        RegisteredAgent registeredAgent = findRegisteredAgent(agentId, agentVersion)
                .orElseThrow(() -> new AgentExecutionException(
                        AgentErrorCode.AGENT_NOT_FOUND,
                        agentVersion == null || agentVersion.isBlank()
                                ? "No agent is registered with id " + agentId
                                : "No agent is registered with id %s and version %s".formatted(agentId, agentVersion)
                ));
        Agent<?, ?> agent = registeredAgent.agent();

        publish(AgentPipelineStep.AGENT_RESOLUTION, context, agent.descriptor(), "Agent resolved");
        ResolvedAgentPolicy resolvedPolicy = policyResolver.resolve(agentId, options, agent.descriptor().riskLevel());
        if (!resolvedPolicy.enabled()) {
            throw new AgentExecutionException(AgentErrorCode.AGENT_DISABLED, "Agent is disabled: " + agentId);
        }
        AgentExecutionContext executionContext = withResolvedPolicy(context, resolvedPolicy, agent.descriptor());
        publish(AgentPipelineStep.POLICY_RESOLUTION, context, agent.descriptor(), "Policy resolved");

        // Aggregate workflow ceiling is consumed per agent invocation, so an orchestrator cannot
        // multiply total work by spawning sub-agents that each stay under their own per-run limit.
        AgentWorkflowBudget workflowBudget = AgentWorkflowContext.workflowBudget(executionContext);
        if (workflowBudget != null) {
            workflowBudget.consumeAgentInvocation(agentId);
        }

        if (!authorizationService.canRun(executionContext, agent.descriptor())) {
            publish(AgentPipelineStep.AUTHORIZATION, executionContext, agent.descriptor(), "Authorization denied");
            throw new AgentExecutionException(AgentErrorCode.AUTHORIZATION_DENIED, "Caller is not allowed to run agent " + agentId);
        }
        publish(AgentPipelineStep.AUTHORIZATION, executionContext, agent.descriptor(), "Authorization granted");

        governanceServices.forEach(governance -> governance.beforeRun(executionContext, agent.descriptor(), resolvedPolicy));

        GuardrailDecision guardrailDecision = guardrailService.evaluateBeforeRun(executionContext, agent.descriptor(), input);
        if (!guardrailDecision.allowed()) {
            publish(AgentPipelineStep.INPUT_GUARDRAILS, executionContext, agent.descriptor(), guardrailDecision.reason());
            throw new AgentExecutionException(AgentErrorCode.GUARDRAIL_BLOCKED, guardrailDecision.reason());
        }
        publish(AgentPipelineStep.INPUT_GUARDRAILS, executionContext, agent.descriptor(), "Input guardrails passed");

        try {
            executionStore.markStarted(executionContext, agent.descriptor(), input);
            auditService.recordStatus(executionContext, agent.descriptor(), AgentRunStatus.RUNNING, "Agent execution started");
            publish(AgentPipelineStep.EXECUTION_STARTED, executionContext, agent.descriptor(), "Execution started");
            AgentOutput output = invokeWithRetry(agent, input, executionContext, resolvedPolicy);
            executionStore.markCompleted(executionContext.executionId(), output);
            governanceServices.forEach(governance -> governance.afterRun(executionContext, agent.descriptor(), output));
            auditService.recordStatus(executionContext, agent.descriptor(), AgentRunStatus.SUCCEEDED, "Agent execution completed");
            publish(AgentPipelineStep.EXECUTION_COMPLETED, executionContext, agent.descriptor(), "Execution completed");
            // The run reached a terminal state, so its resume checkpoints are no longer needed.
            checkpointStore.deleteByExecutionId(executionContext.executionId());
            return output;
        } catch (ToolApprovalRequiredException ex) {
            // Deliberately do NOT clear checkpoints here: the execution is paused, not finished,
            // and the recorded steps are exactly what lets it resume without redoing work.
            auditService.recordStatus(executionContext, agent.descriptor(), AgentRunStatus.WAITING_FOR_APPROVAL, ex.getMessage());
            publish(AgentPipelineStep.EXECUTION_FAILED, executionContext, agent.descriptor(), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            executionStore.markFailed(executionContext.executionId(), ex);
            auditService.recordStatus(executionContext, agent.descriptor(), AgentRunStatus.FAILED, ex.getMessage());
            publish(AgentPipelineStep.EXECUTION_FAILED, executionContext, agent.descriptor(), ex.getMessage());
            checkpointStore.deleteByExecutionId(executionContext.executionId());
            throw ex;
        }
    }

    private AgentOutput invokeWithRetry(Agent<?, ?> agent, AgentInput input, AgentExecutionContext context, ResolvedAgentPolicy policy) {
        int maxAttempts = Math.max(1, policy.maxRetries() + 1);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                AgentRunBudget budget = budget(context);
                budget.consumeStep("agent invocation attempt " + attempt);
                publish(AgentPipelineStep.AGENT_INVOCATION, context, agent.descriptor(), "Agent invocation started attempt " + attempt);
                return invokeWithTimeout(agent, input, context, policy);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt >= maxAttempts || !isRetryable(ex)) {
                    throw ex;
                }
                publish(AgentPipelineStep.AGENT_INVOCATION, context, agent.descriptor(), "Retrying agent invocation after " + ex.getMessage());
            }
        }
        throw lastFailure;
    }

    private AgentOutput invokeWithTimeout(Agent<?, ?> agent, AgentInput input, AgentExecutionContext context, ResolvedAgentPolicy policy) {
        if (policy.timeoutSeconds() <= 0) {
            return invoke(agent, input, context);
        }
        try {
            CompletableFuture<AgentOutput> future = CompletableFuture.supplyAsync(() -> invoke(agent, input, context), invocationExecutor);
            return future.get(policy.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            throw new AgentExecutionException(
                    AgentErrorCode.TIMEOUT,
                    "Agent execution exceeded timeoutSeconds=" + policy.timeoutSeconds(),
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentExecutionException(AgentErrorCode.TIMEOUT, "Agent execution was interrupted", ex);
        } catch (CompletionException ex) {
            throw unwrap(ex.getCause());
        } catch (java.util.concurrent.ExecutionException ex) {
            throw unwrap(ex.getCause());
        }
    }

    private RuntimeException unwrap(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new AgentExecutionException(AgentErrorCode.EXECUTION_FAILED, "Agent execution failed", throwable);
    }

    private boolean isRetryable(RuntimeException ex) {
        if (ex instanceof AgentExecutionException agentException) {
            return agentException.errorCode() == AgentErrorCode.AI_PROVIDER_FAILURE
                    || agentException.errorCode() == AgentErrorCode.TIMEOUT
                    || agentException.errorCode() == AgentErrorCode.TOOL_FAILED;
        }
        return true;
    }

    private Optional<RegisteredAgent> findRegisteredAgent(String agentId, String agentVersion) {
        if (agentVersion == null || agentVersion.isBlank()) {
            return registry.findRegisteredAgent(agentId);
        }
        return registry.findRegisteredAgent(agentId, agentVersion);
    }

    @SuppressWarnings("unchecked")
    private <I extends AgentInput, O extends AgentOutput> O invoke(Agent<?, ?> agent, AgentInput input, AgentExecutionContext context) {
        return ((Agent<I, O>) agent).run((I) input, context);
    }

    private void publish(AgentPipelineStep step, AgentExecutionContext context, AgentDescriptor descriptor, String message) {
        if (pipelineListeners.isEmpty()) {
            return;
        }
        AgentPipelineEvent event = AgentPipelineEvent.of(step, context, descriptor, message);
        pipelineListeners.forEach(listener -> listener.onEvent(event));
    }

    private AgentExecutionContext withResolvedPolicy(AgentExecutionContext context, ResolvedAgentPolicy policy, AgentDescriptor descriptor) {
        Map<String, Object> attributes = new HashMap<>(context.attributes());
        attributes.put("resolvedPolicy", policy);
        attributes.put("agentRunBudget", new AgentRunBudget(policy));
        attributes.put("agentId", descriptor.id());
        attributes.put("agentVersion", descriptor.version());
        // Published so a multi-step agent can memoize completed steps and resume across an
        // approval interruption without repeating their side effects.
        attributes.put(
                ai.qubere.agent.checkpoint.AgentCheckpointScope.CONTEXT_ATTRIBUTE,
                new ai.qubere.agent.checkpoint.AgentCheckpointScope(context.executionId(), checkpointStore, checkpointObjectMapper)
        );
        return new AgentExecutionContext(
                context.executionId(),
                context.tenantId(),
                context.actorId(),
                context.correlationId(),
                context.requestedAt(),
                attributes
        );
    }

    private AgentRunBudget budget(AgentExecutionContext context) {
        Object budget = context.attributes().get("agentRunBudget");
        if (budget instanceof AgentRunBudget agentRunBudget) {
            return agentRunBudget;
        }
        return new AgentRunBudget(ResolvedAgentPolicy.defaults());
    }
}