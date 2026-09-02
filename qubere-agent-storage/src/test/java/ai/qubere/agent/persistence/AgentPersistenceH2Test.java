package ai.qubere.agent.persistence;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.ai.ModelUsageRecord;
import ai.qubere.agent.ai.ModelUsageStatus;
import ai.qubere.agent.async.AgentApprovalRequest;
import ai.qubere.agent.async.AgentApprovalStatus;
import ai.qubere.agent.async.AgentPendingCommandStore;
import ai.qubere.agent.async.AgentRunCommand;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.evaluation.EvaluationCaseResult;
import ai.qubere.agent.evaluation.EvaluationResult;
import ai.qubere.agent.evaluation.EvaluationStatus;
import ai.qubere.agent.evaluation.GoldenDataset;
import ai.qubere.agent.evaluation.GoldenExample;
import ai.qubere.agent.prompts.PromptStatus;
import ai.qubere.agent.prompts.PromptTemplate;
import ai.qubere.agent.runtime.AgentPipelineEvent;
import ai.qubere.agent.runtime.AgentPipelineStep;
import ai.qubere.agent.runtime.AgentWorkflowBudget;
import ai.qubere.agent.runtime.AgentWorkflowContext;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.checkpoint.AgentCheckpoint;
import ai.qubere.agent.orchestration.AgentWorkflowService;
import ai.qubere.agent.orchestration.AgentWorkflowStatus;
import ai.qubere.agent.orchestration.AgentWorkflowSummary;
import ai.qubere.agent.tools.ToolAuditEvent;
import ai.qubere.agent.tools.ToolCallRecord;
import ai.qubere.agent.tools.ToolCallStatus;
import ai.qubere.agent.tools.ToolRiskLevel;
import ai.qubere.agent.tools.ToolSideEffect;
import ai.qubere.agent.tools.ToolAuditStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.format_sql=false"
})
class AgentPersistenceH2Test {

    @Autowired
    private JpaAgentExecutionStore executionStore;

    @Autowired
    private JpaPromptVersionStore promptVersionStore;

    @Autowired
    private JpaToolAuditService toolAuditService;

    @Autowired
    private JpaAgentApprovalStore approvalStore;

    @Autowired
    private AgentPendingCommandStore pendingCommandStore;

    @Autowired
    private AgentAsyncQueueCommandRepository asyncQueueRepository;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AgentToolAuditEventRepository toolAuditRepository;

    @Autowired
    private JpaToolCallRecorder toolCallRecorder;

    @Autowired
    private AgentToolCallRepository toolCallRepository;

    @Autowired
    private JpaAgentExecutionLogListener executionLogListener;

    @Autowired
    private AgentExecutionLogRepository executionLogRepository;

    @Autowired
    private JpaModelUsageRecorder modelUsageRecorder;

    @Autowired
    private AgentModelUsageRepository modelUsageRepository;

    @Autowired
    private JpaEvaluationResultStore evaluationResultStore;

    @Autowired
    private AgentCheckpointRepository checkpointRepository;

    @Autowired
    private JpaAgentCheckpointStore checkpointStore;

    @Autowired
    private AgentWorkflowBudgetRepository workflowBudgetRepository;

