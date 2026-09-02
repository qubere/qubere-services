package ai.qubere.agent.orchestration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared, mutable state for one orchestrated run — the "blackboard" that lets sibling steps see
 * each other's results.
 * <p>
 * Without this, an orchestrator can only pass data forward by manually threading one step's output
 * map into the next step's input, which does not work at all for parallel fan-out where several
 * steps must contribute to a later aggregation step.
 * <p>
 * Backed by concurrent maps because {@link AgentOrchestrator#parallel} writes step results from
 * multiple threads. Note that this makes individual put/get operations safe, not compound
 * read-modify-write sequences; steps should write their own keys rather than mutate shared ones.
 */
public final class OrchestrationState {

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Map<String, StepOutcome> outcomes = new ConcurrentHashMap<>();

    /**
     * Seeds the blackboard with the orchestration's initial input.
     */
    public static OrchestrationState withInput(Map<String, Object> input) {
        OrchestrationState state = new OrchestrationState();
        if (input != null) {
            input.forEach((key, value) -> {
                if (key != null && value != null) {
                    state.values.put(key, value);
                }
            });
        }
        return state;
    }

    public OrchestrationState put(String key, Object value) {
        if (key != null && value != null) {
            values.put(key, value);
        }
        return this;
    }

    public Optional<Object> get(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(values.get(key));
    }

    /**
     * Returns a value coerced to {@code type}, or empty when absent or of a different type.
     * Returning empty rather than throwing keeps input mappers tolerant of steps that were
     * skipped by a router or that failed under {@link FailurePolicy#CONTINUE}.
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        return get(key).filter(type::isInstance).map(type::cast);
    }

    /**
     * Returns the recorded outcome of a previously executed step, if any.
     */
    public Optional<StepOutcome> outcome(String stepName) {
        return stepName == null ? Optional.empty() : Optional.ofNullable(outcomes.get(stepName));
    }

    /**
     * Returns the successful result value produced by {@code stepName}, if it succeeded.
     */
    public Optional<Object> result(String stepName) {
        return outcome(stepName).filter(StepOutcome::succeeded).map(StepOutcome::value);
    }

    void record(StepOutcome outcome) {
        outcomes.put(outcome.stepName(), outcome);
        if (outcome.succeeded() && outcome.value() != null) {
            values.put(outcome.stepName(), outcome.value());
        }
    }

    /**
     * Snapshot of every value on the blackboard, including step results.
     */
    public Map<String, Object> values() {
        return new LinkedHashMap<>(values);
    }
}
