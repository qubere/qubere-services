package ai.qubere.agent.app;

import ai.qubere.agent.async.AgentApprovalStatus;
import ai.qubere.agent.persistence.AgentApprovalRequestEntity;
import ai.qubere.agent.persistence.AgentApprovalRequestRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:agentadminapproval;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.ai.model.chat=none",
                "agent-platform.ai.spring.enabled=false",
                "agent-platform.admin.enabled=true",
                "agent-platform.admin.token=test-admin-token",
                "agent-platform.prompts.seed-enabled=false"
        }
)
class AgentAdminApprovalE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private AgentApprovalRequestRepository approvalRepository;

    @Test
    void listsDetailsAndExpiresDueApprovals() throws Exception {
        approvalRepository.save(approval("approval-open", "tenant-a", AgentApprovalStatus.PENDING, Instant.now().plusSeconds(3600)));
        approvalRepository.save(approval("approval-expired", "tenant-a", AgentApprovalStatus.PENDING, Instant.now().minusSeconds(60)));
        approvalRepository.save(approval("approval-other-tenant", "tenant-b", AgentApprovalStatus.PENDING, Instant.now().plusSeconds(3600)));

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> list = client.send(request("GET", "/api/agents/admin/approvals?status=PENDING&tenantId=tenant-a", null), HttpResponse.BodyHandlers.ofString());
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("approval-open", "approval-expired");
        assertThat(list.body()).doesNotContain("approval-other-tenant");

        HttpResponse<String> detail = client.send(request("GET", "/api/agents/admin/approvals/approval-open", null), HttpResponse.BodyHandlers.ofString());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("approval-open", "execution-approval-open");

        HttpResponse<String> expiry = client.send(request("POST", "/api/agents/admin/approvals/expire", null), HttpResponse.BodyHandlers.ofString());
        assertThat(expiry.statusCode()).isEqualTo(200);
        assertThat(expiry.body()).contains("\"expiredCount\":1");

        assertThat(approvalRepository.findById("approval-expired").orElseThrow().getStatus()).isEqualTo(AgentApprovalStatus.EXPIRED);
        assertThat(approvalRepository.findById("approval-open").orElseThrow().getStatus()).isEqualTo(AgentApprovalStatus.PENDING);
    }

    private AgentApprovalRequestEntity approval(String approvalId, String tenantId, AgentApprovalStatus status, Instant expiresAt) {
        AgentApprovalRequestEntity entity = new AgentApprovalRequestEntity();
        entity.setApprovalId(approvalId);
        entity.setExecutionId("execution-" + approvalId);
        entity.setAgentId("generic.echo-analysis");
        entity.setAgentVersion("0.1.0");
        entity.setTenantId(tenantId);
        entity.setRequestedBy("actor-test");
        entity.setStatus(status);
        entity.setReason("approval required");
        entity.setMetadataJson("{}");
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    private HttpRequest request(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("X-Agent-Admin-Token", "test-admin-token");
        if (body == null) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
