package ai.qubere.document.agent.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DocumentSubmissionController} end-to-end against real local-disk storage: an
 * upload actually lands on disk and a real {@code ProcessingRunEntity} is enqueued, closing the loop
 * this controller exists for (there was previously no way to get a document's bytes into the
 * processing worker's reach at all).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "agent-platform.prompts.provider=in-memory",
                "agent-platform.async.worker-enabled=false",
                "document-agent.storage.type=local-disk",
                "spring.datasource.url=jdbc:h2:mem:document_submission_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
        }
)
class DocumentSubmissionControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("document-agent.storage.local-disk.root-dir", tempDir::toString);
    }

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void acceptsAnUploadAndEnqueuesAProcessingRunWhenAShipmentIdIsSupplied() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/documents"))
                .header("Content-Type", "application/pdf")
                .header("X-Document-Filename", "invoice.pdf")
                .header("X-Document-Mime-Type", "application/pdf")
                .header("X-Shipment-Id", "shipment-1")
                .header("X-Tenant-Id", "tenant-1")
                .header("X-Actor-Id", "actor-1")
                .POST(HttpRequest.BodyPublishers.ofByteArray("%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).contains("\"documentId\"");
        assertThat(response.body()).contains("\"processingRunId\"");
        assertThat(response.body()).contains("\"state\":\"QUEUED\"");
        assertThat(response.body()).contains("\"unassignedIntakeId\":null");
        assertThat(response.body()).contains("\"crossShipmentDuplicates\":[]");
    }

    @Test
    void reportsACrossShipmentDuplicateAsANonBlockingSignal() throws Exception {
        byte[] content = "%PDF-1.4 identical bytes".getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> first = client.send(uploadRequest(content, "shipment-a", "invoice-a.pdf"), HttpResponse.BodyHandlers.ofString());
        assertThat(first.statusCode()).isEqualTo(202);

        HttpResponse<String> second = client.send(uploadRequest(content, "shipment-b", "invoice-b.pdf"), HttpResponse.BodyHandlers.ofString());

        assertThat(second.statusCode()).isEqualTo(202);
        assertThat(second.body()).contains("\"shipmentId\":\"shipment-a\"");
    }

    private HttpRequest uploadRequest(byte[] content, String shipmentId, String fileName) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/documents"))
                .header("Content-Type", "application/pdf")
                .header("X-Document-Filename", fileName)
                .header("X-Shipment-Id", shipmentId)
                .header("X-Tenant-Id", "tenant-dup")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
    }

    @Test
    void recordsAnUnassignedIntakeRatherThanGuessingAShipmentWhenNoneIsSupplied() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/documents"))
                .header("Content-Type", "application/pdf")
                .header("X-Document-Filename", "invoice.pdf")
                .header("X-Tenant-Id", "tenant-1")
                .POST(HttpRequest.BodyPublishers.ofByteArray("%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).contains("\"unassignedIntakeId\"");
        assertThat(response.body()).contains("without naming a shipment");
        assertThat(response.body()).contains("\"processingRunId\":null");
        assertThat(response.body()).contains("\"crossShipmentDuplicates\":[]");
    }

    @Test
    void rejectsAnEmptyBodyWithBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/documents"))
                .header("Content-Type", "application/pdf")
                .header("X-Shipment-Id", "shipment-1")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
    }
}
