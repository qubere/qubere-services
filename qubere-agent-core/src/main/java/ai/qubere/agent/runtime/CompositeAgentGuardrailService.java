package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;

import java.util.List;

/**
 * Chains multiple {@link AgentGuardrailService} instances so applications can compose the
 * framework's guardrail building blocks (baseline input checks, PII detection, custom
 * business-rule guardrails) instead of being limited to a single monolithic implementation.
 * <p>
 * Guardrails run in order; the first blocking decision short-circuits the chain. All guardrails
 * must allow the input for the composite decision to be {@code allowed}.
 */
public class CompositeAgentGuardrailService implements AgentGuardrailService {

    private final List<AgentGuardrailService> guardrails;

    public CompositeAgentGuardrailService(List<AgentGuardrailService> guardrails) {
        this.guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
    }

    @Override
    public GuardrailDecision evaluateBeforeRun(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
        for (AgentGuardrailService guardrail : guardrails) {
            GuardrailDecision decision = guardrail.evaluateBeforeRun(context, descriptor, input);
            if (!decision.allowed()) {
                return decision;
            }
        }
        return GuardrailDecision.allow();
    }
}
