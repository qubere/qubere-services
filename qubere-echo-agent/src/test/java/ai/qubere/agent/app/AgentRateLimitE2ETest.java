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
 * Proves the governance rate limiter actually rejects excess HTTP runs with 429, not just at the
 * unit level. This is the production-readiness Tier 1 validation for
 * {@code agent-platform.governance.max-runs-per-actor-per-minute}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:agentratelimitweb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.ai.model.chat=none",
                "agent-platform.ai.spring.enabled=false",
                "agent-platform.governance.max-runs-per-actor-per-minute=1"
        }
)
class AgentRateLimitE2ETest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void secondRunWithinTheSameMinuteForTheSameActorIsRateLimited() throws Exception {
        HttpResponse<String> first = client.send(runRequest(), HttpResponse.BodyHandlers.ofString());
        assertThat(first.statusCode()).isEqualTo(200);

        HttpResponse<String> second = client.send(runRequest(), HttpResponse.BodyHandlers.ofString());
        assertThat(second.statusCode()).isEqualTo(429);
        assertThat(second.body()).contains("\"code\":\"GOVERNANCE_LIMIT_EXCEEDED\"");
    }

    private HttpRequest runRequest() {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/generic.echo-analysis/runs"))
                .header("Content-Type", "application/json")
                .header("X-Actor-Id", "rate-limited-actor")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"agentVersion":"0.1.0","input":{"message":"rate limit test"}}
                        """))
                .build();
    }
}
