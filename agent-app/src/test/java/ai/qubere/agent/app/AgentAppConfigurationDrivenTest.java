package ai.qubere.agent.app;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.ai.AgentAiClient;
import ai.qubere.agent.ai.AgentAiRequestMetadata;
import ai.qubere.agent.ai.AgentPrompt;
import ai.qubere.agent.ai.ModelUsageRecord;
import ai.qubere.agent.ai.ModelUsageRecorder;
import ai.qubere.agent.ai.ModelUsageStatus;
import ai.qubere.agent.async.AgentAsyncRunHandle;
import ai.qubere.agent.async.AgentAsyncRuntimeService;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.app.sample.AiBackedAnalysisAgent;
import ai.qubere.agent.app.api.AgentAdminController;
import ai.qubere.agent.persistence.AgentExecutionLogRepository;
import ai.qubere.agent.persistence.AgentModelUsageRepository;
import ai.qubere.agent.persistence.AgentToolAuditEventRepository;
import ai.qubere.agent.persistence.AgentToolCallRepository;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentPipelineStep;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.tools.ToolAuditStatus;
import ai.qubere.agent.tools.ToolCallStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:agentapp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.ai.model.chat=none",
        "agent-platform.ai.spring.enabled=false",
        "agent-platform.ai.default-provider=test-provider",
        "agent-platform.ai.default-model=test-model",
        "agent-platform.memory.max-results=7",
        "agent-platform.tools.max-tool-calls=9",
        "agent-platform.definitions[generic.echo-analysis].model-provider=configured-provider",
        "agent-platform.definitions[generic.echo-analysis].model-name=configured-model",
        "agent-platform.definitions[generic.echo-analysis].prompt-version=2.0.0",
        "agent-platform.definitions[generic.echo-analysis].max-tool-calls=3",
        "agent-platform.definitions[generic.echo-analysis].timeout-seconds=44",
        "agent-platform.definitions[generic.tool-echo-analysis].model-provider=configured-provider",
        "agent-platform.definitions[generic.tool-echo-analysis].model-name=configured-model",
        "agent-platform.definitions[generic.tool-echo-analysis].prompt-version=2.0.0",
        "agent-platform.definitions[generic.tool-echo-analysis].allowed-tools[0]=echo.lookup",
        "agent-platform.definitions[generic.tool-echo-analysis].max-tool-calls=3"
})
class AgentAppConfigurationDrivenTest {

    @Autowired
    private AgentRuntimeService runtimeService;

    @Autowired
    private AgentAsyncRuntimeService asyncRuntimeService;

    @Autowired
    private AgentExecutionStore executionStore;

    @Autowired
    private AgentExecutionLogRepository executionLogRepository;

    @Autowired
    private AgentToolCallRepository toolCallRepository;

    @Autowired
    private AgentToolAuditEventRepository toolAuditEventRepository;

    @Autowired
    private AgentModelUsageRepository modelUsageRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void sampleAgentReceivesResolvedPolicyFromConfiguration() {
        AgentOutput output = runtimeService.run(
                "generic.echo-analysis",
                new GenericAgentInput(Map.of("message", "config-check")),
                new AgentExecutionContext("exec-config", "tenant", "actor", "corr", null, Map.of()),
                null
        );

        @SuppressWarnings("unchecked")
        AgentResult<Map<String, Object>> result = (AgentResult<Map<String, Object>>) output;
        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) result.value().get("policy");

        assertThat(policy)
                .containsEntry("modelProvider", "configured-provider")
                .containsEntry("modelName", "configured-model")
                .containsEntry("promptVersion", "2.0.0")
                .containsEntry("maxToolCalls", 3)
                .containsEntry("timeoutSeconds", 44);

