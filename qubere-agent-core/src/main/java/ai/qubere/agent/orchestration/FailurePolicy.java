package ai.qubere.agent.orchestration;

/**
 * What an orchestration does when a step fails.
 * <p>
 * There is no universally correct answer, so the framework refuses to guess: a fan-out of
 * independent enrichment agents usually wants partial results, while a sequential chain whose
 * later steps consume earlier output usually wants to stop. The choice is explicit at the call
 * site.
 */
public enum FailurePolicy {

    /**
     * Stop at the first failure. Remaining steps are reported as
     * {@link StepOutcome.Status#NOT_ATTEMPTED} rather than silently omitted, so the run's shape
     * stays visible in the outcome.
     */
    FAIL_FAST,

    /**
     * Run every step regardless of individual failures and report them all. The orchestration
     * outcome is still marked unsuccessful, so "continue" never quietly turns a partial failure
     * into an apparent success.
     */
    CONTINUE
}
