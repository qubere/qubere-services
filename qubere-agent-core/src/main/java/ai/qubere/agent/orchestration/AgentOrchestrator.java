package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.AgentWorkflowContext;
import ai.qubere.agent.tools.ToolApprovalRequiredException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Code-declared multi-agent orchestration patterns: sequential chains, parallel fan-out/fan-in,
 * routing, and supervisor loops.
 * <p>
 * <strong>How this relates to {@link AgentCallTool}.</strong> Both delegate to sub-agents, and both
 * apply {@link DelegationGuard} and the aggregate workflow budget. They differ in who chooses the
 * plan. {@code agent.call} exists for delegation chosen at runtime by an LLM, so it routes through
 * the tool layer to pick up the tool allow-list and approval policy — an LLM must not be able to
 * reach an agent an operator did not sanction. {@code AgentOrchestrator} executes a plan a
 * developer wrote in code and reviewed, so it invokes {@link AgentRuntimeService} directly. Every
 * sub-agent still gets its full per-agent governance (policy, guardrails, budgets, audit records,
 * workflow linkage); what is skipped is the tool-level allow-list, which would only be re-checking
 * a decision already made in source.
 * <p>
 * <strong>Threading.</strong> {@link #parallel} must not share the executor used by
 * {@link AgentRuntimeService} for its own timeout handling. An orchestration task occupies a thread
 * while blocking on its sub-agent's future; if both came from the same bounded pool, a wide enough
 * fan-out would fill the pool with waiters and deadlock. Supply a dedicated executor (the
 * auto-configuration wires {@code agentOrchestrationExecutor} for this reason).
 */
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    /** Blackboard key under which a supervisor loop records the sequence of steps it chose. */
    public static final String SUPERVISOR_TRACE = "supervisorTrace";

    private final AgentRuntimeService runtimeService;
    private final Executor executor;
    private final int maxDelegationDepth;

    public AgentOrchestrator(AgentRuntimeService runtimeService, Executor executor) {
        this(runtimeService, executor, AgentCallTool.DEFAULT_MAX_DELEGATION_DEPTH);
    }

    public AgentOrchestrator(AgentRuntimeService runtimeService, Executor executor, int maxDelegationDepth) {
        if (runtimeService == null) {
            throw new IllegalArgumentException("AgentRuntimeService is required");
        }
        this.runtimeService = runtimeService;
        this.executor = executor;
        this.maxDelegationDepth = Math.max(0, maxDelegationDepth);
    }

    /**
     * Runs {@code steps} in order, publishing each result to the blackboard so later steps can
     * consume it.
     */
    public OrchestrationOutcome sequential(
            AgentExecutionContext context,
            OrchestrationState state,
            FailurePolicy failurePolicy,
            List<OrchestrationStep> steps
    ) {
        OrchestrationState workingState = state == null ? OrchestrationState.withInput(Map.of()) : state;
        List<OrchestrationStep> plan = steps == null ? List.of() : steps;
        rejectDuplicateNames(plan);

        List<StepOutcome> outcomes = new ArrayList<>();
        boolean halted = false;
        for (OrchestrationStep step : plan) {
            if (halted) {
                outcomes.add(StepOutcome.notAttempted(step.name(), step.agentId()));
                continue;
            }
            StepOutcome outcome = executeStep(step, workingState, context);
            workingState.record(outcome);
            outcomes.add(outcome);
            if (outcome.failed() && failurePolicy != FailurePolicy.CONTINUE) {
                halted = true;
            }
        }
        return new OrchestrationOutcome(AgentWorkflowContext.workflowId(context), outcomes, workingState);
    }

    /**
     * Runs {@code steps} concurrently and joins the results.
     * <p>
     * {@link FailurePolicy#FAIL_FAST} cannot un-start work that is already running, so it does not
     * cancel in-flight siblings; it means the outcome is unsuccessful and dependent work is not
     * scheduled. Every step is still awaited so no sub-agent is left running past the join — a
     * detached sub-agent would keep consuming workflow budget invisibly.
     */
    public OrchestrationOutcome parallel(
            AgentExecutionContext context,
            OrchestrationState state,
            FailurePolicy failurePolicy,
            List<OrchestrationStep> steps
    ) {
        OrchestrationState workingState = state == null ? OrchestrationState.withInput(Map.of()) : state;
        List<OrchestrationStep> plan = steps == null ? List.of() : steps;
        rejectDuplicateNames(plan);
        if (plan.isEmpty()) {
            return new OrchestrationOutcome(AgentWorkflowContext.workflowId(context), List.of(), workingState);
        }
        if (executor == null) {
            log.debug("No orchestration executor configured; running {} parallel steps sequentially", plan.size());
            return sequential(context, workingState, failurePolicy, plan);
        }

        // Input mappers run on the calling thread, before fan-out. Running them inside the async
        // tasks would race: each mapper would observe a partially populated blackboard depending
        // on scheduling, making the orchestration non-deterministic.
        Map<String, Map<String, Object>> resolvedInputs = new LinkedHashMap<>();
        List<OrchestrationStep> runnable = new ArrayList<>();
        List<StepOutcome> outcomes = new ArrayList<>();
        for (OrchestrationStep step : plan) {
            if (!step.condition().test(workingState)) {
                outcomes.add(StepOutcome.skipped(step.name(), step.agentId()));
                continue;
            }
            resolvedInputs.put(step.name(), step.inputMapper().apply(workingState));
            runnable.add(step);
        }

        List<CompletableFuture<StepOutcome>> futures = runnable.stream()
                .map(step -> CompletableFuture.supplyAsync(
                        () -> invoke(step, resolvedInputs.get(step.name()), context),
                        executor))
                .toList();

        for (int i = 0; i < runnable.size(); i++) {
            OrchestrationStep step = runnable.get(i);
            StepOutcome outcome;
            try {
                outcome = futures.get(i).join();
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                // An approval request is a control-flow signal, not a step failure. Swallowing it
                // here would strand the workflow: the caller would see a "failed" step and never
                // learn approval was required.
                if (cause instanceof ToolApprovalRequiredException approvalRequired) {
                    throw approvalRequired;
                }
                outcome = StepOutcome.failed(step.name(), step.agentId(), cause, Duration.ZERO);
            }
            workingState.record(outcome);
            outcomes.add(outcome);
        }
        if (failurePolicy == FailurePolicy.FAIL_FAST && outcomes.stream().anyMatch(StepOutcome::failed)) {
            log.debug("Parallel orchestration had {} failed step(s) under FAIL_FAST", outcomes.stream().filter(StepOutcome::failed).count());
        }
        return new OrchestrationOutcome(AgentWorkflowContext.workflowId(context), outcomes, workingState);
    }

    /**
     * Selects one step from {@code routes} using {@code router} and executes it.
     * <p>
     * A router that returns an unknown or {@code null} key produces an empty outcome rather than an
     * exception, so a classifier returning something unexpected degrades to "no route taken"
     * instead of failing the whole workflow.
     *
     * @param router chooses a key of {@code routes} from the current state
     */
    public OrchestrationOutcome route(
            AgentExecutionContext context,
            OrchestrationState state,
            Function<OrchestrationState, String> router,
            Map<String, OrchestrationStep> routes
    ) {
        OrchestrationState workingState = state == null ? OrchestrationState.withInput(Map.of()) : state;
        if (router == null || routes == null || routes.isEmpty()) {
            return new OrchestrationOutcome(AgentWorkflowContext.workflowId(context), List.of(), workingState);
        }
        String selected = router.apply(workingState);
        OrchestrationStep step = selected == null ? null : routes.get(selected);
        if (step == null) {
            log.debug("Router selected no matching route (key={})", selected);
            return new OrchestrationOutcome(AgentWorkflowContext.workflowId(context), List.of(), workingState);
        }
        return sequential(context, workingState, FailurePolicy.FAIL_FAST, List.of(step));
    }

    /**
     * Runs a supervisor loop: a supervisor agent repeatedly inspects the blackboard and names the
     * next worker step to run, until it signals completion or {@code maxIterations} is reached.
     * <p>
     * {@code maxIterations} is mandatory and enforced because a supervisor is an LLM deciding its
     * own control flow, and the common failure is not a crash but an agent that keeps choosing
     * "one more step" forever. The aggregate workflow budget is a backstop, but a loop bound gives
     * a clear, cheap failure instead of one measured in spend.
     * <p>
     * The supervisor agent is expected to return a map result containing {@code next} (the name of
     * a worker step) and optionally {@code done=true}. Anything else terminates the loop.
     *
     * @param supervisorAgentId agent that chooses the next step
     * @param workers           candidate steps keyed by the name the supervisor will use
     * @param maxIterations     hard cap on supervisor turns; must be positive
     */
    public OrchestrationOutcome supervisor(
            AgentExecutionContext context,
            OrchestrationState state,
            String supervisorAgentId,
            Map<String, OrchestrationStep> workers,
            int maxIterations
    ) {
        if (supervisorAgentId == null || supervisorAgentId.isBlank()) {
            throw new IllegalArgumentException("Supervisor agent id is required");
        }
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("Supervisor loops require a positive maxIterations bound");
        }
        OrchestrationState workingState = state == null ? OrchestrationState.withInput(Map.of()) : state;
        Map<String, OrchestrationStep> candidates = workers == null ? Map.of() : workers;

        List<StepOutcome> outcomes = new ArrayList<>();
        List<String> trace = new ArrayList<>();

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            String supervisorStepName = "supervisor-" + (iteration + 1);
            OrchestrationStep supervisorStep = OrchestrationStep.of(
                    supervisorStepName,
                    supervisorAgentId,
                    OrchestrationState::values
            );
            StepOutcome supervisorOutcome = executeStep(supervisorStep, workingState, context);
            workingState.record(supervisorOutcome);
            outcomes.add(supervisorOutcome);
            if (!supervisorOutcome.succeeded()) {
                break;
            }

            Optional<String> next = nextStepName(supervisorOutcome.value(), candidates);
            if (next.isEmpty()) {
                break;
            }
            OrchestrationStep worker = candidates.get(next.get());
            trace.add(next.get());
            StepOutcome workerOutcome = executeStep(worker, workingState, context);
            workingState.record(workerOutcome);
            outcomes.add(workerOutcome);
            if (workerOutcome.failed()) {
                break;
            }
        }
        workingState.put(SUPERVISOR_TRACE, List.copyOf(trace));
        return new OrchestrationOutcome(AgentWorkflowContext.workflowId(context), outcomes, workingState);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> nextStepName(Object supervisorValue, Map<String, OrchestrationStep> candidates) {
        if (!(supervisorValue instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Map<String, Object> decision = (Map<String, Object>) map;
        if (Boolean.TRUE.equals(decision.get("done"))) {
            return Optional.empty();
        }
        Object next = decision.get("next");
        if (next == null) {
            return Optional.empty();
        }
        String name = next.toString().trim();
        // An unknown step name is treated as termination rather than an error: the supervisor is a
        // model, and hallucinating a step that does not exist should stop the loop cleanly.
        if (!candidates.containsKey(name)) {
            log.debug("Supervisor selected unknown step '{}'; ending loop", name);
            return Optional.empty();
        }
        return Optional.of(name);
    }

    private StepOutcome executeStep(OrchestrationStep step, OrchestrationState state, AgentExecutionContext context) {
        if (!step.condition().test(state)) {
            return StepOutcome.skipped(step.name(), step.agentId());
        }
        return invoke(step, step.inputMapper().apply(state), context);
    }

    private StepOutcome invoke(OrchestrationStep step, Map<String, Object> input, AgentExecutionContext context) {
        long startedAt = System.nanoTime();
        String childExecutionId = UUID.randomUUID().toString();
        try {
            DelegationGuard.check(context, step.agentId(), maxDelegationDepth);
            AgentExecutionContext childContext =
                    AgentWorkflowContext.childOf(context, childExecutionId, step.agentId());
            AgentOutput output = runtimeService.run(
                    step.agentId(),
                    step.agentVersion(),
                    new GenericAgentInput(sanitize(input)),
                    childContext,
                    null
            );
            Object value = output instanceof AgentResult<?> result ? result.value() : output;
            return StepOutcome.succeeded(step.name(), step.agentId(), value, childExecutionId, elapsed(startedAt));
        } catch (ToolApprovalRequiredException approvalRequired) {
            // Propagate: the workflow is pausing for a human, which is not a step failure and must
            // not be absorbed by FailurePolicy.CONTINUE.
            throw approvalRequired;
        } catch (RuntimeException ex) {
            log.warn("Orchestration step '{}' invoking agent '{}' failed: {}", step.name(), step.agentId(), ex.toString());
            return StepOutcome.failed(step.name(), step.agentId(), ex, elapsed(startedAt));
        }
    }

    /**
     * {@link GenericAgentInput} copies its map, which rejects null values. A user-supplied input
     * mapper legitimately may produce nulls for absent upstream results, so drop them rather than
     * failing the step with an opaque NullPointerException.
     */
    private Map<String, Object> sanitize(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key != null && value != null) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private Duration elapsed(long startedAtNanos) {        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    private void rejectDuplicateNames(List<OrchestrationStep> steps) {
        // Duplicate names would silently overwrite each other on the blackboard, so a later step
        // would read the wrong predecessor's result. Fail loudly instead.
        List<String> seen = new ArrayList<>();
        for (OrchestrationStep step : steps) {
            if (seen.contains(step.name())) {
                throw new IllegalArgumentException("Duplicate orchestration step name: " + step.name());
            }
            seen.add(step.name());
        }
    }
}