        assertThat(executionLogRepository.findByExecutionIdOrderByOccurredAtAsc("exec-config"))
                .extracting(log -> log.getStep())
                .contains(
                        AgentPipelineStep.AGENT_RESOLUTION,
                        AgentPipelineStep.POLICY_RESOLUTION,
                        AgentPipelineStep.EXECUTION_STARTED,
                        AgentPipelineStep.EXECUTION_COMPLETED
                );
    }


    @Test
    void toolBackedSampleAgentRecordsToolCallAndAuditRows() {
        AgentOutput output = runtimeService.run(
                "generic.tool-echo-analysis",
                new GenericAgentInput(Map.of("message", "Tool Test")),
                new AgentExecutionContext("exec-tool-backed-app", "tenant", "actor", "corr", null, Map.of()),
                null
        );

        @SuppressWarnings("unchecked")
        AgentResult<Map<String, Object>> result = (AgentResult<Map<String, Object>>) output;
        @SuppressWarnings("unchecked")
        Map<String, Object> tool = (Map<String, Object>) result.value().get("tool");
        @SuppressWarnings("unchecked")
        Map<String, Object> toolResult = (Map<String, Object>) tool.get("result");

        assertThat(tool)
                .containsEntry("name", "echo.lookup")
                .containsEntry("success", true);
        assertThat(toolResult)
                .containsEntry("normalizedMessage", "tool test")
                .containsEntry("messageLength", 9);

        assertThat(toolCallRepository.findByExecutionIdOrderByStartedAtAsc("exec-tool-backed-app"))
                .hasSize(1)
                .first()
                .satisfies(call -> {
                    assertThat(call.getToolName()).isEqualTo("echo.lookup");
                    assertThat(call.getStatus()).isEqualTo(ToolCallStatus.SUCCEEDED);
                    assertThat(call.getInputJson()).contains("\"message\":\"Tool Test\"");
                    assertThat(call.getOutputSummary()).isEqualTo("Tool result logging disabled");
                    assertThat(call.getLatencyMs()).isNotNull();
                });

        assertThat(toolAuditEventRepository.findByExecutionIdOrderByOccurredAtAsc("exec-tool-backed-app"))
                .extracting(event -> event.getStatus())
                .contains(ToolAuditStatus.STARTED, ToolAuditStatus.SUCCEEDED);
    }


    @Test
    void aiBackedSampleAgentUsesStructuredAiClientAndRecordsModelUsage() {
        AgentOutput output = runtimeService.run(
                "generic.ai-analysis",
                new GenericAgentInput(Map.of("message", "AI Test")),
                new AgentExecutionContext("exec-ai-backed-app", "tenant", "actor", "corr", null, Map.of()),
                null
        );

        @SuppressWarnings("unchecked")
        AgentResult<Map<String, Object>> result = (AgentResult<Map<String, Object>>) output;
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = (Map<String, Object>) result.value().get("analysis");

        assertThat(analysis)
                .containsEntry("summary", "Fake structured analysis for AI Test")
                .containsEntry("sentiment", "NEUTRAL")
                .containsEntry("recommendedAction", "AI_ANALYSIS_ACCEPTED")
                .containsEntry("confidence", 0.91d);

        assertThat(modelUsageRepository.findByExecutionIdOrderByStartedAtAsc("exec-ai-backed-app"))
                .hasSize(1)
                .first()
                .satisfies(usage -> {
                    assertThat(usage.getAgentId()).isEqualTo("generic.ai-analysis");
                    assertThat(usage.getAgentVersion()).isEqualTo("0.1.0");
                    assertThat(usage.getModelProvider()).isEqualTo("openai");
                    assertThat(usage.getModelName()).isEqualTo("gpt-4.1-mini");
                    assertThat(usage.getPromptVersion()).isEqualTo("0.1.0");
                    assertThat(usage.getStatus()).isEqualTo(ModelUsageStatus.SUCCEEDED);
                    assertThat(usage.getInputTokens()).isEqualTo(11L);
                    assertThat(usage.getOutputTokens()).isEqualTo(13L);
                    assertThat(usage.getTotalTokens()).isEqualTo(24L);
                    assertThat(usage.getMetadataJson()).contains("\"fakeAiClient\":true");
                });
    }

    @Test
    void sampleAgentSupportsApprovalBackedAsyncExecution() {
        AgentAsyncRunHandle waiting = asyncRuntimeService.submit(
                "generic.echo-analysis",
                "0.1.0",
                new GenericAgentInput(Map.of("message", "async-config-check")),
                new AgentExecutionContext("exec-app-async", "tenant", "actor", "corr", null, Map.of()),
                new AgentRunOptions(null, null, null, null, null, true, null, null),
                null
        );

        assertThat(waiting.status()).isEqualTo(AgentRunStatus.WAITING_FOR_APPROVAL);
        assertThat(waiting.approvalId()).isNotBlank();
        assertThat(executionStore.findByExecutionId("exec-app-async"))
                .isPresent()
                .get()
                .extracting(record -> record.status())
                .isEqualTo(AgentRunStatus.WAITING_FOR_APPROVAL);

        AgentAsyncRunHandle resumed = asyncRuntimeService.resumeApproved(waiting.approvalId(), "approver");

        assertThat(resumed.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(asyncRuntimeService.processNext()).isPresent();
        assertThat(executionStore.findByExecutionId("exec-app-async"))
                .isPresent()
                .get()
                .extracting(record -> record.status())
                .isEqualTo(AgentRunStatus.SUCCEEDED);
    }

    @Test
    void adminControllerIsDisabledByDefault() {
        assertThat(applicationContext.getBeansOfType(AgentAdminController.class)).isEmpty();
    }
    @TestConfiguration
    static class FakeAiClientConfiguration {

        @Bean
        AgentAiClient agentAiClient(ModelUsageRecorder modelUsageRecorder) {
            return new AgentAiClient() {
                @Override
                public <T> T generate(AgentPrompt prompt, Class<T> responseType) {
                    return generate(prompt, responseType, AgentAiRequestMetadata.empty());
                }

                @Override
                public <T> T generate(AgentPrompt prompt, Class<T> responseType, AgentAiRequestMetadata metadata) {
                    String usageId = UUID.randomUUID().toString();
                    Instant startedAt = Instant.now();
                    modelUsageRecorder.record(new ModelUsageRecord(
                            usageId,
                            metadata.executionId(),
                            metadata.tenantId(),
                            metadata.actorId(),
                            metadata.correlationId(),
                            metadata.agentId(),
                            metadata.agentVersion(),
                            metadata.modelProvider(),
                            metadata.modelName(),
                            metadata.promptVersion(),
                            ModelUsageStatus.STARTED,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            Map.of("fakeAiClient", true),
                            startedAt,
                            null
                    ));
                    modelUsageRecorder.record(new ModelUsageRecord(
                            usageId,
                            metadata.executionId(),
                            metadata.tenantId(),
                            metadata.actorId(),
                            metadata.correlationId(),
                            metadata.agentId(),
                            metadata.agentVersion(),
                            metadata.modelProvider(),
                            metadata.modelName(),
                            metadata.promptVersion(),
                            ModelUsageStatus.SUCCEEDED,
                            11L,
                            13L,
                            24L,
                            null,
                            1L,
                            null,
                            Map.of("fakeAiClient", true),
                            startedAt,
                            startedAt.plusMillis(1)
                    ));
                    Object response = new AiBackedAnalysisAgent.AiAnalysisResponse(
                            "Fake structured analysis for " + prompt.variables().getOrDefault("message", ""),
                            "NEUTRAL",
                            "AI_ANALYSIS_ACCEPTED",
                            0.91d
                    );
                    return responseType.cast(response);
                }
            };
        }
    }
}
