package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.runtime.AgentWorkflowContext;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRemoteAgentClientTest {

    private com.sun.net.httpserver.HttpServer server;
    private final Map<String, String> receivedHeaders = new HashMap<>();
    private volatile String responseBody = """
            {"executionId":"remote-exec-1","status":"SUCCEEDED","result":{"summary":"done"}}
            """;

    @BeforeEach
    void startServer() throws Exception {
        server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/api/agents", exchange -> {
            exchange.getRequestHeaders().forEach((key, values) -> {
                if (!values.isEmpty()) {
                    receivedHeaders.put(key, values.get(0));
                }
            });
            byte[] body = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void propagatesWorkflowAndCorrelationHeadersToRemoteService() {
        HttpRemoteAgentClient client = client();
        AgentExecutionContext caller = callerContext("exec-orchestrator", "wf-123");

        RemoteAgentRunResult result = client.run("invoice.review", null, Map.of("invoiceId", "inv-1"), caller);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.executionId()).isEqualTo("remote-exec-1");
        assertThat(result.output()).containsEntry("summary", "done");

        assertThat(headerValue(AgentPropagationHeaders.CORRELATION_ID)).isEqualTo("corr-1");
        assertThat(headerValue(AgentPropagationHeaders.WORKFLOW_ID)).isEqualTo("wf-123");
        assertThat(headerValue(AgentPropagationHeaders.PARENT_EXECUTION_ID)).isEqualTo("exec-orchestrator");
    }

    @Test
    void fallsBackToCallerExecutionIdAsWorkflowIdWhenCallerIsWorkflowRoot() {
        HttpRemoteAgentClient client = client();
        AgentExecutionContext caller = callerContext("exec-root", null);

        client.run("invoice.review", null, Map.of(), caller);

        assertThat(headerValue(AgentPropagationHeaders.WORKFLOW_ID)).isEqualTo("exec-root");
        assertThat(headerValue(AgentPropagationHeaders.PARENT_EXECUTION_ID)).isEqualTo("exec-root");
    }

    @Test
    void surfacesRemoteApprovalPauseInsteadOfTreatingItAsSuccess() {
        responseBody = """
                {"executionId":"remote-exec-2","status":"WAITING_FOR_APPROVAL","approvalId":"appr-9"}
                """;
        HttpRemoteAgentClient client = client();

        RemoteAgentRunResult result = client.run("invoice.review", null, Map.of(), callerContext("exec-root", null));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.awaitingApproval()).isTrue();
        assertThat(result.approvalId()).isEqualTo("appr-9");
    }

    @Test
    void requiresConfiguredBaseUrl() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getOrchestration().getRemote().setBaseUrl(null);
        HttpRemoteAgentClient client = new HttpRemoteAgentClient(RestClient.builder(), properties);

        assertThatThrownBy(() -> client.run("invoice.review", null, Map.of(), callerContext("exec-root", null)))
                .isInstanceOf(ai.qubere.agent.core.AgentExecutionException.class);
    }

    @Test
    void requiresAgentId() {
        assertThatThrownBy(() -> client().run(null, null, Map.of(), callerContext("exec-root", null)))
                .isInstanceOf(ai.qubere.agent.core.AgentExecutionException.class);
    }

    private HttpRemoteAgentClient client() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getOrchestration().getRemote().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        return new HttpRemoteAgentClient(RestClient.builder(), properties);
    }

    private AgentExecutionContext callerContext(String executionId, String workflowId) {
        Map<String, Object> attributes = new HashMap<>();
        if (workflowId != null) {
            attributes.put(AgentWorkflowContext.WORKFLOW_ID, workflowId);
        }
        return new AgentExecutionContext(executionId, "tenant-1", "actor-1", "corr-1", Instant.now(), attributes);
    }

    /** HTTP header names are case-insensitive and the JDK server normalizes their casing. */
    private String headerValue(String name) {
        return receivedHeaders.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
