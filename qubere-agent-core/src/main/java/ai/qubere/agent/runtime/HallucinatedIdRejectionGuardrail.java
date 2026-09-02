package ai.qubere.agent.runtime;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Composable output guardrail that rejects model output referencing identifiers (record IDs,
 * customer IDs, order numbers, etc.) that do not exist in a caller-supplied set of known/valid
 * identifiers. This directly implements framework standard 3.4's "reject hallucinated
 * identifiers" output guardrail and standard 16's "never allow model to invent tenant/account/user
 * IDs" requirement.
 * <pre>{@code
 * Set<String> knownInvoiceIds = invoiceLookupService.findExistingIds(referencedIds);
 * GuardrailDecision decision = HallucinatedIdRejectionGuardrail.validate(referencedIds, knownInvoiceIds);
 * if (!decision.allowed()) {
 *     throw new AgentExecutionException(AgentErrorCode.VALIDATION_FAILED, decision.reason());
 * }
 * }</pre>
 */
public final class HallucinatedIdRejectionGuardrail {

    private HallucinatedIdRejectionGuardrail() {
    }

    /**
     * Validates that every ID in {@code referencedIds} is present in {@code knownIds}.
     *
     * @param referencedIds identifiers the model output referenced
     * @param knownIds      identifiers independently verified to exist (e.g. via a database lookup)
     */
    public static GuardrailDecision validate(Collection<String> referencedIds, Set<String> knownIds) {
        if (referencedIds == null || referencedIds.isEmpty()) {
            return GuardrailDecision.allow();
        }
        Set<String> safeKnownIds = knownIds == null ? Set.of() : knownIds;
        Set<String> unknown = new LinkedHashSet<>();
        for (String id : referencedIds) {
            if (id != null && !safeKnownIds.contains(id)) {
                unknown.add(id);
            }
        }
        if (!unknown.isEmpty()) {
            return GuardrailDecision.block("Agent output referenced unknown or hallucinated identifiers: " + unknown);
        }
        return GuardrailDecision.allow();
    }
}
