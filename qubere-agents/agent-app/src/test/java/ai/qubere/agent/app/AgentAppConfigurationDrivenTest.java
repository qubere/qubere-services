package ai.qubere.agent.app;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.async.AgentAsyncRunHandle;
import ai.qubere.agent.async.AgentAsyncRuntimeService;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.app.api.AgentAdminController;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRuntimeService;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
        "agent-platform.definitions[generic.echo-analysis].timeout-seconds=44"
})
class AgentAppConfigurationDrivenTest {

    @Autowired
    private AgentRuntimeService runtimeService;

    @Autowired
    private AgentAsyncRuntimeService asyncRuntimeService;

    @Autowired
    private AgentExecutionStore executionStore;

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
}
