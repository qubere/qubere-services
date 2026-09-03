package ai.qubere.document.agent.document.parser.ibm;

import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.DocumentParserProvider;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ParserJobReference;
import ai.qubere.document.agent.document.parser.ParserJobStatus;
import ai.qubere.document.agent.document.parser.ParserResult;
import ai.qubere.document.agent.document.parser.ParserSource;
import ai.qubere.document.agent.document.parser.ParserSourceInline;
import ai.qubere.document.agent.document.parser.ParserSourceSignedUrl;
import ai.qubere.document.agent.document.parser.ParserSubmission;
import ai.qubere.document.agent.document.parser.ParserSubmissionAck;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingRunState;
import ai.qubere.document.agent.document.parser.SourceDelivery;
import ai.qubere.document.agent.document.parser.config.ParserProfileCatalog;
import ai.qubere.document.agent.document.parser.config.ParserProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * IBM-hosted Docling parser provider, ported from {@code parser/ibm/ibmHostedDoclingProvider.ts}.
 * Routes every HTTP call through {@link AgentResilienceGateway} keyed by provider id, so a flaky
 * parser deployment is circuit-broken exactly like an AI provider or tool call already is
 * elsewhere in this framework — this is a deliberate reuse of an existing framework capability the
 * source project's own worker had no equivalent of.
 * <p>
 * <strong>Scoped out of this port, documented rather than silently dropped</strong> (see
 * {@code qubere-document-agent/MIGRATION.md}): {@code multipart/form-data} submission encoding
 * (only the JSON {@code /convert/source/...} encoding is implemented; a deployment that only
 * exposes {@code /convert/file/...} is not yet supported), and the batch/presigned-artifact-URL
 * result shape some hosted deployments return instead of inlining content (only the inline
 * {@code doclingResultSchema} shape is normalized here).
 */
public class IbmDoclingProvider implements DocumentParserProvider {

    private static final Set<Integer> RETRYABLE_HTTP_STATUSES = Set.of(408, 425, 429, 500, 502, 503, 504, 507, 509);

    private final RestClient restClient;
    private final ParserProperties.IbmDocling config;
    private final AgentResilienceGateway resilienceGateway;
    private final ObjectMapper objectMapper;

    public IbmDoclingProvider(
            RestClient.Builder restClientBuilder,
            ParserProperties properties,
            AgentResilienceGateway resilienceGateway,
            ObjectMapper objectMapper
    ) {
        this.config = properties.validatedIbmDoclingConfig();
        this.resilienceGateway = resilienceGateway == null ? AgentResilienceGateway.noop() : resilienceGateway;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(config.getRequestTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(config.getRequestTimeoutMs()));
        this.restClient = restClientBuilder.baseUrl(config.getBaseUrl()).requestFactory(requestFactory).build();
    }

    @Override
    public String providerId() {
        return DoclingAdapter.PROVIDER_ID;
    }

    @Override
    public boolean isMockProvider() {
        return false;
    }

    @Override
    public SourceDelivery sourceDelivery() {
        return config.getSourceDelivery();
    }

    /**
     * Hashes the settings that change what the provider produces. The API key is excluded —
     * rotating a credential does not change the parse, and hashing a secret into a stored column
     * is a needless exposure.
     */
    @Override
    public String configurationHash(ProcessingProfile profile) {
        String material = String.join("|",
                "ibm.docling.serve/v1", config.getBaseUrl(), config.getSubmitPath(),
                config.getSubmitEncoding().name(), config.getSourceDelivery().name(), profile.name(),
                String.valueOf(ParserProfileCatalog.optionsFor(profile))
        );
        return sha256Hex(material).substring(0, 32);
    }

