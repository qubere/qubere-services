package ai.qubere.agent.app;

import ai.qubere.agent.orchestration.AgentPropagationHeaders;
import ai.qubere.agent.orchestration.AgentWorkflowService;
import ai.qubere.agent.orchestration.AgentWorkflowStatus;
import ai.qubere.agent.orchestration.AgentWorkflowSummary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves cross-service workflow linkage closes the loop: when an upstream agent service sends
 * workflow propagation headers, the receiving service stitches the resulting execution into that
 * workflow so it can be rolled up alongside the caller's own executions.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:agentworkflowpropagation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.ai.model.chat=none",
                "agent-platform.ai.spring.enabled=false"
        }
)
class AgentWorkflowPropagationE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private AgentWorkflowService workflowService;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void inboundWorkflowHeadersLinkRemoteExecutionIntoCallerWorkflow() throws Exception {
        String workflowId = "wf-cross-service-1";
        String parentExecutionId = "exec-upstream-orchestrator";

        HttpResponse<String> first = client.send(runRequest(workflowId, parentExecutionId), HttpResponse.BodyHandlers.ofString());
        assertThat(first.statusCode()).isEqualTo(200);

        HttpResponse<String> second = client.send(runRequest(workflowId, parentExecutionId), HttpResponse.BodyHandlers.ofString());
        assertThat(second.statusCode()).isEqualTo(200);

        AgentWorkflowSummary summary = workflowService.summarize(workflowId);

        assertThat(summary.totalExecutions()).isEqualTo(2);
        assertThat(summary.status()).isEqualTo(AgentWorkflowStatus.SUCCEEDED);
        assertThat(summary.executions())
                .allSatisfy(execution -> {
                    assertThat(execution.workflowId()).isEqualTo(workflowId);
                    assertThat(execution.parentExecutionId()).isEqualTo(parentExecutionId);
                    // Every execution here was invoked by an upstream orchestrator, so none of
                    // them is the workflow root within this service.
                    assertThat(execution.isWorkflowRoot()).isFalse();
                });
    }

    @Test
    void runsWithoutWorkflowHeadersRemainUnlinked() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/agents/generic.echo-analysis/runs"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"agentVersion":"0.1.0","input":{"message":"standalone"}}
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        // A standalone run belongs to no workflow, so summarizing an unrelated id finds nothing.
        assertThat(workflowService.summarize("wf-does-not-exist").status()).isEqualTo(AgentWorkflowStatus.UNKNOWN);
    }

    private HttpRequest runRequest(String workflowId, String parentExecutionId) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/generic.echo-analysis/runs"))
                .header("Content-Type", "application/json")
                .header(AgentPropagationHeaders.WORKFLOW_ID, workflowId)
                .header(AgentPropagationHeaders.PARENT_EXECUTION_ID, parentExecutionId)
                .header(AgentPropagationHeaders.CORRELATION_ID, "corr-cross-service")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"agentVersion":"0.1.0","input":{"message":"delegated"}}
                        """))
                .build();
    }
}
