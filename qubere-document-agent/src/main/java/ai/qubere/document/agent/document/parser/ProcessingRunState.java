package ai.qubere.document.agent.document.parser;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The Qubere document-processing state machine, ported from {@code parser/contracts.ts}. Provider
 * status strings are translated into these and never leak past a provider module.
 * <pre>
 * QUEUED -&gt; SUBMITTED -&gt; POLLING -&gt; SUCCEEDED
 *                              \-&gt; NEEDS_REVIEW
 *                              \-&gt; FAILED (-&gt; QUEUED when retryable)
 * </pre>
 */
public enum ProcessingRunState {
    QUEUED,
    SUBMITTED,
    POLLING,
    SUCCEEDED,
    NEEDS_REVIEW,
    FAILED;

    /** Terminal states never transition again; a new run is created instead. */
    public static final Set<ProcessingRunState> TERMINAL_STATES = EnumSet.of(SUCCEEDED, NEEDS_REVIEW);

    private static final Map<ProcessingRunState, Set<ProcessingRunState>> LEGAL_TRANSITIONS = Map.of(
            QUEUED, EnumSet.of(SUBMITTED, FAILED),
            SUBMITTED, EnumSet.of(POLLING, SUCCEEDED, NEEDS_REVIEW, FAILED),
            POLLING, EnumSet.of(POLLING, SUCCEEDED, NEEDS_REVIEW, FAILED),
            // A retryable failure goes back to QUEUED as the SAME run's next attempt.
            FAILED, EnumSet.of(QUEUED),
            // Never mutate a historical accepted run. Reprocessing creates a new run.
            SUCCEEDED, EnumSet.noneOf(ProcessingRunState.class),
            NEEDS_REVIEW, EnumSet.noneOf(ProcessingRunState.class)
    );

    /**
     * Whether transitioning from {@code this} to {@code target} is legal. Callers must guard every
     * state write with this check via a conditional database update (not a read-then-write), so a
     * stale or concurrent writer cannot force an illegal transition.
     */
    public boolean canTransitionTo(ProcessingRunState target) {
        return LEGAL_TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }
}
