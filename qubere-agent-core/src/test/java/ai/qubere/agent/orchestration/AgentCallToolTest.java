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
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.AgentWorkflowBudget;
import ai.qubere.agent.runtime.AgentWorkflowContext;
import ai.qubere.agent.runtime.DefaultAgentPolicyResolver;
import ai.qubere.agent.runtime.GuardrailDecision;
import ai.qubere.agent.tools.ToolExecutionRequest;
import ai.qubere.agent.tools.ToolExecutionService;
import ai.qubere.agent.tools.ToolRegistry;
import ai.qubere.agent.tools.ToolResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCallToolTest {

    @Test
    void invokesSubAgentAndLinksItToTheParentWorkflow() {
        RecordingExecutionStore store = new RecordingExecutionStore();
        AgentRuntimeService runtime = runtimeService(store);
        AgentRegistry registry = registry();
        ToolExecutionService toolService = toolService(runtime, registry);

        AgentExecutionContext orchestratorContext = workflowRootContext("exec-orchestrator");

        ToolResult result = toolService.execute(new ToolExecutionRequest(
                AgentCallTool.TOOL_NAME,
                orchestratorContext,
                ResolvedAgentPolicy.defaults(),
                Map.of(
                        AgentCallTool.ARG_AGENT_ID, "sub.echo",
                        AgentCallTool.ARG_INPUT, Map.of("message", "delegated work")
                )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.values()).containsEntry("agentId", "sub.echo");
        assertThat(result.values()).containsEntry("workflowId", "exec-orchestrator");
        assertThat(result.values().get("output")).isEqualTo(Map.of("message", "delegated work"));

        String childExecutionId = result.values().get("executionId").toString();
        AgentExecutionRecord childRecord = store.findByExecutionId(childExecutionId).orElseThrow();
        assertThat(childRecord.workflowId()).isEqualTo("exec-orchestrator");
        assertThat(childRecord.parentExecutionId()).isEqualTo("exec-orchestrator");
        assertThat(childRecord.isWorkflowRoot()).isFalse();
    }

    @Test
    void subAgentInheritsTenantAndActorFromOrchestrator() {
        RecordingExecutionStore store = new RecordingExecutionStore();
        ToolExecutionService toolService = toolService(runtimeService(store), registry());

        ToolResult result = toolService.execute(new ToolExecutionRequest(
                AgentCallTool.TOOL_NAME,
                workflowRootContext("exec-orchestrator"),
                ResolvedAgentPolicy.defaults(),
                Map.of(AgentCallTool.ARG_AGENT_ID, "sub.echo")
        ));

        AgentExecutionRecord childRecord = store.findByExecutionId(result.values().get("executionId").toString()).orElseThrow();
        assertThat(childRecord.tenantId()).isEqualTo("tenant-1");
        assertThat(childRecord.actorId()).isEqualTo("actor-1");
    }

    @Test
    void sharedWorkflowBudgetLimitsTotalSubAgentInvocations() {
        RecordingExecutionStore store = new RecordingExecutionStore();
        ToolExecutionService toolService = toolService(runtimeService(store), registry());

        // Budget allows two agent invocations for the entire workflow.
        AgentWorkflowBudget budget = new AgentWorkflowBudget(2, 0, BigDecimal.ZERO);
        AgentExecutionContext orchestratorContext = workflowRootContext("exec-orchestrator", budget);

        toolService.execute(callRequest(orchestratorContext));
        toolService.execute(callRequest(orchestratorContext));

        assertThatThrownBy(() -> toolService.execute(callRequest(orchestratorContext)))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED));
    }

    @Test
    void rejectsCallWithoutAgentId() {
        ToolExecutionService toolService = toolService(runtimeService(new RecordingExecutionStore()), registry());

        assertThatThrownBy(() -> toolService.execute(new ToolExecutionRequest(
                AgentCallTool.TOOL_NAME,
                workflowRootContext("exec-orchestrator"),
                ResolvedAgentPolicy.defaults(),
                Map.of()
        ))).isInstanceOf(AgentExecutionException.class);
    }

    @Test
    void rejectsCallToUnregisteredAgent() {
        ToolExecutionService toolService = toolService(runtimeService(new RecordingExecutionStore()), registry());

        assertThatThrownBy(() -> toolService.execute(new ToolExecutionRequest(
                AgentCallTool.TOOL_NAME,
                workflowRootContext("exec-orchestrator"),
                ResolvedAgentPolicy.defaults(),
                Map.of(AgentCallTool.ARG_AGENT_ID, "does.not.exist")
        ))).isInstanceOf(AgentExecutionException.class);
    }

    @Test
    void rejectsSelfRecursionToAvoidUnboundedDelegationLoops() {
        ToolExecutionService toolService = toolService(runtimeService(new RecordingExecutionStore()), registry());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AgentWorkflowContext.WORKFLOW_ID, "exec-orchestrator");
        // Simulate the runtime having already stamped the calling agent's identity on the context.
        attributes.put("agentId", "sub.echo");
        AgentExecutionContext selfCallingContext = new AgentExecutionContext(
                "exec-orchestrator", "tenant-1", "actor-1", "corr-1", Instant.now(), attributes
        );

        assertThatThrownBy(() -> toolService.execute(new ToolExecutionRequest(
                AgentCallTool.TOOL_NAME,
                selfCallingContext,
                ResolvedAgentPolicy.defaults(),
                Map.of(AgentCallTool.ARG_AGENT_ID, "sub.echo")
        ))).isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.TOOL_NOT_ALLOWED));
    }

    @Test
    void rejectsMutualRecursionAcrossDistinctAgents() {
        ToolExecutionService toolService = toolService(runtimeService(new RecordingExecutionStore()), registry());

        // Simulates sub.echo -> other.agent, where other.agent now tries to call sub.echo again.
        // An immediate-caller check alone would allow this and recurse until budget exhaustion.
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AgentWorkflowContext.WORKFLOW_ID, "exec-orchestrator");
        attributes.put(AgentWorkflowContext.AGENT_PATH, List.of("sub.echo", "other.agent"));
        attributes.put("agentId", "other.agent");
        AgentExecutionContext cyclicContext = new AgentExecutionContext(
                "exec-child", "tenant-1", "actor-1", "corr-1", Instant.now(), attributes
        );

        assertThatThrownBy(() -> toolService.execute(new ToolExecutionRequest(
                AgentCallTool.TOOL_NAME,
                cyclicContext,
                ResolvedAgentPolicy.defaults(),
                Map.of(AgentCallTool.ARG_AGENT_ID, "sub.echo")
        ))).isInstanceOfSatisfying(AgentExecutionException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.TOOL_NOT_ALLOWED);
            assertThat(exception.getMessage()).contains("cycle");
            assertThat(exception.getMessage()).contains("sub.echo -> other.agent -> sub.echo");
        });
    }

    @Test
    void rejectsDelegationDeeperThanTheConfiguredLimit() {
        AgentRuntimeService runtime = runtimeService(new RecordingExecutionStore());
        ToolExecutionService toolService = new ToolExecutionService(
                List.of(new AgentCallTool(runtime, registry(), null, 2))
        );

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AgentWorkflowContext.WORKFLOW_ID, "exec-orchestrator");
        attributes.put(AgentWorkflowContext.DELEGATION_DEPTH, 2);
        attributes.put("agentId", "deep.agent");
        AgentExecutionContext deepContext = new AgentExecutionContext(
                "exec-deep", "tenant-1", "actor-1", "corr-1", Instant.now(), attributes
        );

        assertThatThrownBy(() -> toolService.execute(new ToolExecutionRequest(
                AgentCallTool.TOOL_NAME,
                deepContext,
                ResolvedAgentPolicy.defaults(),
                Map.of(AgentCallTool.ARG_AGENT_ID, "sub.echo")
        ))).isInstanceOfSatisfying(AgentExecutionException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.TOOL_NOT_ALLOWED);
            assertThat(exception.getMessage()).contains("maximum depth of 2");
        });
    }

    @Test
    void recordsDelegationDepthAndAgentPathOnTheChildContext() {
        ToolExecutionService toolService = toolService(runtimeService(new RecordingExecutionStore()), registry());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AgentWorkflowContext.WORKFLOW_ID, "exec-orchestrator");
        attributes.put("agentId", "orchestrator");
        AgentExecutionContext rootContext = new AgentExecutionContext(
                "exec-orchestrator", "tenant-1", "actor-1", "corr-1", Instant.now(), attributes
        );

        AgentExecutionContext child = AgentWorkflowContext.childOf(rootContext, "exec-child", "sub.echo");

        assertThat(AgentWorkflowContext.delegationDepth(child)).isEqualTo(1);
        assertThat(AgentWorkflowContext.agentPath(child)).containsExactly("orchestrator", "sub.echo");
        // The child must not inherit the parent's agent identity.
        assertThat(child.attributes()).doesNotContainKey("agentId");
        assertThat(toolService).isNotNull();
    }

    @Test
    void workflowSummaryRollsUpOrchestratorAndSubAgentExecutions() {
        RecordingExecutionStore store = new RecordingExecutionStore();
        AgentRuntimeService runtime = runtimeService(store);
        ToolExecutionService toolService = toolService(runtime, registry());

        AgentExecutionContext orchestratorContext = workflowRootContext("exec-orchestrator");
        // Record the orchestrator's own execution as the workflow root.
        store.markStarted(orchestratorContext, orchestratorDescriptor(), new GenericAgentInput(Map.of()));
        store.markCompleted("exec-orchestrator", new AgentResult<>(Map.of(), null, List.of(), Map.of()));

        toolService.execute(callRequest(orchestratorContext));
        toolService.execute(callRequest(orchestratorContext));

        AgentWorkflowSummary summary = new AgentWorkflowService(store).summarize("exec-orchestrator");

        assertThat(summary.totalExecutions()).isEqualTo(3);
        assertThat(summary.status()).isEqualTo(AgentWorkflowStatus.SUCCEEDED);
        assertThat(summary.root()).isNotNull();
        assertThat(summary.root().executionId()).isEqualTo("exec-orchestrator");
    }

    private ToolExecutionRequest callRequest(AgentExecutionContext context) {
        return new ToolExecutionRequest(
                AgentCallTool.TOOL_NAME,
                context,
                ResolvedAgentPolicy.defaults(),
                Map.of(AgentCallTool.ARG_AGENT_ID, "sub.echo", AgentCallTool.ARG_INPUT, Map.of("message", "work"))
        );
    }

    private AgentExecutionContext workflowRootContext(String executionId) {
        return workflowRootContext(executionId, null);
    }

    private AgentExecutionContext workflowRootContext(String executionId, AgentWorkflowBudget budget) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AgentWorkflowContext.WORKFLOW_ID, executionId);
        if (budget != null) {
            attributes.put(AgentWorkflowContext.WORKFLOW_BUDGET, budget);
        }
        return new AgentExecutionContext(executionId, "tenant-1", "actor-1", "corr-1", Instant.now(), attributes);
    }

    private AgentRegistry registry() {
        return new AgentRegistry(List.of(new SubEchoAgent()));
    }

    private AgentRuntimeService runtimeService(AgentExecutionStore store) {
        return new AgentRuntimeService(
                registry(),
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

    private ToolExecutionService toolService(AgentRuntimeService runtime, AgentRegistry registry) {
        return new ToolExecutionService(List.of(new AgentCallTool(runtime, registry)));
    }

    private AgentDescriptor orchestratorDescriptor() {
        return new AgentDescriptor("orchestrator", "Orchestrator", "1.0.0", "test", AgentRiskLevel.LOW, Set.of());
    }

    private static final class SubEchoAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR =
                new AgentDescriptor("sub.echo", "Sub Echo", "1.0.0", "Sub-agent that echoes input", AgentRiskLevel.LOW, Set.of("test"));

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            return new AgentResult<>(input.values(), null, List.of(), Map.of());
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
