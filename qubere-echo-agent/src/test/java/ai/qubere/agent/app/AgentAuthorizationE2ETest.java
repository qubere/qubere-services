package ai.qubere.agent.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:agentauthweb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
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
class AgentAuthorizationE2ETest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void strictAuthorizationRejectsMissingTenantActorAndAllowsHeaderPermissions() throws Exception {
        String body = """
                {"agentVersion":"0.1.0","input":{"message":"auth test"}}
                """;

        HttpResponse<String> rejected = client.send(baseRequest(body).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(rejected.body()).contains("\"code\":\"AUTHORIZATION_DENIED\"");

        HttpResponse<String> allowed = client.send(baseRequest(body)
                .header("X-Tenant-Id", "tenant-test-1")
                .header("X-Actor-Id", "actor-test-1")
                .header("X-Agent-Permissions", "agents.run")
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.body()).contains("\"status\":\"SUCCEEDED\"");
    }

    private HttpRequest.Builder baseRequest(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/generic.echo-analysis/runs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }
}