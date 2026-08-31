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
