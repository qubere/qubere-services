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
import ai.qubere.agent.tools.AgentTool;
import ai.qubere.agent.tools.ToolApprovalPolicy;
import ai.qubere.agent.tools.ToolApprovalRequestSink;
import ai.qubere.agent.tools.ToolCallRecord;
import ai.qubere.agent.tools.ToolCallStatus;
import ai.qubere.agent.tools.ToolDescriptor;
import ai.qubere.agent.tools.ToolExecutionService;
import ai.qubere.agent.tools.ToolInput;
import ai.qubere.agent.tools.ToolRegistry;
import ai.qubere.agent.tools.ToolResult;
import ai.qubere.agent.tools.ToolRiskLevel;
import ai.qubere.agent.tools.ToolSideEffect;
import java.time.Duration;
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
    void reusesExistingAsyncRunForSameTenantAndIdempotencyKey() {
        TestExecutionStore executionStore = new TestExecutionStore();
        AgentAsyncRuntimeService service = service(executionStore, false, new ArrayList<>());

        AgentAsyncRunHandle first = service.submit("echo", "1.0.0", input("hello"), context("exec-first"), null, null, "retry-key-1");
        AgentAsyncRunHandle retry = service.submit("echo", "1.0.0", input("hello again"), context("exec-second"), null, null, " retry-key-1 ");

        assertThat(first.executionId()).isEqualTo("exec-first");
        assertThat(retry.executionId()).isEqualTo("exec-first");
        assertThat(retry.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(executionStore.findByExecutionId("exec-second")).isEmpty();
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
    void repeatedApprovalDecisionReturnsExistingExecutionHandle() {
        TestExecutionStore executionStore = new TestExecutionStore();
        AgentAsyncRuntimeService service = service(executionStore, true, new ArrayList<>());
        AgentAsyncRunHandle waiting = service.submit("echo", "1.0.0", input("approved"), context("exec-approval-retry"), null, null);

        AgentAsyncRunHandle first = service.resumeApproved(waiting.approvalId(), "approver");
        AgentAsyncRunHandle retry = service.resumeApproved(waiting.approvalId(), "approver");

        assertThat(first.executionId()).isEqualTo("exec-approval-retry");
        assertThat(retry.executionId()).isEqualTo("exec-approval-retry");
        assertThat(retry.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(retry.approvalId()).isEqualTo(waiting.approvalId());
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

    @Test
    void repeatedRejectDecisionReturnsExistingExecutionHandleWithoutDuplicateCallback() {
        TestExecutionStore executionStore = new TestExecutionStore();
        List<AgentRunCallback> callbacks = new ArrayList<>();
        AgentAsyncRuntimeService service = service(executionStore, true, callbacks);
        AgentAsyncRunHandle waiting = service.submit("echo", "1.0.0", input("reject-me"), context("exec-reject-retry"), null, "https://callback.test/reject");

        AgentAsyncRunHandle first = service.reject(waiting.approvalId(), "approver");
        AgentAsyncRunHandle retry = service.reject(waiting.approvalId(), "approver");

        assertThat(first.executionId()).isEqualTo("exec-reject-retry");
        assertThat(retry.executionId()).isEqualTo("exec-reject-retry");
        assertThat(retry.status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(retry.approvalId()).isEqualTo(waiting.approvalId());
        assertThat(callbacks).hasSize(1);
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


    private static AgentTool writeTool() {
        ToolDescriptor descriptor = new ToolDescriptor(
                "write-record",
                "Writes a record after approval",
                ToolRiskLevel.DESTRUCTIVE,
                Set.of(ToolSideEffect.WRITE_INTERNAL),
                Set.of(),
                Map.of(),
                Map.of(),
                Duration.ofSeconds(5),
                true
        );
        return new AgentTool() {
            @Override
            public ToolResult execute(ToolInput input) {
                return ToolResult.success(Map.of(
                        "recordId", input.arguments().get("recordId"),
                        "value", input.arguments().get("value")
                ));
            }

            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }
        };
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
        private final Map<String, AgentExecutionRecord> records = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void markQueued(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
            markQueued(context, descriptor, input, null);
        }

        @Override
        public void markQueued(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input, String idempotencyKey) {
            upsert(context, descriptor, AgentRunStatus.QUEUED, idempotencyKey);
        }

        @Override
        public void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
            upsert(context, descriptor, AgentRunStatus.RUNNING, null);
        }

        @Override
        public void markCompleted(String executionId, AgentOutput output) {
            updateStatus(executionId, AgentRunStatus.SUCCEEDED, null);
        }

        @Override
        public void markFailed(String executionId, Throwable failure) {
            updateStatus(executionId, AgentRunStatus.FAILED, failure.getMessage());
        }

        @Override
        public void markWaitingForApproval(String executionId, String approvalId, String reason) {
            updateStatus(executionId, AgentRunStatus.WAITING_FOR_APPROVAL, reason);
        }

        @Override
        public void markCancelled(String executionId, String reason) {
            updateStatus(executionId, AgentRunStatus.CANCELLED, reason);
        }

        @Override
        public Optional<AgentExecutionRecord> findByExecutionId(String executionId) {
            return Optional.ofNullable(records.get(executionId));
        }

        @Override
        public Optional<AgentExecutionRecord> findByIdempotencyKey(String tenantId, String idempotencyKey) {
            return records.values().stream()
                    .filter(record -> java.util.Objects.equals(record.tenantId(), tenantId))
                    .filter(record -> java.util.Objects.equals(record.idempotencyKey(), idempotencyKey))
                    .findFirst();
        }

        AgentRunStatus status(String executionId) {
            return records.get(executionId).status();
        }

        private void upsert(AgentExecutionContext context, AgentDescriptor descriptor, AgentRunStatus status, String idempotencyKey) {
            Instant now = Instant.now();
            AgentExecutionRecord existing = records.get(context.executionId());
            records.put(context.executionId(), new AgentExecutionRecord(
                    context.executionId(),
                    descriptor.id(),
                    descriptor.version(),
                    context.tenantId(),
                    context.actorId(),
                    status,
                    "{}",
                    existing == null ? null : existing.outputJson(),
                    existing == null ? null : existing.errorMessage(),
                    existing == null ? now : existing.createdAt(),
                    now,
                    idempotencyKey == null || idempotencyKey.isBlank() ? existing == null ? null : existing.idempotencyKey() : idempotencyKey.trim()
            ));
        }

        private void updateStatus(String executionId, AgentRunStatus status, String errorMessage) {
            AgentExecutionRecord existing = records.get(executionId);
            records.put(executionId, new AgentExecutionRecord(
                    existing.executionId(),
                    existing.agentId(),
                    existing.agentVersion(),
                    existing.tenantId(),
                    existing.actorId(),
                    status,
                    existing.inputJson(),
                    existing.outputJson(),
                    errorMessage,
                    existing.createdAt(),
                    Instant.now(),
                    existing.idempotencyKey()
            ));
        }
    }
}
