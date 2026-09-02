package ai.qubere.agent.checkpoint;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Makes a multi-step agent resumable across an approval interruption.
 * <p>
 * Java cannot serialize and restore a paused call stack, so full "continue exactly where you left
 * off" continuation is implemented as durable step memoization: the agent expresses its work as
 * named {@link #step} calls, each completed step's result is persisted, and on resume the agent is
 * re-invoked from the top with already-completed steps returning their stored results instead of
 * running again. Execution therefore continues from the first incomplete step, and side effects
 * from earlier steps are not repeated.
 * <p>
 * Usage inside an agent:
 * <pre>{@code
 * AgentCheckpointScope checkpoints = AgentCheckpointScope.from(context);
 *
 * InvoiceData invoice = checkpoints.step("load-invoice", InvoiceData.class,
 *         () -> invoiceService.load(input.invoiceId()));
 *
 * // If this tool requires approval it throws ToolApprovalRequiredException and the run pauses.
 * // After approval the agent re-runs: "load-invoice" replays from the checkpoint rather than
 * // hitting the invoice service again, and execution proceeds to the next step.
 * ToolResult posted = checkpoints.step("post-ledger", ToolResult.class,
 *         () -> toolExecutionService.execute(postLedgerRequest));
 * }</pre>
 *
 * <b>Contract the agent must honor.</b> Step names must be unique and produced in a deterministic
 * order across re-invocations; a step whose name or position changes between runs cannot be
 * matched to its checkpoint. Step results must be JSON-serializable. Steps should be the unit at
 * which side effects occur, so replaying a checkpoint genuinely avoids repeating them.
 */
public class AgentCheckpointScope {

    /** Context attribute under which the runtime publishes the scope for the current execution. */
    public static final String CONTEXT_ATTRIBUTE = "agentCheckpointScope";

    private final String executionId;
    private final AgentCheckpointStore store;
    private final ObjectMapper objectMapper;
    private final AtomicInteger stepCounter = new AtomicInteger();
    private final Set<String> seenStepNames = new LinkedHashSet<>();

    public AgentCheckpointScope(String executionId, AgentCheckpointStore store, ObjectMapper objectMapper) {
        this.executionId = executionId;
        this.store = store == null ? AgentCheckpointStore.noop() : store;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * Reads the checkpoint scope published on the execution context, or {@code null} when
     * checkpointing is not enabled for this run.
     */
    public static AgentCheckpointScope from(ai.qubere.agent.api.AgentExecutionContext context) {
        if (context == null) {
            return null;
        }
        return context.attributes().get(CONTEXT_ATTRIBUTE) instanceof AgentCheckpointScope scope ? scope : null;
    }

    /**
     * Executes {@code work} unless this step already completed in an earlier attempt of the same
     * execution, in which case the persisted result is returned and {@code work} is not run.
     *
     * @param stepName   unique, stable name for this step within the execution
     * @param resultType type used to deserialize a replayed checkpoint
     * @param work       the step's actual work, executed only on first completion
     */
    public <T> T step(String stepName, Class<T> resultType, Supplier<T> work) {
        return step(stepName, work, json -> readValue(json, resultType, stepName));
    }

    /**
     * Variant for generic result types (for example {@code Map<String, Object>} or
     * {@code List<Finding>}) that a {@link Class} token cannot express.
     */
    public <T> T step(String stepName, TypeReference<T> resultType, Supplier<T> work) {
        return step(stepName, work, json -> readValue(json, resultType, stepName));
    }

    private <T> T step(String stepName, Supplier<T> work, java.util.function.Function<String, T> replay) {
        if (stepName == null || stepName.isBlank()) {
            throw new IllegalArgumentException("Checkpoint step name is required");
        }
        if (!seenStepNames.add(stepName)) {
            // A duplicate name within one run means two different pieces of work share a
            // checkpoint key, which would silently replay the wrong result after a resume.
            throw new AgentExecutionException(
                    AgentErrorCode.VALIDATION_FAILED,
                    "Duplicate checkpoint step name within execution %s: %s".formatted(executionId, stepName)
            );
        }

        Optional<AgentCheckpoint> existing = store.find(executionId, stepName);
        if (existing.isPresent()) {
            stepCounter.incrementAndGet();
            return replay.apply(existing.get().resultJson());
        }

        T result = work.get();
        store.save(new AgentCheckpoint(
                executionId,
                stepName,
                stepCounter.incrementAndGet(),
                writeValue(result, stepName),
                java.time.Instant.now()
        ));
        return result;
    }

    /**
     * Whether a step already completed, useful for agents that want to branch on prior progress
     * rather than memoize a value.
     */
    public boolean isStepCompleted(String stepName) {
        return store.find(executionId, stepName).isPresent();
    }

    /**
     * Number of checkpoints recorded for this execution so far.
     */
    public int completedStepCount() {
        return store.findByExecutionId(executionId).size();
    }

    public String executionId() {
        return executionId;
    }

    private <T> T readValue(String json, Class<T> resultType, String stepName) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, resultType);
        } catch (Exception ex) {
            throw new AgentExecutionException(
                    AgentErrorCode.EXECUTION_FAILED,
                    "Unable to replay checkpoint for step '%s' of execution %s".formatted(stepName, executionId),
                    ex
            );
        }
    }

    private <T> T readValue(String json, TypeReference<T> resultType, String stepName) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, resultType);
        } catch (Exception ex) {
            throw new AgentExecutionException(
                    AgentErrorCode.EXECUTION_FAILED,
                    "Unable to replay checkpoint for step '%s' of execution %s".formatted(stepName, executionId),
                    ex
            );
        }
    }

    private String writeValue(Object value, String stepName) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AgentExecutionException(
                    AgentErrorCode.EXECUTION_FAILED,
                    "Checkpoint step '%s' returned a result that cannot be serialized; step results must be JSON-serializable"
                            .formatted(stepName),
                    ex
            );
        }
    }
}