    @Autowired
    private AgentEvaluationDatasetRepository evaluationDatasetRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void persistsAgentExecutionLifecycleWithH2() {
        AgentExecutionContext context = new AgentExecutionContext("exec-h2", "tenant", "actor", "corr", Instant.now(), Map.of());
        AgentDescriptor descriptor = new AgentDescriptor("agent", "Agent", "1.0.0", "Test", AgentRiskLevel.LOW, Set.of());

        executionStore.markStarted(context, descriptor, new GenericAgentInput(Map.of("message", "hello")));
        executionStore.markCompleted("exec-h2", new AgentResult<>(Map.of("ok", true), null, List.of(), Map.of()));

        assertThat(executionStore.findByExecutionId("exec-h2"))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
                    assertThat(record.outputJson()).contains("\"ok\":true");
                });
    }

    @Test
    void persistsWorkflowLinkageAndRollsUpMultiAgentWorkflowWithH2() {
        AgentDescriptor orchestrator = new AgentDescriptor("wf.orchestrator", "Orchestrator", "1.0.0", "Test", AgentRiskLevel.LOW, Set.of());
        AgentDescriptor subAgent = new AgentDescriptor("wf.sub", "Sub", "1.0.0", "Test", AgentRiskLevel.LOW, Set.of());

        AgentExecutionContext rootContext = new AgentExecutionContext(
                "wf-root", "tenant", "actor", "corr", Instant.now(),
                Map.of(AgentWorkflowContext.WORKFLOW_ID, "wf-root")
        );
        executionStore.markStarted(rootContext, orchestrator, new GenericAgentInput(Map.of("message", "orchestrate")));

        AgentExecutionContext childContext = AgentWorkflowContext.childOf(rootContext, "wf-child-1");
        executionStore.markStarted(childContext, subAgent, new GenericAgentInput(Map.of("message", "sub work")));

        executionStore.markCompleted("wf-child-1", new AgentResult<>(Map.of("ok", true), null, List.of(), Map.of()));
        executionStore.markCompleted("wf-root", new AgentResult<>(Map.of("ok", true), null, List.of(), Map.of()));

        assertThat(executionStore.findByExecutionId("wf-child-1"))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.workflowId()).isEqualTo("wf-root");
                    assertThat(record.parentExecutionId()).isEqualTo("wf-root");
                    assertThat(record.isWorkflowRoot()).isFalse();
                });

        assertThat(executionStore.findByExecutionId("wf-root"))
                .isPresent()
                .get()
                .satisfies(record -> assertThat(record.isWorkflowRoot()).isTrue());

        AgentWorkflowSummary summary = new AgentWorkflowService(executionStore).summarize("wf-root");
        assertThat(summary.totalExecutions()).isEqualTo(2);
        assertThat(summary.status()).isEqualTo(AgentWorkflowStatus.SUCCEEDED);
        assertThat(summary.root()).isNotNull();
        assertThat(summary.root().executionId()).isEqualTo("wf-root");
    }

    @Test
    void persistsCheckpointsForRestartSafeMultiStepResumeWithH2() {
        checkpointStore.save(new AgentCheckpoint("exec-ckpt", "charge-card", 1, "\"txn-1\"", Instant.now()));
        checkpointStore.save(new AgentCheckpoint("exec-ckpt", "reserve-stock", 2, "\"res-1\"", Instant.now()));

        assertThat(checkpointStore.find("exec-ckpt", "charge-card"))
                .isPresent()
                .get()
                .satisfies(checkpoint -> assertThat(checkpoint.resultJson()).isEqualTo("\"txn-1\""));

        assertThat(checkpointStore.findByExecutionId("exec-ckpt"))
                .hasSize(2)
                .extracting(AgentCheckpoint::stepName)
                .containsExactly("charge-card", "reserve-stock");

        // Re-saving the same step must update in place rather than duplicate, so a retried save
        // during resume cannot corrupt step history.
        checkpointStore.save(new AgentCheckpoint("exec-ckpt", "charge-card", 1, "\"txn-updated\"", Instant.now()));
        assertThat(checkpointStore.findByExecutionId("exec-ckpt")).hasSize(2);
        assertThat(checkpointStore.find("exec-ckpt", "charge-card"))
                .get()
                .satisfies(checkpoint -> assertThat(checkpoint.resultJson()).isEqualTo("\"txn-updated\""));

        checkpointStore.deleteByExecutionId("exec-ckpt");
        assertThat(checkpointStore.findByExecutionId("exec-ckpt")).isEmpty();
    }

    @Test
    void distributedBudgetEnforcesOneCeilingAcrossServices() {
        // Simulates two services participating in the same workflow. Each gets its own
        // AgentWorkflowBudget object, exactly as separate JVMs would, but both consume the same
        // shared record — so the ceiling is enforced once in total, not once per service.
        JpaDistributedWorkflowBudgetStore budgetStore =
                new JpaDistributedWorkflowBudgetStore(workflowBudgetRepository, transactionManager);

        AgentWorkflowBudget serviceA = new AgentWorkflowBudget(3, 0, BigDecimal.ZERO, budgetStore, "wf-distributed");
        AgentWorkflowBudget serviceB = new AgentWorkflowBudget(3, 0, BigDecimal.ZERO, budgetStore, "wf-distributed");

        assertThat(serviceA.isDistributed()).isTrue();

        serviceA.consumeAgentInvocation("agent.a1");
        serviceB.consumeAgentInvocation("agent.b1");
        serviceA.consumeAgentInvocation("agent.a2");

        // Three invocations already consumed across both services; the fourth must be rejected
        // even though neither service individually issued more than two.
        assertThatThrownBy(() -> serviceB.consumeAgentInvocation("agent.b2"))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED));

        budgetStore.release("wf-distributed");
    }

    @Test
    void distributedBudgetAccumulatesCostAcrossParticipants() {
        JpaDistributedWorkflowBudgetStore budgetStore =
                new JpaDistributedWorkflowBudgetStore(workflowBudgetRepository, transactionManager);

        AgentWorkflowBudget serviceA = new AgentWorkflowBudget(0, 0, new BigDecimal("1.00"), budgetStore, "wf-cost");
        AgentWorkflowBudget serviceB = new AgentWorkflowBudget(0, 0, new BigDecimal("1.00"), budgetStore, "wf-cost");

        serviceA.consumeCost(new BigDecimal("0.60"));

        assertThatThrownBy(() -> serviceB.consumeCost(new BigDecimal("0.60")))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED));

        budgetStore.release("wf-cost");
    }

    @Test
    void releasingDistributedBudgetResetsWorkflowUsage() {
        JpaDistributedWorkflowBudgetStore budgetStore =
                new JpaDistributedWorkflowBudgetStore(workflowBudgetRepository, transactionManager);
        AgentWorkflowBudget budget = new AgentWorkflowBudget(1, 0, BigDecimal.ZERO, budgetStore, "wf-release");

        budget.consumeAgentInvocation("agent.a");
        budgetStore.release("wf-release");

        // After release the workflow starts fresh rather than staying permanently exhausted.
        assertThat(budget.consumeAgentInvocation("agent.b")).isEqualTo(1);
        budgetStore.release("wf-release");
    }

    @Test
    void persistsOperationallyCuratedGoldenDatasetsWithH2() {
        JpaGoldenDatasetRepository datasetRepository =
                new JpaGoldenDatasetRepository(evaluationDatasetRepository, new com.fasterxml.jackson.databind.ObjectMapper());

        GoldenDataset dataset = new GoldenDataset(
                "invoice-regressions",
                "Cases grown from production failures",
                List.of(new GoldenExample(
                        "case-1",
                        "invoice.review",
                        "1.0.0",
                        Map.of("invoiceId", "inv-1"),
                        Map.of("decision", "APPROVE"),
                        null
                )),
                Map.of("owner", "finance-team")
        );

        datasetRepository.save(dataset);

        assertThat(datasetRepository.find("invoice-regressions"))
                .isPresent()
                .get()
                .satisfies(stored -> {
                    assertThat(stored.description()).isEqualTo("Cases grown from production failures");
                    assertThat(stored.metadata()).containsEntry("owner", "finance-team");
                    assertThat(stored.examples()).hasSize(1);
                    assertThat(stored.examples().get(0).agentId()).isEqualTo("invoice.review");
                    assertThat(stored.examples().get(0).expectedOutput()).containsEntry("decision", "APPROVE");
                });

        // Saving the same name updates in place rather than creating a duplicate dataset.
        datasetRepository.save(new GoldenDataset("invoice-regressions", "Updated", List.of(), Map.of()));
        assertThat(datasetRepository.list()).hasSize(1);
        assertThat(datasetRepository.find("invoice-regressions")).get()
                .satisfies(stored -> assertThat(stored.description()).isEqualTo("Updated"));
    }

    @Test
    void persistsPromptVersionsWithH2() {
        promptVersionStore.save(new PromptTemplate(
                "prompt",
                "agent",
                "1.0.0",
                PromptStatus.ACTIVE,
                "system",
                "user",
                Map.of("owner", "platform"),
                null,
                null
        ));

        assertThat(promptVersionStore.findActiveForAgent("agent"))
                .isPresent()
                .get()
                .satisfies(prompt -> {
                    assertThat(prompt.version()).isEqualTo("1.0.0");
                    assertThat(prompt.metadata()).containsEntry("owner", "platform");
                });
    }

    @Test
    void persistsToolAuditEventsWithH2() {
        toolAuditService.record(new ToolAuditEvent(
                "exec-tool",
                "tenant",
                "actor",
                "lookup",
                ToolAuditStatus.SUCCEEDED,
                "done",
                Map.of("count", 1),
                Instant.now()
        ));

        assertThat(toolAuditRepository.findByExecutionIdOrderByOccurredAtAsc("exec-tool"))
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getToolName()).isEqualTo("lookup");
                    assertThat(event.getStatus()).isEqualTo(ToolAuditStatus.SUCCEEDED);
                    assertThat(event.getMetadataJson()).contains("\"count\":1");
                });
    }

    @Test
    void persistsToolCallLifecycleWithH2() {
        Instant startedAt = Instant.now();
        toolCallRecorder.record(new ToolCallRecord(
                "tool-call-h2",
                "exec-tool-call-h2",
                "tenant",
                "actor",
                "corr",
                "lookup",
                ToolRiskLevel.READ_ONLY,
                Set.of(ToolSideEffect.READ_EXTERNAL),
                ToolCallStatus.STARTED,
                Map.of("query", "hello"),
                null,
                null,
                null,
                startedAt,
                null,
                null
        ));
        toolCallRecorder.record(new ToolCallRecord(
                "tool-call-h2",
                "exec-tool-call-h2",
                "tenant",
                "actor",
                "corr",
                "lookup",
                ToolRiskLevel.READ_ONLY,
                Set.of(ToolSideEffect.READ_EXTERNAL),
                ToolCallStatus.SUCCEEDED,
                Map.of("query", "hello"),
                "{count=1}",
                null,
                null,
                startedAt,
                startedAt.plusMillis(25),
                25L
        ));

        assertThat(toolCallRepository.findByExecutionIdOrderByStartedAtAsc("exec-tool-call-h2"))
                .hasSize(1)
                .first()
                .satisfies(call -> {
                    assertThat(call.getCallId()).isEqualTo("tool-call-h2");
                    assertThat(call.getToolName()).isEqualTo("lookup");
                    assertThat(call.getToolRiskLevel()).isEqualTo(ToolRiskLevel.READ_ONLY);
                    assertThat(call.getSideEffects()).isEqualTo("READ_EXTERNAL");
                    assertThat(call.getStatus()).isEqualTo(ToolCallStatus.SUCCEEDED);
                    assertThat(call.getInputJson()).contains("\"query\":\"hello\"");
                    assertThat(call.getOutputSummary()).isEqualTo("{count=1}");
                    assertThat(call.getLatencyMs()).isEqualTo(25L);
                });
    }
    @Test
    void persistsExecutionLogEventsWithH2() {
        AgentExecutionContext context = new AgentExecutionContext("exec-log-h2", "tenant", "actor", "corr", Instant.now(), Map.of());
        AgentDescriptor descriptor = new AgentDescriptor("agent", "Agent", "1.0.0", "Test", AgentRiskLevel.LOW, Set.of());

        executionLogListener.onEvent(new AgentPipelineEvent(
                AgentPipelineStep.EXECUTION_STARTED,
                context,
                descriptor,
                "Execution started",
                Map.of("source", "h2-test"),
                Instant.now()
        ));

        assertThat(executionLogRepository.findByExecutionIdOrderByOccurredAtAsc("exec-log-h2"))
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getAgentId()).isEqualTo("agent");
                    assertThat(event.getAgentVersion()).isEqualTo("1.0.0");
                    assertThat(event.getTenantId()).isEqualTo("tenant");
                    assertThat(event.getActorId()).isEqualTo("actor");
                    assertThat(event.getCorrelationId()).isEqualTo("corr");
                    assertThat(event.getStep()).isEqualTo(AgentPipelineStep.EXECUTION_STARTED);
                    assertThat(event.getLogLevel()).isEqualTo("INFO");
                    assertThat(event.getMessage()).isEqualTo("Execution started");
                    assertThat(event.getAttributesJson()).contains("\"source\":\"h2-test\"");
                });
    }


    @Test
    void persistsModelUsageLifecycleWithH2() {
        Instant startedAt = Instant.now();
        modelUsageRecorder.record(new ModelUsageRecord(
                "model-usage-h2",
                "exec-model-usage-h2",
                "tenant",
                "actor",
                "corr",
                "agent",
                "1.0.0",
                "openai",
                "gpt-test",
                "prompt-v1",
                ModelUsageStatus.STARTED,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("phase", "started"),
                startedAt,
                null
        ));
        modelUsageRecorder.record(new ModelUsageRecord(
                "model-usage-h2",
                "exec-model-usage-h2",
                "tenant",
                "actor",
                "corr",
                "agent",
                "1.0.0",
                "openai",
                "gpt-test",
                "prompt-v1",
                ModelUsageStatus.SUCCEEDED,
                10L,
                15L,
                25L,
                new BigDecimal("0.000500"),
                42L,
                null,
                Map.of("phase", "completed"),
                startedAt,
                startedAt.plusMillis(42)
        ));

        assertThat(modelUsageRepository.findByExecutionIdOrderByStartedAtAsc("exec-model-usage-h2"))
                .hasSize(1)
                .first()
                .satisfies(usage -> {
                    assertThat(usage.getUsageId()).isEqualTo("model-usage-h2");
                    assertThat(usage.getAgentId()).isEqualTo("agent");
                    assertThat(usage.getAgentVersion()).isEqualTo("1.0.0");
                    assertThat(usage.getTenantId()).isEqualTo("tenant");
                    assertThat(usage.getActorId()).isEqualTo("actor");
                    assertThat(usage.getCorrelationId()).isEqualTo("corr");
                    assertThat(usage.getModelProvider()).isEqualTo("openai");
                    assertThat(usage.getModelName()).isEqualTo("gpt-test");
                    assertThat(usage.getPromptVersion()).isEqualTo("prompt-v1");
                    assertThat(usage.getStatus()).isEqualTo(ModelUsageStatus.SUCCEEDED);
                    assertThat(usage.getInputTokens()).isEqualTo(10L);
                    assertThat(usage.getOutputTokens()).isEqualTo(15L);
                    assertThat(usage.getTotalTokens()).isEqualTo(25L);
                    assertThat(usage.getEstimatedCostUsd()).isEqualByComparingTo("0.000500");
                    assertThat(usage.getLatencyMs()).isEqualTo(42L);
                    assertThat(usage.getMetadataJson()).contains("\"phase\":\"completed\"");
                    assertThat(usage.getCompletedAt()).isNotNull();
                });
    }

    @Test
    void persistsApprovalLifecycleWithH2() {
        AgentApprovalRequest pending = approvalStore.create(new AgentApprovalRequest(
                "approval-h2",
                "exec-approval-h2",
                "agent",
                "1.0.0",
                "tenant",
                "actor",
                AgentApprovalStatus.PENDING,
                "approval required",
                Map.of("callbackUrl", "https://callback.test"),
                Instant.now(),
                Instant.now().plusSeconds(60),
                null,
                null
        ));

        assertThat(pending.status()).isEqualTo(AgentApprovalStatus.PENDING);
        assertThat(approvalStore.findPendingByExecutionId("exec-approval-h2"))
                .isPresent()
                .get()
                .satisfies(approval -> assertThat(approval.metadata()).containsEntry("callbackUrl", "https://callback.test"));

        AgentApprovalRequest approved = approvalStore.approve("approval-h2", "approver");

        assertThat(approved.status()).isEqualTo(AgentApprovalStatus.APPROVED);
        assertThat(approved.decidedBy()).isEqualTo("approver");
        assertThat(approved.decidedAt()).isNotNull();
        assertThat(approvalStore.findPendingByExecutionId("exec-approval-h2")).isEmpty();
    }

    @Test
    void persistsPendingCommandsWithTypedInputForRestartSafeResume() {
        AgentExecutionContext context = new AgentExecutionContext("exec-pending-h2", "tenant", "actor", "corr", Instant.now(), Map.of("source", "test"));
        AgentRunCommand command = new AgentRunCommand(
                "agent",
                "1.0.0",
                new GenericAgentInput(Map.of("message", "persist-me")),
                context,
                new AgentRunOptions(null, 3, null, null, null, null, null, null),
                "https://callback.test"
        );

        pendingCommandStore.save(command);

        assertThat(pendingCommandStore.findByExecutionId("exec-pending-h2"))
                .isPresent()
                .get()
                .satisfies(saved -> {
                    assertThat(saved.agentId()).isEqualTo("agent");
                    assertThat(saved.context().tenantId()).isEqualTo("tenant");
                    assertThat(saved.context().attributes()).containsEntry("source", "test");
                    assertThat(saved.callbackUrl()).isEqualTo("https://callback.test");
                    assertThat(saved.input()).isInstanceOf(GenericAgentInput.class);
                    assertThat(((GenericAgentInput) saved.input()).values()).containsEntry("message", "persist-me");
                });

        pendingCommandStore.delete("exec-pending-h2");
        assertThat(pendingCommandStore.findByExecutionId("exec-pending-h2")).isEmpty();
    }


    @Test
    void persistsAsyncQueueCommandsWithH2() {
        JpaAgentAsyncQueue asyncQueue = new JpaAgentAsyncQueue(asyncQueueRepository, objectMapper);
        AgentExecutionContext context = new AgentExecutionContext("exec-queue-h2", "tenant", "actor", "corr", Instant.now(), Map.of("source", "queue-test"));
        AgentRunCommand command = new AgentRunCommand(
                "agent",
                "1.0.0",
                new GenericAgentInput(Map.of("message", "queue-me")),
                context,
                new AgentRunOptions(null, 3, null, null, null, null, null, null),
                "https://callback.test/queue"
        );

        asyncQueue.enqueue(command);

        assertThat(asyncQueue.size()).isEqualTo(1);
        assertThat(asyncQueue.poll())
                .isPresent()
                .get()
                .satisfies(saved -> {
                    assertThat(saved.agentId()).isEqualTo("agent");
                    assertThat(saved.context().tenantId()).isEqualTo("tenant");
                    assertThat(saved.context().attributes()).containsEntry("source", "queue-test");
                    assertThat(saved.callbackUrl()).isEqualTo("https://callback.test/queue");
                    assertThat(saved.input()).isInstanceOf(GenericAgentInput.class);
                    assertThat(((GenericAgentInput) saved.input()).values()).containsEntry("message", "queue-me");
                });
        assertThat(asyncQueue.size()).isZero();
        assertThat(asyncQueue.poll()).isEmpty();
    }
    @Test
    void persistsEvaluationResultsWithH2() {
        var stored = evaluationResultStore.save(new EvaluationResult(
                "dataset-h2",
                2,
                1,
                1,
                List.of(
                        new EvaluationCaseResult("case-1", EvaluationStatus.PASSED, "exec-1", "ok"),
                        new EvaluationCaseResult("case-2", EvaluationStatus.FAILED, "exec-2", "mismatch")
                ),
                Instant.now()
        ));

        assertThat(evaluationResultStore.find(stored.evaluationId()))
                .isPresent()
                .get()
                .satisfies(result -> {
                    assertThat(result.datasetName()).isEqualTo("dataset-h2");
                    assertThat(result.status()).isEqualTo(EvaluationStatus.FAILED);
                    assertThat(result.casesJson()).contains("case-1", "case-2");
                });
        assertThat(evaluationResultStore.listRecent(5))
                .extracting(result -> result.evaluationId())
                .contains(stored.evaluationId());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("ai.qubere.agent.persistence")
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}