    @Override
    public ParserSubmissionAck submit(ParserSubmission submission) {
        return resilienceGateway.execute("parser:" + providerId(), () -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("options", convertOptions(submission.profile()));
            body.putAll(buildSourcePayload(submission.source()));

            JsonNode payload = request("submission", config.getSubmitPath(), body, submission.correlationId());
            String taskId = textOrNull(payload.path("task_id"));
            if (taskId == null) {
                throw new DocumentParserException(
                        ParserErrorCode.PARSER_RESULT_INVALID,
                        "The document parser returned a submission response without a task id.",
                        false, null, null
                );
            }
            String rawStatus = textOrNull(payload.path("task_status"));
            DoclingAdapter.TaskStatusTranslation translated = DoclingAdapter.translateTaskStatus(
                    rawStatus == null ? "pending" : rawStatus);
            ProcessingRunState state = translated.state() == ProcessingRunState.FAILED
                    ? ProcessingRunState.SUBMITTED : translated.state();

            return new ParserSubmissionAck(taskId, rawStatus == null ? "pending" : rawStatus, state, List.of(), Instant.now());
        });
    }

    @Override
    public ParserJobStatus getStatus(ParserJobReference reference) {
        return resilienceGateway.execute("parser:" + providerId(), () -> {
            JsonNode payload = requestGet("status",
                    config.getStatusPathTemplate().replace("{taskId}", encode(reference.externalTaskId())),
                    reference.correlationId());

            String rawStatus = textOrNull(payload.path("task_status"));
            DoclingAdapter.TaskStatusTranslation translated = DoclingAdapter.translateTaskStatus(
                    rawStatus == null ? "" : rawStatus);
            Instant observedAt = Instant.now();

            if (translated.state() == ProcessingRunState.FAILED) {
                return new ParserJobStatus(
                        ProcessingRunState.FAILED,
                        rawStatus == null ? "unknown" : rawStatus,
                        new DocumentParserException(
                                ParserErrorCode.PARSER_PROVIDER_ERROR,
                                "The document parser reported task status '" + rawStatus + "'.",
                                true, rawStatus, null
                        ),
                        observedAt
                );
            }
            return new ParserJobStatus(translated.state(), rawStatus == null ? "unknown" : rawStatus, null, observedAt);
        });
    }

    @Override
    public ParserResult getResult(ParserJobReference reference, ProcessingProfile profile) {
        return resilienceGateway.execute("parser:" + providerId(), () -> {
            JsonNode payload = requestGet("result",
                    config.getResultPathTemplate().replace("{taskId}", encode(reference.externalTaskId())),
                    reference.correlationId());
            try {
                return DoclingAdapter.adaptDoclingResult(payload, profile);
            } catch (DocumentParserException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new DocumentParserException(
                        ParserErrorCode.PARSER_RESULT_INVALID,
                        "The document parser result could not be normalized.",
                        false, null, ex
                );
            }
        });
    }

    // ---------------------------------------------------------------------------------------
    // Request building
    // ---------------------------------------------------------------------------------------

    private Map<String, Object> convertOptions(ProcessingProfile profile) {
        var options = ParserProfileCatalog.optionsFor(profile);
        Map<String, Object> converted = new LinkedHashMap<>();
        // JSON is the canonical artifact; Markdown is requested as a derivative so it does not
        // have to be re-rendered and risk drifting from the provider's own rendering.
        converted.put("to_formats", List.of("json", "md"));
        converted.put("do_ocr", options.doOcr());
        converted.put("force_ocr", options.forceOcr());
        converted.put("do_table_structure", options.doTableStructure());
        // Page images would multiply payload size for no downstream consumer.
        converted.put("include_images", false);
        return converted;
    }

    private Map<String, Object> buildSourcePayload(ParserSource source) {
        if (source instanceof ParserSourceSignedUrl signedUrl) {
            // The URL must be one this deployment minted against its own storage. A
            // client-supplied URL reaching the provider would be an SSRF primitive with the
            // provider as the confused deputy.
            SsrfArtifactHostValidator.assertHttpsUrl(signedUrl.url());
            return Map.of("sources", List.of(Map.of("kind", "http", "url", signedUrl.url())));
        }
        ParserSourceInline inline = (ParserSourceInline) source;
        String base64 = Base64.getEncoder().encodeToString(inline.bytes());
        return Map.of("sources", List.of(Map.of("kind", "file", "base64_string", base64, "filename", inline.filename())));
    }

    // ---------------------------------------------------------------------------------------
    // HTTP plumbing
    // ---------------------------------------------------------------------------------------

    private Map<String, String> authHeaders() {
        String value = config.getAuthHeaderScheme().isEmpty()
                ? config.getApiKey()
                : config.getAuthHeaderScheme() + " " + config.getApiKey();
        return Map.of(config.getAuthHeaderName(), value);
    }

    private JsonNode request(String operation, String path, Map<String, Object> body, String correlationId) {
        try {
            String responseBody = restClient.post()
                    .uri(path)
                    .headers(headers -> {
                        authHeaders().forEach(headers::set);
                        headers.set("X-Correlation-Id", correlationId);
                        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw httpFailure(operation, res.getStatusCode().value(), res.getStatusText());
                    })
                    .body(String.class);
            return parseJson(operation, responseBody);
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            throw timeoutOrUnreachable(operation, ex);
        }
    }

    private JsonNode requestGet(String operation, String path, String correlationId) {
        try {
            String responseBody = restClient.get()
                    .uri(path)
                    .headers(headers -> {
                        authHeaders().forEach(headers::set);
                        headers.set("X-Correlation-Id", correlationId);
                        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw httpFailure(operation, res.getStatusCode().value(), res.getStatusText());
                    })
                    .body(String.class);
            return parseJson(operation, responseBody);
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            throw timeoutOrUnreachable(operation, ex);
        }
    }

    private JsonNode parseJson(String operation, String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_RESULT_INVALID,
                    "The document parser returned a non-JSON response to the " + operation + " request.",
                    false, null, ex
            );
        }
    }

    private DocumentParserException timeoutOrUnreachable(String operation, Exception cause) {
        boolean aborted = cause.getCause() instanceof java.net.SocketTimeoutException
                || cause instanceof java.net.SocketTimeoutException;
        return new DocumentParserException(
                aborted ? ParserErrorCode.PARSER_TIMEOUT : ParserErrorCode.PARSER_PROVIDER_ERROR,
                aborted
                        ? "The document parser did not respond to the " + operation + " request within "
                        + config.getRequestTimeoutMs() + "ms."
                        : "The document parser could not be reached for the " + operation + " request.",
                true, null, cause
        );
    }

    /**
     * Maps an HTTP failure status to a {@link ParserErrorCode}, ported exactly from
     * {@code ibmHostedDoclingProvider.ts}: 9 specific statuses are retryable; 401/403 mean the
     * credential is wrong ({@code PARSER_NOT_CONFIGURED}); 404 and 408/504 get their own codes
     * (with 408/504 overridden retryable); everything else is a generic provider error.
     */
    private DocumentParserException httpFailure(String operation, int status, String statusText) {
        boolean retryable = RETRYABLE_HTTP_STATUSES.contains(status);
        ParserErrorCode code;
        if (status == 401 || status == 403) {
            code = ParserErrorCode.PARSER_NOT_CONFIGURED;
        } else if (status == 404) {
            code = ParserErrorCode.PARSER_PROVIDER_ERROR;
        } else if (status == 408 || status == 504) {
            code = ParserErrorCode.PARSER_TIMEOUT;
            retryable = true;
        } else {
            code = ParserErrorCode.PARSER_PROVIDER_ERROR;
        }
        return new DocumentParserException(
                code,
                "The document parser " + operation + " request failed with status " + status
                        + (statusText == null || statusText.isBlank() ? "" : " (" + statusText + ")") + ".",
                retryable, String.valueOf(status), null
        );
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isTextual() ? null : node.asText();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required and must be available on every supported JVM", ex);
        }
    }
}
