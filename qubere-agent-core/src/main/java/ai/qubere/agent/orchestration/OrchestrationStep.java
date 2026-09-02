package ai.qubere.agent.orchestration;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One node in a code-declared orchestration: which agent to invoke, what to feed it, and
 * optionally whether to run it at all.
 *
 * @param name        unique step name within the orchestration; also the blackboard key under
 *                    which this step's result is published
 * @param agentId     id of the agent to invoke
 * @param agentVersion specific version, or {@code null} for the registry default
 * @param inputMapper builds the agent input from the current {@link OrchestrationState}, letting a
 *                    step consume results produced by earlier steps
 * @param condition   evaluated before execution; when it returns {@code false} the step is skipped
 *                    rather than failed, which is what makes conditional branches expressible
 *                    without a separate graph type
 */
public record OrchestrationStep(
        String name,
        String agentId,
        String agentVersion,
        Function<OrchestrationState, Map<String, Object>> inputMapper,
        Predicate<OrchestrationState> condition
) {

    public OrchestrationStep {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Orchestration step name is required");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Orchestration step '" + name + "' requires an agentId");
        }
        inputMapper = inputMapper == null ? state -> Map.of() : inputMapper;
        condition = condition == null ? state -> true : condition;
    }

    /**
     * Step that passes the orchestration's current blackboard values straight through as input.
     */
    public static OrchestrationStep of(String name, String agentId) {
        return new OrchestrationStep(name, agentId, null, OrchestrationState::values, null);
    }

    public static OrchestrationStep of(
            String name,
            String agentId,
            Function<OrchestrationState, Map<String, Object>> inputMapper
    ) {
        return new OrchestrationStep(name, agentId, null, inputMapper, null);
    }

    /**
     * Returns a copy of this step that only runs when {@code condition} holds.
     */
    public OrchestrationStep when(Predicate<OrchestrationState> condition) {
        return new OrchestrationStep(name, agentId, agentVersion, inputMapper, condition);
    }

    /**
     * Returns a copy of this step pinned to a specific agent version.
     */
    public OrchestrationStep withVersion(String version) {
        return new OrchestrationStep(name, agentId, version, inputMapper, condition);
    }
}
