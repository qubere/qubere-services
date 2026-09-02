package ai.qubere.agent.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the standardized API error mapping added as part of production-readiness Tier 1:
 * unknown resources return 404, malformed request bodies return 400, and conflicting approval
 * decisions return 409 instead of falling through to a generic 500.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:agentapierrormapping;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.ai.model.chat=none",
                "agent-platform.ai.spring.enabled=false"
        }
)
class AgentApiErrorMappingE2ETest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void approvingUnknownApprovalIdReturnsNotFound() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/approvals/does-not-exist/approve"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void malformedJsonBodyReturnsBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/generic.echo-analysis/runs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not-valid-json"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    @Test
    void rejectingAlreadyApprovedRequestReturnsConflict() throws Exception {
        HttpRequest runRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/generic.echo-analysis/runs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"agentVersion":"0.1.0","async":true,"input":{"message":"conflict test"},"options":{"requireHumanApproval":true}}
                        """))
                .build();
        HttpResponse<String> runResponse = client.send(runRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(runResponse.statusCode()).isEqualTo(200);

        String approvalId = extractApprovalId(runResponse.body());
        assertThat(approvalId).isNotNull();

        HttpResponse<String> approveResponse = client.send(approvalDecision(approvalId, "approve"), HttpResponse.BodyHandlers.ofString());
        assertThat(approveResponse.statusCode()).isEqualTo(200);

        HttpResponse<String> conflictingReject = client.send(approvalDecision(approvalId, "reject"), HttpResponse.BodyHandlers.ofString());
        assertThat(conflictingReject.statusCode()).isEqualTo(409);
        assertThat(conflictingReject.body()).contains("\"code\":\"CONFLICT\"");
    }

    private HttpRequest approvalDecision(String approvalId, String action) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/approvals/" + approvalId + "/" + action))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private String extractApprovalId(String body) {
        int index = body.indexOf("\"approvalId\":\"");
        if (index < 0) {
            return null;
        }
        int start = index + "\"approvalId\":\"".length();
        int end = body.indexOf('"', start);
        return end < 0 ? null : body.substring(start, end);
    }
}
