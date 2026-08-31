package ai.qubere.agent.async;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.DefaultAgentPolicyResolver;
import ai.qubere.agent.runtime.GuardrailDecision;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAsyncRuntimeServiceTest {

    @Test
    void queuesAndProcessesAsyncRun() {
        TestExecutionStore executionStore = new TestExecutionStore();
        List<AgentRunCallback> callbacks = new ArrayList<>();
        AgentAsyncRuntimeService service = service(executionStore, false, callbacks);

        AgentAsyncRunHandle handle = service.submit("echo", "1.0.0", input("hello"), context("exec-async"), null, "https://callback.test/run");

        assertThat(handle.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(executionStore.status("exec-async")).isEqualTo(AgentRunStatus.QUEUED);

        Optional<AgentOutput> output = service.processNext();

        assertThat(output).isPresent();
        assertThat(executionStore.status("exec-async")).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(callbacks)
                .extracting(AgentRunCallback::status)
                .containsExactly(AgentRunStatus.SUCCEEDED);
    }

    @Test
    void createsApprovalAndWaitsWhenPolicyRequiresHumanApproval() {
        TestExecutionStore executionStore = new TestExecutionStore();
        AgentAsyncRuntimeService service = service(executionStore, true, new ArrayList<>());

        AgentAsyncRunHandle handle = service.submit("echo", "1.0.0", input("approve-me"), context("exec-waiting"), null, null);

        assertThat(handle.status()).isEqualTo(AgentRunStatus.WAITING_FOR_APPROVAL);
        assertThat(handle.approvalId()).isNotBlank();
        assertThat(executionStore.status("exec-waiting")).isEqualTo(AgentRunStatus.WAITING_FOR_APPROVAL);
        assertThat(service.processNext()).isEmpty();
    }

    @Test
    void resumesApprovedExecutionWithOriginalInput() {
        TestExecutionStore executionStore = new TestExecutionStore();
        AgentAsyncRuntimeService service = service(executionStore, true, new ArrayList<>());
        AgentAsyncRunHandle waiting = service.submit("echo", "1.0.0", input("approved"), context("exec-approved"), null, null);

        AgentAsyncRunHandle resumed = service.resumeApproved(waiting.approvalId(), "approver");
        Optional<AgentOutput> output = service.processNext();

        assertThat(resumed.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(output).isPresent();
        @SuppressWarnings("unchecked")
        AgentResult<Map<String, Object>> result = (AgentResult<Map<String, Object>>) output.get();
        assertThat(result.value()).containsEntry("message", "approved");
        assertThat(executionStore.status("exec-approved")).isEqualTo(AgentRunStatus.SUCCEEDED);
    }

    @Test
    void rejectedApprovalCancelsExecutionAndEmitsCallback() {
        TestExecutionStore executionStore = new TestExecutionStore();
        List<AgentRunCallback> callbacks = new ArrayList<>();
        AgentAsyncRuntimeService service = service(executionStore, true, callbacks);
        AgentAsyncRunHandle waiting = service.submit("echo", "1.0.0", input("reject-me"), context("exec-rejected"), null, "https://callback.test/reject");

        AgentAsyncRunHandle rejected = service.reject(waiting.approvalId(), "approver");

        assertThat(rejected.status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(executionStore.status("exec-rejected")).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(callbacks)
                .extracting(AgentRunCallback::status)
                .containsExactly(AgentRunStatus.CANCELLED);
    }

    private AgentAsyncRuntimeService service(TestExecutionStore executionStore, boolean requireApproval, List<AgentRunCallback> callbacks) {
        AgentRegistry registry = new AgentRegistry(List.of(new EchoAgent()));
        AgentRuntimeService runtimeService = new AgentRuntimeService(
                registry,
                new DefaultAgentPolicyResolver(),
                (context, descriptor) -> true,
                (context, descriptor, input) -> GuardrailDecision.allow(),
                executionStore,
                (context, descriptor, status, message) -> {
                }
        );
        return new AgentAsyncRuntimeService(
                runtimeService,
                registry,
                (agentId, options) -> new DefaultAgentPolicyResolver().resolve(
                        agentId,
                        new AgentRunOptions(null, null, null, null, null, requireApproval, null, null)
                ),
                executionStore,
                new InMemoryAgentAsyncQueue(),
                new InMemoryAgentApprovalStore(),
                new InMemoryAgentPendingCommandStore(),
                callbacks::add,
                new AgentPlatformProperties()
        );
    }

    private static GenericAgentInput input(String message) {
        return new GenericAgentInput(Map.of("message", message));
    }

    private static AgentExecutionContext context(String executionId) {
        return new AgentExecutionContext(executionId, "tenant", "actor", "corr", Instant.now(), Map.of());
    }

    private static final class EchoAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
                "echo",
                "Echo",
                "1.0.0",
                "Echo test agent",
                AgentRiskLevel.LOW,
                Set.of("test")
        );

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            return new AgentResult<>(input.values(), null, List.of(), Map.of());
        }
    }

    private static final class TestExecutionStore implements AgentExecutionStore {
        private final Map<String, AgentRunStatus> statuses = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void markQueued(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
            statuses.put(context.executionId(), AgentRunStatus.QUEUED);
        }

        @Override
        public void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
            statuses.put(context.executionId(), AgentRunStatus.RUNNING);
        }

        @Override
        public void markCompleted(String executionId, AgentOutput output) {
            statuses.put(executionId, AgentRunStatus.SUCCEEDED);
        }

        @Override
        public void markFailed(String executionId, Throwable failure) {
            statuses.put(executionId, AgentRunStatus.FAILED);
        }

        @Override
        public void markWaitingForApproval(String executionId, String approvalId, String reason) {
            statuses.put(executionId, AgentRunStatus.WAITING_FOR_APPROVAL);
        }

        @Override
        public void markCancelled(String executionId, String reason) {
            statuses.put(executionId, AgentRunStatus.CANCELLED);
        }

        @Override
        public Optional<AgentExecutionRecord> findByExecutionId(String executionId) {
            return Optional.empty();
        }

        AgentRunStatus status(String executionId) {
            return statuses.get(executionId);
        }
    }
}
