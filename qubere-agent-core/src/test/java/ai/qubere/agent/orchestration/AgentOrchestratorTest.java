package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.AgentWorkflowBudget;
import ai.qubere.agent.runtime.AgentWorkflowContext;
import ai.qubere.agent.runtime.DefaultAgentPolicyResolver;
import ai.qubere.agent.runtime.GuardrailDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentOrchestratorTest {

    // --- sequential -----------------------------------------------------------------------

    @Test
    void sequentialStepsRunInOrderAndCanConsumeEarlierResults() {
        RecordingExecutionStore store = new RecordingExecutionStore();
        AgentOrchestrator orchestrator = orchestrator(store, null);

        OrchestrationOutcome outcome = orchestrator.sequential(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of("message", "start")),
                FailurePolicy.FAIL_FAST,
                List.of(
                        OrchestrationStep.of("first", "echo", state -> Map.of("message", "a")),
                        // Consumes the first step's result off the blackboard.
                        OrchestrationStep.of("second", "echo", state ->
                                Map.of("message", "b-after-" + firstMessage(state)))
                )
        );

        assertThat(outcome.successful()).isTrue();
        assertThat(outcome.steps()).extracting(StepOutcome::stepName).containsExactly("first", "second");
        assertThat(outcome.results().get("second")).isEqualTo(Map.of("message", "b-after-a"));
    }

    @Test
    void failFastStopsAtTheFirstFailureAndMarksLaterStepsNotAttempted() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        OrchestrationOutcome outcome = orchestrator.sequential(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                FailurePolicy.FAIL_FAST,
                List.of(
                        OrchestrationStep.of("ok", "echo", state -> Map.of("message", "fine")),
                        OrchestrationStep.of("boom", "boom", state -> Map.of()),
                        OrchestrationStep.of("never", "echo", state -> Map.of("message", "unreachable"))
                )
        );

        assertThat(outcome.successful()).isFalse();
        assertThat(outcome.steps()).extracting(StepOutcome::status).containsExactly(
                StepOutcome.Status.SUCCEEDED,
                StepOutcome.Status.FAILED,
                // Reported explicitly rather than omitted, so the plan's shape stays visible.
                StepOutcome.Status.NOT_ATTEMPTED
        );
    }

    @Test
    void continuePolicyRunsEveryStepButStillReportsFailure() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        OrchestrationOutcome outcome = orchestrator.sequential(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                FailurePolicy.CONTINUE,
                List.of(
                        OrchestrationStep.of("boom", "boom", state -> Map.of()),
                        OrchestrationStep.of("after", "echo", state -> Map.of("message", "still ran"))
                )
        );

        assertThat(outcome.steps()).extracting(StepOutcome::status).containsExactly(
                StepOutcome.Status.FAILED, StepOutcome.Status.SUCCEEDED);
        // CONTINUE must never turn a partial failure into an apparent success.
        assertThat(outcome.successful()).isFalse();
        assertThat(outcome.failures()).hasSize(1);
    }

    @Test
    void conditionalStepIsSkippedRatherThanFailed() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        OrchestrationOutcome outcome = orchestrator.sequential(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of("escalate", false)),
                FailurePolicy.FAIL_FAST,
                List.of(
                        OrchestrationStep.of("maybe", "echo", state -> Map.of("message", "x"))
                                .when(state -> state.get("escalate", Boolean.class).orElse(false))
                )
        );

        assertThat(outcome.steps().get(0).status()).isEqualTo(StepOutcome.Status.SKIPPED);
        assertThat(outcome.successful()).isTrue();
    }

    @Test
    void rejectsDuplicateStepNamesThatWouldOverwriteBlackboardResults() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        assertThatThrownBy(() -> orchestrator.sequential(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                FailurePolicy.FAIL_FAST,
                List.of(OrchestrationStep.of("dup", "echo"), OrchestrationStep.of("dup", "echo"))
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    // --- parallel -------------------------------------------------------------------------

    @Test
    void parallelStepsRunConcurrentlyAndAllResultsAreCollected() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            // The latch only releases once all three agents are running at the same time, so this
            // fails rather than hangs forever if execution silently degraded to sequential.
            CountDownLatch concurrent = new CountDownLatch(3);
            AgentRegistry registry = new AgentRegistry(List.of(new LatchAgent(concurrent)));
            AgentOrchestrator orchestrator = new AgentOrchestrator(
                    runtimeService(registry, new RecordingExecutionStore()), pool);

            OrchestrationOutcome outcome = orchestrator.parallel(
                    rootContext("wf-1", null),
                    OrchestrationState.withInput(Map.of()),
                    FailurePolicy.CONTINUE,
                    List.of(
                            OrchestrationStep.of("a", "latch", state -> Map.of("id", "a")),
                            OrchestrationStep.of("b", "latch", state -> Map.of("id", "b")),
                            OrchestrationStep.of("c", "latch", state -> Map.of("id", "c"))
                    )
            );

            assertThat(outcome.successful()).isTrue();
            assertThat(outcome.results()).containsOnlyKeys("a", "b", "c");
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void parallelAwaitsEveryStepEvenWhenOneFails() {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), pool);

            OrchestrationOutcome outcome = orchestrator.parallel(
                    rootContext("wf-1", null),
                    OrchestrationState.withInput(Map.of()),
                    FailurePolicy.FAIL_FAST,
                    List.of(
                            OrchestrationStep.of("ok", "echo", state -> Map.of("message", "fine")),
                            OrchestrationStep.of("boom", "boom", state -> Map.of())
                    )
            );

            // Both are reported: an in-flight sibling must not be abandoned, or it would keep
            // consuming workflow budget after the join returned.
            assertThat(outcome.steps()).hasSize(2);
            assertThat(outcome.successful()).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelFallsBackToSequentialWhenNoExecutorIsConfigured() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        OrchestrationOutcome outcome = orchestrator.parallel(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                FailurePolicy.CONTINUE,
                List.of(
                        OrchestrationStep.of("a", "echo", state -> Map.of("message", "a")),
                        OrchestrationStep.of("b", "echo", state -> Map.of("message", "b"))
                )
        );

        assertThat(outcome.successful()).isTrue();
        assertThat(outcome.results()).containsOnlyKeys("a", "b");
    }

    @Test
    void parallelFanOutSharesTheAggregateWorkflowBudget() {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), pool);
            AgentWorkflowBudget budget = new AgentWorkflowBudget(2, 0, BigDecimal.ZERO);

            OrchestrationOutcome outcome = orchestrator.parallel(
                    rootContext("wf-1", budget),
                    OrchestrationState.withInput(Map.of()),
                    FailurePolicy.CONTINUE,
                    List.of(
                            OrchestrationStep.of("a", "echo", state -> Map.of("message", "a")),
                            OrchestrationStep.of("b", "echo", state -> Map.of("message", "b")),
                            OrchestrationStep.of("c", "echo", state -> Map.of("message", "c")),
                            OrchestrationStep.of("d", "echo", state -> Map.of("message", "d"))
                    )
            );

            // Fanning out must not be a way to exceed the workflow-wide invocation ceiling.
            assertThat(outcome.steps().stream().filter(StepOutcome::succeeded)).hasSize(2);
            assertThat(outcome.failures()).hasSize(2);
            assertThat(outcome.failures()).allSatisfy(failure ->
                    assertThat(failure.failure()).isInstanceOfSatisfying(AgentExecutionException.class, ex ->
                            assertThat(ex.errorCode()).isEqualTo(AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED)));
        } finally {
            pool.shutdownNow();
        }
    }

    // --- routing --------------------------------------------------------------------------

    @Test
    void routeExecutesOnlyTheSelectedBranch() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        OrchestrationOutcome outcome = orchestrator.route(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of("kind", "refund")),
                state -> state.get("kind", String.class).orElse(null),
                Map.of(
                        "refund", OrchestrationStep.of("refund", "echo", state -> Map.of("message", "refund")),
                        "dispute", OrchestrationStep.of("dispute", "echo", state -> Map.of("message", "dispute"))
                )
        );

        assertThat(outcome.steps()).hasSize(1);
        assertThat(outcome.steps().get(0).stepName()).isEqualTo("refund");
    }

    @Test
    void routeWithUnknownKeyDegradesToNoRouteInsteadOfFailing() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        OrchestrationOutcome outcome = orchestrator.route(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                state -> "hallucinated-branch",
                Map.of("refund", OrchestrationStep.of("refund", "echo"))
        );

        assertThat(outcome.steps()).isEmpty();
        assertThat(outcome.successful()).isTrue();
    }

    // --- supervisor -----------------------------------------------------------------------

    @Test
    void supervisorLoopRunsChosenWorkersUntilItSignalsDone() {
        RecordingExecutionStore store = new RecordingExecutionStore();
        ScriptedSupervisorAgent supervisor = new ScriptedSupervisorAgent(List.of(
                Map.of("next", "research"),
                Map.of("next", "summarize"),
                Map.of("done", true)
        ));
        AgentRegistry registry = new AgentRegistry(List.of(supervisor, new EchoAgent()));
        AgentOrchestrator orchestrator = new AgentOrchestrator(runtimeService(registry, store), null);

        OrchestrationOutcome outcome = orchestrator.supervisor(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                "supervisor",
                Map.of(
                        "research", OrchestrationStep.of("research", "echo", state -> Map.of("message", "researched")),
                        "summarize", OrchestrationStep.of("summarize", "echo", state -> Map.of("message", "summarized"))
                ),
                10
        );

        assertThat(outcome.successful()).isTrue();
        assertThat(outcome.state().get(AgentOrchestrator.SUPERVISOR_TRACE))
                .contains(List.of("research", "summarize"));
    }

    @Test
    void supervisorLoopIsBoundedWhenTheSupervisorNeverSignalsDone() {
        // A supervisor that always asks for one more step is the realistic runaway case; the loop
        // bound must stop it rather than relying on budget exhaustion.
        ScriptedSupervisorAgent supervisor = new ScriptedSupervisorAgent(List.of(Map.of("next", "work")), true);
        AgentRegistry registry = new AgentRegistry(List.of(supervisor, new EchoAgent()));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                runtimeService(registry, new RecordingExecutionStore()), null);

        OrchestrationOutcome outcome = orchestrator.supervisor(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                "supervisor",
                Map.of("work", OrchestrationStep.of("work", "echo", state -> Map.of("message", "w"))),
                3
        );

        // 3 supervisor turns + 3 worker runs.
        assertThat(outcome.steps()).hasSize(6);
        assertThat(supervisor.calls.get()).isEqualTo(3);
    }

    @Test
    void supervisorRequiresAPositiveIterationBound() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        assertThatThrownBy(() -> orchestrator.supervisor(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                "supervisor",
                Map.of(),
                0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supervisorStopsWhenItNamesAnUnknownStep() {
        ScriptedSupervisorAgent supervisor = new ScriptedSupervisorAgent(List.of(Map.of("next", "no-such-worker")));
        AgentRegistry registry = new AgentRegistry(List.of(supervisor, new EchoAgent()));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                runtimeService(registry, new RecordingExecutionStore()), null);

        OrchestrationOutcome outcome = orchestrator.supervisor(
                rootContext("wf-1", null),
                OrchestrationState.withInput(Map.of()),
                "supervisor",
                Map.of("work", OrchestrationStep.of("work", "echo")),
                5
        );

        // Only the supervisor turn ran; a hallucinated step name ends the loop cleanly.
        assertThat(outcome.steps()).hasSize(1);
        assertThat(outcome.successful()).isTrue();
    }

    // --- linkage --------------------------------------------------------------------------

    @Test
    void everyStepExecutionIsLinkedToTheWorkflowAndParent() {
        RecordingExecutionStore store = new RecordingExecutionStore();
        AgentOrchestrator orchestrator = orchestrator(store, null);

        orchestrator.sequential(
                rootContext("wf-root", null),
                OrchestrationState.withInput(Map.of()),
                FailurePolicy.FAIL_FAST,
                List.of(OrchestrationStep.of("a", "echo", state -> Map.of("message", "a")))
        );

        AgentExecutionRecord record = store.records.values().iterator().next();        assertThat(record.workflowId()).isEqualTo("wf-root");
        assertThat(record.parentExecutionId()).isEqualTo("wf-root");
    }

    @Test
    void orchestratorAppliesTheSameCycleGuardAsAgentCall() {
        AgentOrchestrator orchestrator = orchestrator(new RecordingExecutionStore(), null);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AgentWorkflowContext.WORKFLOW_ID, "wf-1");
        attributes.put(AgentWorkflowContext.AGENT_PATH, List.of("echo", "other"));
        attributes.put("agentId", "other");
        AgentExecutionContext cyclic = new AgentExecutionContext(
                "exec-1", "tenant-1", "actor-1", "corr-1", Instant.now(), attributes);

        OrchestrationOutcome outcome = orchestrator.sequential(
                cyclic,
                OrchestrationState.withInput(Map.of()),
                FailurePolicy.CONTINUE,
                List.of(OrchestrationStep.of("loop", "echo", state -> Map.of("message", "x")))
        );

        assertThat(outcome.failures()).hasSize(1);
        assertThat(outcome.failures().get(0).failure()).hasMessageContaining("cycle");
    }

    // --- helpers --------------------------------------------------------------------------

    private static String firstMessage(OrchestrationState state) {
        return state.result("first")
                .filter(Map.class::isInstance)
                .map(value -> String.valueOf(((Map<?, ?>) value).get("message")))
                .orElse("missing");
    }

    private AgentExecutionContext rootContext(String executionId, AgentWorkflowBudget budget) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AgentWorkflowContext.WORKFLOW_ID, executionId);
        if (budget != null) {
            attributes.put(AgentWorkflowContext.WORKFLOW_BUDGET, budget);
        }
        return new AgentExecutionContext(executionId, "tenant-1", "actor-1", "corr-1", Instant.now(), attributes);
    }

    private AgentOrchestrator orchestrator(AgentExecutionStore store, java.util.concurrent.Executor executor) {
        AgentRegistry registry = new AgentRegistry(List.of(new EchoAgent(), new BoomAgent()));
        return new AgentOrchestrator(runtimeService(registry, store), executor);
    }

    private AgentRuntimeService runtimeService(AgentRegistry registry, AgentExecutionStore store) {
        return new AgentRuntimeService(
                registry,
                new DefaultAgentPolicyResolver(),
                (context, descriptor) -> true,
                (context, descriptor, input) -> GuardrailDecision.allow(),
                store,
                (context, descriptor, status, message) -> {
                },
                List.of(),
                List.of()
        );
    }

    private static final class EchoAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR =
                new AgentDescriptor("echo", "Echo", "1.0.0", "Echoes input", AgentRiskLevel.LOW, Set.of("test"));

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            return new AgentResult<>(new LinkedHashMap<>(input.values()), null, List.of(), Map.of());
        }
    }

    private static final class BoomAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR =
                new AgentDescriptor("boom", "Boom", "1.0.0", "Always fails", AgentRiskLevel.LOW, Set.of("test"));

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            throw new IllegalStateException("deliberate step failure");
        }
    }

    private static final class LatchAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR =
                new AgentDescriptor("latch", "Latch", "1.0.0", "Blocks until all peers arrive", AgentRiskLevel.LOW, Set.of("test"));

        private final CountDownLatch latch;

        private LatchAgent(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            latch.countDown();
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Steps did not run concurrently");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return new AgentResult<>(new LinkedHashMap<>(input.values()), null, List.of(), Map.of());
        }
    }

    private static final class ScriptedSupervisorAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR =
                new AgentDescriptor("supervisor", "Supervisor", "1.0.0", "Chooses next step", AgentRiskLevel.LOW, Set.of("test"));

        private final List<Map<String, Object>> script;
        private final boolean repeatLast;
        private final AtomicInteger calls = new AtomicInteger();

        private ScriptedSupervisorAgent(List<Map<String, Object>> script) {
            this(script, false);
        }

        private ScriptedSupervisorAgent(List<Map<String, Object>> script, boolean repeatLast) {
            this.script = script;
            this.repeatLast = repeatLast;
        }

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            int index = calls.getAndIncrement();
            Map<String, Object> decision;
            if (index < script.size()) {
                decision = script.get(index);
            } else if (repeatLast) {
                decision = script.get(script.size() - 1);
            } else {
                decision = Map.of("done", true);
            }
            return new AgentResult<>(new LinkedHashMap<>(decision), null, List.of(), Map.of());
        }
    }

    private static final class RecordingExecutionStore implements AgentExecutionStore {
        private final Map<String, AgentExecutionRecord> records = new ConcurrentHashMap<>();
        private final List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
            records.compute(context.executionId(), (id, existing) -> new AgentExecutionRecord(
                    id,
                    descriptor.id(),
                    descriptor.version(),
                    context.tenantId(),
                    context.actorId(),
                    AgentRunStatus.RUNNING,
                    "{}",
                    null,
                    null,
                    existing == null ? Instant.now() : existing.createdAt(),
                    Instant.now(),
                    null,
                    AgentWorkflowContext.workflowId(context),
                    AgentWorkflowContext.parentExecutionId(context)
            ));
            if (!order.contains(context.executionId())) {
                order.add(context.executionId());
            }
        }

        @Override
        public void markCompleted(String executionId, AgentOutput output) {
            records.computeIfPresent(executionId, (id, existing) -> new AgentExecutionRecord(
                    existing.executionId(), existing.agentId(), existing.agentVersion(), existing.tenantId(),
                    existing.actorId(), AgentRunStatus.SUCCEEDED, existing.inputJson(), "{}", null,
                    existing.createdAt(), Instant.now(), existing.idempotencyKey(),
                    existing.workflowId(), existing.parentExecutionId()
            ));
        }

        @Override
        public void markFailed(String executionId, Throwable failure) {
            records.computeIfPresent(executionId, (id, existing) -> new AgentExecutionRecord(
                    existing.executionId(), existing.agentId(), existing.agentVersion(), existing.tenantId(),
                    existing.actorId(), AgentRunStatus.FAILED, existing.inputJson(), null, failure.getMessage(),
                    existing.createdAt(), Instant.now(), existing.idempotencyKey(),
                    existing.workflowId(), existing.parentExecutionId()
            ));
        }

        @Override
        public Optional<AgentExecutionRecord> findByExecutionId(String executionId) {
            return Optional.ofNullable(records.get(executionId));
        }

        @Override
        public List<AgentExecutionRecord> findByWorkflowId(String workflowId, int limit) {
            synchronized (order) {
                return order.stream()
                        .map(records::get)
                        .filter(java.util.Objects::nonNull)
                        .filter(record -> workflowId.equals(record.workflowId()))
                        .limit(limit)
                        .toList();
            }
        }
    }
}
