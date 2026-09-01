package ai.qubere.agent.app;

import ai.qubere.agent.persistence.AgentPromptTemplateRepository;
import ai.qubere.agent.prompts.PromptStatus;

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
                "spring.datasource.url=jdbc:h2:mem:agentadminprompt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.ai.model.chat=none",
                "agent-platform.ai.spring.enabled=false",
                "agent-platform.admin.enabled=true",
                "agent-platform.admin.token=test-admin-token",
                "agent-platform.prompts.seeds[0].prompt-id=prompt.seeded",
                "agent-platform.prompts.seeds[0].agent-id=generic.echo-analysis",
                "agent-platform.prompts.seeds[0].version=0.1.0",
                "agent-platform.prompts.seeds[0].status=ACTIVE",
                "agent-platform.prompts.seeds[0].system-template=Seeded system",
                "agent-platform.prompts.seeds[0].user-template=Seeded user {{message}}"
        }
)
class AgentAdminPromptE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private AgentPromptTemplateRepository promptTemplateRepository;

    @Test
    void seedsListsCreatesAndTransitionsPromptVersions() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> seededList = client.send(request("GET", "/api/agents/admin/prompts/agents/generic.echo-analysis", null), HttpResponse.BodyHandlers.ofString());
        assertThat(seededList.statusCode()).isEqualTo(200);
        assertThat(seededList.body()).contains("prompt.seeded");
        assertThat(promptTemplateRepository.findAll()).hasSize(1);

        String body = """
                {
                  "promptId":"prompt.created",
                  "agentId":"generic.echo-analysis",
                  "version":"0.2.0",
                  "status":"DRAFT",
                  "systemTemplate":"Created system",
                  "userTemplate":"Created user {{message}}",
                  "metadata":{"owner":"qa"}
                }
                """;

        HttpResponse<String> created = client.send(request("POST", "/api/agents/admin/prompts", body), HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.body()).contains("prompt.created");

        HttpResponse<String> activated = client.send(request("POST", "/api/agents/admin/prompts/prompt.created/versions/0.2.0/activate", null), HttpResponse.BodyHandlers.ofString());
        assertThat(activated.statusCode()).isEqualTo(200);
        assertThat(activated.body()).contains("ACTIVE");

        assertThat(promptTemplateRepository.findById(new ai.qubere.agent.persistence.AgentPromptTemplateId("prompt.created", "0.2.0")).orElseThrow().getStatus())
                .isEqualTo(PromptStatus.ACTIVE);
        assertThat(promptTemplateRepository.findById(new ai.qubere.agent.persistence.AgentPromptTemplateId("prompt.seeded", "0.1.0")).orElseThrow().getStatus())
                .isEqualTo(PromptStatus.DEPRECATED);

        HttpResponse<String> archived = client.send(request("POST", "/api/agents/admin/prompts/prompt.created/versions/0.2.0/archive", null), HttpResponse.BodyHandlers.ofString());
        assertThat(archived.statusCode()).isEqualTo(200);
        assertThat(archived.body()).contains("ARCHIVED");
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
