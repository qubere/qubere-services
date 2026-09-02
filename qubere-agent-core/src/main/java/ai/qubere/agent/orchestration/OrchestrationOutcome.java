package ai.qubere.agent.orchestration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregate result of an orchestrated run.
 *
 * @param workflowId workflow all step executions were linked under
 * @param steps      outcomes in declaration order
 * @param state      final blackboard, including every successful step's result
 */
public record OrchestrationOutcome(
        String workflowId,
        List<StepOutcome> steps,
        OrchestrationState state
) {

    public OrchestrationOutcome {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /**
     * True only when no step failed. A run in which every step was skipped is still successful —
     * skipping is a branch decision, not an error.
     */
    public boolean successful() {
        return steps.stream().noneMatch(StepOutcome::failed);
    }

    public List<StepOutcome> failures() {
        return steps.stream().filter(StepOutcome::failed).toList();
    }

    /**
     * Result value of a named step, when it succeeded.
     */
    public Optional<Object> result(String stepName) {
        return state == null ? Optional.empty() : state.result(stepName);
    }

    /**
     * Flattened view of every successful step's result, keyed by step name.
     */
    public Map<String, Object> results() {
        return steps.stream()
                .filter(outcome -> outcome.succeeded() && outcome.value() != null)
                .collect(
                        java.util.LinkedHashMap::new,
                        (map, outcome) -> map.put(outcome.stepName(), outcome.value()),
                        java.util.LinkedHashMap::putAll
                );
    }
}
