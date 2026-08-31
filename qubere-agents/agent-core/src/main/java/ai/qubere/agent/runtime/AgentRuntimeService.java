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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Autowired
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
        this.registry = registry;
        this.policyResolver = policyResolver;
        this.authorizationService = authorizationService;
        this.guardrailService = guardrailService;
        this.executionStore = executionStore;
        this.auditService = auditService;
        this.governanceServices = governanceServices == null ? List.of() : List.copyOf(governanceServices);
        this.pipelineListeners = pipelineListeners == null ? List.of() : List.copyOf(pipelineListeners);
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
        ResolvedAgentPolicy resolvedPolicy = policyResolver.resolve(agentId, options);
        if (!resolvedPolicy.enabled()) {
            throw new AgentExecutionException(AgentErrorCode.AGENT_DISABLED, "Agent is disabled: " + agentId);
        }
        AgentExecutionContext executionContext = withResolvedPolicy(context, resolvedPolicy, agent.descriptor());
        publish(AgentPipelineStep.POLICY_RESOLUTION, context, agent.descriptor(), "Policy resolved");

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
            publish(AgentPipelineStep.AGENT_INVOCATION, executionContext, agent.descriptor(), "Agent invocation started");
            AgentOutput output = invoke(agent, input, executionContext);
            executionStore.markCompleted(executionContext.executionId(), output);
            governanceServices.forEach(governance -> governance.afterRun(executionContext, agent.descriptor(), output));
            auditService.recordStatus(executionContext, agent.descriptor(), AgentRunStatus.SUCCEEDED, "Agent execution completed");
            publish(AgentPipelineStep.EXECUTION_COMPLETED, executionContext, agent.descriptor(), "Execution completed");
            return output;
        } catch (RuntimeException ex) {
            executionStore.markFailed(executionContext.executionId(), ex);
            auditService.recordStatus(executionContext, agent.descriptor(), AgentRunStatus.FAILED, ex.getMessage());
            publish(AgentPipelineStep.EXECUTION_FAILED, executionContext, agent.descriptor(), ex.getMessage());
            throw ex;
        }
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
        attributes.put("agentId", descriptor.id());
        attributes.put("agentVersion", descriptor.version());
        return new AgentExecutionContext(
                context.executionId(),
                context.tenantId(),
                context.actorId(),
                context.correlationId(),
                context.requestedAt(),
                attributes
        );
    }
}
