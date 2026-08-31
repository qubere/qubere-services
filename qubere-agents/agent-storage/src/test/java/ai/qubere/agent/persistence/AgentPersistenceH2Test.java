package ai.qubere.agent.persistence;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
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
import ai.qubere.agent.prompts.PromptStatus;
import ai.qubere.agent.prompts.PromptTemplate;
import ai.qubere.agent.tools.ToolAuditEvent;
import ai.qubere.agent.tools.ToolAuditStatus;

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
    private AgentToolAuditEventRepository toolAuditRepository;

    @Autowired
    private JpaEvaluationResultStore evaluationResultStore;

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
