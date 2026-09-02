package ai.qubere.agent.orchestration;

import java.time.Duration;

/**
 * Result of a single {@link OrchestrationStep}.
 *
 * @param stepName    name of the step this outcome belongs to
 * @param agentId     agent that was (or would have been) invoked
 * @param status      terminal status of the step
 * @param value       unwrapped agent result value on success, otherwise {@code null}
 * @param executionId execution id of the sub-agent run, or {@code null} when skipped
 * @param failure     the exception that ended the step, or {@code null}
 * @param duration    wall-clock time spent on the step
 */
public record StepOutcome(
        String stepName,
        String agentId,
        Status status,
        Object value,
        String executionId,
        Throwable failure,
        Duration duration
) {

    public enum Status {
        SUCCEEDED,
        FAILED,
        /** The step's condition evaluated false, so it never ran. Not an error. */
        SKIPPED,
        /**
         * An earlier step failed under {@link FailurePolicy#FAIL_FAST}, so this step was never
         * attempted. Distinguished from {@link #SKIPPED} so an operator can tell a deliberate
         * branch from collateral damage.
         */
        NOT_ATTEMPTED
    }

    public boolean succeeded() {
        return status == Status.SUCCEEDED;
    }

    public boolean failed() {
        return status == Status.FAILED;
    }

    public static StepOutcome succeeded(String stepName, String agentId, Object value, String executionId, Duration duration) {
        return new StepOutcome(stepName, agentId, Status.SUCCEEDED, value, executionId, null, duration);
    }

    public static StepOutcome failed(String stepName, String agentId, Throwable failure, Duration duration) {
        return new StepOutcome(stepName, agentId, Status.FAILED, null, null, failure, duration);
    }

    public static StepOutcome skipped(String stepName, String agentId) {
        return new StepOutcome(stepName, agentId, Status.SKIPPED, null, null, null, Duration.ZERO);
    }

    public static StepOutcome notAttempted(String stepName, String agentId) {
        return new StepOutcome(stepName, agentId, Status.NOT_ATTEMPTED, null, null, null, Duration.ZERO);
    }
}
