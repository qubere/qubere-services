package ai.qubere.agent.runtime;

import java.util.function.Predicate;

/**
 * Composable output guardrail utility for validating structured AI output before it is trusted,
 * persisted, or shown to a user (framework standard section 14: "Output Validation Standards").
 * <p>
 * The framework does not force a specific validation library. Callers supply a
 * {@link Predicate} that expresses the business-rule/schema conformance check for their output
 * type, keeping this guardrail dependency-free. Agents typically call this immediately after
 * {@code AgentAiClient.generate(...)} returns a structured object and before constructing the
 * final {@code AgentResult}.
 * <pre>{@code
 * AnalysisOutput output = aiClient.generate(prompt, AnalysisOutput.class, metadata);
 * GuardrailDecision decision = OutputSchemaConformanceGuardrail.validate(
 *     output,
 *     o -> o.summary() != null && !o.summary().isBlank() && o.confidence() >= 0.0 && o.confidence() <= 1.0,
 *     "Model output failed schema/business-rule conformance checks"
 * );
 * if (!decision.allowed()) {
 *     throw new AgentExecutionException(AgentErrorCode.VALIDATION_FAILED, decision.reason());
 * }
 * }</pre>
 */
public final class OutputSchemaConformanceGuardrail {

    private OutputSchemaConformanceGuardrail() {
    }

    /**
     * Validates that {@code output} is non-null and satisfies {@code conformancePredicate}.
     *
     * @param output               the structured model output to validate
     * @param conformancePredicate business-rule/schema conformance check; a {@code null}
     *                             predicate skips the conformance check and only rejects null output
     * @param failureReason        reason returned in the blocking decision when the predicate fails
     */
    public static GuardrailDecision validate(Object output, Predicate<Object> conformancePredicate, String failureReason) {
        if (output == null) {
            return GuardrailDecision.block("Agent output must not be null");
        }
        if (conformancePredicate != null && !conformancePredicate.test(output)) {
            return GuardrailDecision.block(failureReason == null ? "Agent output failed conformance validation" : failureReason);
        }
        return GuardrailDecision.allow();
    }
}
