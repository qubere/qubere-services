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
 * Strict authorization mode must fail closed by default: inbound X-Tenant-Id/X-Actor-Id/
 * X-Agent-Permissions headers must never be trusted unless the deployed application explicitly
 * opts in with {@code agent-platform.security.trust-inbound-headers=true}, or supplies a real
 * {@code AgentCallerIdentityResolver} bean backed by a verified identity source. This is the
 * fail-closed behavior fixed as part of production-readiness Tier 0.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:agentauthwebstrict;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.ai.model.chat=none",
                "agent-platform.ai.spring.enabled=false",
                "agent-platform.security.authorization-mode=strict",
                "agent-platform.security.required-run-permissions[0]=agents.run"
        }
)
class AgentAuthorizationStrictModeE2ETest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void strictAuthorizationRejectsMissingTenantActorAndIgnoresSpoofedHeaders() throws Exception {
        String body = """
                {"agentVersion":"0.1.0","input":{"message":"auth test"}}
                """;

        HttpResponse<String> rejected = client.send(baseRequest(body).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(rejected.body()).contains("\"code\":\"AUTHORIZATION_DENIED\"");

        // Even with headers set, strict mode must not trust them by default: the caller identity
        // resolver ignores inbound headers unless explicitly configured otherwise.
        HttpResponse<String> stillRejected = client.send(baseRequest(body)
                .header("X-Tenant-Id", "tenant-test-1")
                .header("X-Actor-Id", "actor-test-1")
                .header("X-Agent-Permissions", "agents.run")
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(stillRejected.statusCode()).isEqualTo(403);
        assertThat(stillRejected.body()).contains("\"code\":\"AUTHORIZATION_DENIED\"");
    }

    private HttpRequest.Builder baseRequest(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/generic.echo-analysis/runs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }
}
