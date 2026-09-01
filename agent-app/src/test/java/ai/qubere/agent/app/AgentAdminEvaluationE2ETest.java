package ai.qubere.agent.app;

import ai.qubere.agent.persistence.AgentEvaluationResultRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:agentadmineval;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.ai.model.chat=none",
                "agent-platform.ai.spring.enabled=false",
                "agent-platform.admin.enabled=true",
                "agent-platform.admin.token=test-admin-token",
                "agent-platform.evaluation.dataset-locations[0]=classpath*:agent-evaluation/*.json"
        }
)
class AgentAdminEvaluationE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private AgentEvaluationResultRepository evaluationResultRepository;

    @Test
    void adminRunEvaluationLoadsClasspathDatasetAndPersistsResult() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/agents/admin/evaluations/generic-echo-admin-e2e/run"))
                .header("X-Agent-Admin-Token", "test-admin-token")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"datasetName\":\"generic-echo-admin-e2e\"");
        assertThat(response.body()).contains("\"total\":1");
        assertThat(response.body()).contains("\"passed\":1");
        assertThat(response.body()).contains("\"failed\":0");

        assertThat(evaluationResultRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(result -> {
                    assertThat(result.getDatasetName()).isEqualTo("generic-echo-admin-e2e");
                    assertThat(result.getTotal()).isEqualTo(1);
                    assertThat(result.getPassed()).isEqualTo(1);
                    assertThat(result.getFailed()).isZero();
                    assertThat(result.getCasesJson()).contains("echo-admin-case-1");
                });
    }
}
