package ai.qubere.agent.runtime;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.GenericAgentInput;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRuntimeServiceTest {

    @Test
    void runsRegisteredAgentAndRecordsLifecycle() {
        InMemoryStore store = new InMemoryStore();
        List<AgentRunStatus> statuses = new ArrayList<>();
        AgentRuntimeService service = service(List.of(new EchoAgent()), store, (context, descriptor, input) -> GuardrailDecision.allow(), statuses);

        AgentOutput output = service.run("echo", new GenericAgentInput(Map.of("message", "hello")), context(), null);

        assertThat(output).isInstanceOf(AgentResult.class);
        assertThat(store.started).isTrue();
        assertThat(store.completed).isTrue();
        assertThat(store.failed).isFalse();
        assertThat(statuses).containsExactly(AgentRunStatus.RUNNING, AgentRunStatus.SUCCEEDED);
    }

    @Test
    void rejectsUnknownAgent() {
        AgentRuntimeService service = service(List.of(), new InMemoryStore(), (context, descriptor, input) -> GuardrailDecision.allow(), new ArrayList<>());

        assertThatThrownBy(() -> service.run("missing", new GenericAgentInput(Map.of()), context(), null))
                .isInstanceOfSatisfying(AgentExecutionException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(AgentErrorCode.AGENT_NOT_FOUND));
    }

    @Test
    void blocksExecutionWhenGuardrailRejectsInput() {
        InMemoryStore store = new InMemoryStore();
        AgentRuntimeService service = service(
                List.of(new EchoAgent()),
                store,
                (context, descriptor, input) -> GuardrailDecision.block("Unsafe input"),
                new ArrayList<>()
        );

        assertThatThrownBy(() -> service.run("echo", new GenericAgentInput(Map.of()), context(), new AgentRunOptions(null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(AgentExecutionException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(AgentErrorCode.GUARDRAIL_BLOCKED));
        assertThat(store.started).isFalse();
    }

    @Test
    void publishesPipelineEventsForSuccessfulRun() {
        InMemoryStore store = new InMemoryStore();
        List<AgentPipelineStep> steps = new ArrayList<>();
        AgentRuntimeService service = new AgentRuntimeService(
                new AgentRegistry(List.of(new EchoAgent())),
                new DefaultAgentPolicyResolver(),
                (context, descriptor) -> true,
                (context, descriptor, input) -> GuardrailDecision.allow(),
                store,
                (context, descriptor, status, message) -> {
                },
                List.of(event -> steps.add(event.step()))
        );

        service.run("echo", "1.0.0", new GenericAgentInput(Map.of("message", "hello")), context(), null);

        assertThat(steps).containsExactly(
                AgentPipelineStep.AGENT_RESOLUTION,
                AgentPipelineStep.POLICY_RESOLUTION,
                AgentPipelineStep.AUTHORIZATION,
                AgentPipelineStep.INPUT_GUARDRAILS,
                AgentPipelineStep.EXECUTION_STARTED,
                AgentPipelineStep.AGENT_INVOCATION,
                AgentPipelineStep.EXECUTION_COMPLETED
        );
    }


    @Test
    void retriesRetryableAgentFailures() {
        FlakyAgent agent = new FlakyAgent(1);
        InMemoryStore store = new InMemoryStore();
        AgentRuntimeService service = new AgentRuntimeService(
                new AgentRegistry(List.of(agent)),
                (agentId, requestedOptions) -> new ai.qubere.agent.core.ResolvedAgentPolicy(
                        true,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        5,
                        8,
                        5,
                        1,
                        java.math.BigDecimal.ZERO,
                        "openai",
                        "default",
                        "latest",
                        ai.qubere.agent.core.AgentRunMode.RECOMMEND,
                        8,
                        0.2d,
                        2048,
                        true,
                        false,
                        false,
                        false,
                        Set.of(),
                        "SUMMARY",
                        "NORMAL"
                ),
                (context, descriptor) -> true,
                (context, descriptor, input) -> GuardrailDecision.allow(),
                store,
                (context, descriptor, status, message) -> {
                },
                List.of(),
                List.of(),
                Runnable::run
        );

        AgentOutput output = service.run("flaky", "1.0.0", new GenericAgentInput(Map.of("message", "retry")), context(), null);

        assertThat(output).isInstanceOf(AgentResult.class);
        assertThat(agent.invocations).isEqualTo(2);
        assertThat(store.completed).isTrue();
    }

    @Test
    void timesOutSlowAgentInvocation() {
        InMemoryStore store = new InMemoryStore();
        Executor newThreadExecutor = command -> {
            Thread thread = new Thread(command);
            thread.setDaemon(true);
            thread.start();
        };
        AgentRuntimeService service = new AgentRuntimeService(
                new AgentRegistry(List.of(new SlowAgent())),
                (agentId, requestedOptions) -> new ai.qubere.agent.core.ResolvedAgentPolicy(
                        true,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        5,
                        8,
                        1,
                        0,
                        java.math.BigDecimal.ZERO,
                        "openai",
                        "default",
                        "latest",
                        ai.qubere.agent.core.AgentRunMode.RECOMMEND,
                        8,
                        0.2d,
                        2048,
                        true,
                        false,
                        false,
                        false,
                        Set.of(),
                        "SUMMARY",
                        "NORMAL"
                ),
                (context, descriptor) -> true,
                (context, descriptor, input) -> GuardrailDecision.allow(),
                store,
                (context, descriptor, status, message) -> {
                },
                List.of(),
                List.of(),
                newThreadExecutor
        );

        assertThatThrownBy(() -> service.run("slow", "1.0.0", new GenericAgentInput(Map.of()), context(), null))
                .isInstanceOfSatisfying(AgentExecutionException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(AgentErrorCode.TIMEOUT));
        assertThat(store.failed).isTrue();
    }

    private AgentRuntimeService service(
            Collection<Agent<?, ?>> agents,
            InMemoryStore store,
            AgentGuardrailService guardrailService,
            List<AgentRunStatus> statuses
    ) {
        return new AgentRuntimeService(
                new AgentRegistry(agents),
                new DefaultAgentPolicyResolver(),
                (context, descriptor) -> true,
                guardrailService,
                store,
                (context, descriptor, status, message) -> statuses.add(status)
        );
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext("exec-1", "tenant-1", "actor-1", "corr-1", Instant.now(), Map.of());
    }

    private static final class EchoAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
                "echo",
                "Echo Agent",
                "1.0.0",
                "Echoes input for runtime tests",
                AgentRiskLevel.LOW,
                Set.of("echo")
        );

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            return new AgentResult<>(input.values(), null, List.of(), Map.of("executionId", context.executionId()));
        }
    }


    private static final class FlakyAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor("flaky", "Flaky Agent", "1.0.0", "Fails before succeeding", AgentRiskLevel.LOW, Set.of("test"));
        private final int failuresBeforeSuccess;
        private int invocations;

        private FlakyAgent(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            invocations++;
            if (invocations <= failuresBeforeSuccess) {
                throw new AgentExecutionException(AgentErrorCode.AI_PROVIDER_FAILURE, "temporary provider failure");
            }
            return new AgentResult<>(Map.of("attempts", invocations), null, List.of(), Map.of());
        }
    }

    private static final class SlowAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor("slow", "Slow Agent", "1.0.0", "Sleeps longer than timeout", AgentRiskLevel.LOW, Set.of("test"));

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return new AgentResult<>(Map.of(), null, List.of(), Map.of());
        }
    }

    private static final class InMemoryStore implements AgentExecutionStore {
        private boolean started;
        private boolean completed;
        private boolean failed;

        @Override
        public void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
            started = true;
        }

        @Override
        public void markCompleted(String executionId, AgentOutput output) {
            completed = true;
        }

        @Override
        public void markFailed(String executionId, Throwable failure) {
            failed = true;
        }

        @Override
        public Optional<AgentExecutionRecord> findByExecutionId(String executionId) {
            return Optional.empty();
        }
    }
}
