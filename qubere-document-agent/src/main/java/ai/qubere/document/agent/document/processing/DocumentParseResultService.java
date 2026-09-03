package ai.qubere.document.agent.document.processing;

import ai.qubere.document.agent.document.parser.NormalizedParserResult;
import ai.qubere.document.agent.document.parser.QualityOutcome;

import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

/**
 * Persists and retrieves the current active parse result per document. See
 * {@link DocumentParseResultEntity} for why this is a single-row-per-document store rather than
 * the source's full multi-artifact scheme.
 */
@Service
public class DocumentParseResultService {

    private final DocumentParseResultRepository repository;
    private final ObjectMapper objectMapper;

    public DocumentParseResultService(DocumentParseResultRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Replaces the active result for {@code documentId}. Callers must only call this for a run
     * whose quality outcome {@link ai.qubere.document.agent.document.parser.QualityGateEvaluator#qualifiesAsActive}
     * — a {@code NEEDS_REVIEW}/{@code RETRY_WITH_OCR} result must not become the document's active
     * version, matching the source's own promotion rule.
     */
    public void promoteToActive(String documentId, String processingRunId, NormalizedParserResult result, QualityOutcome outcome) {
        DocumentParseResultEntity entity = repository.findById(documentId).orElseGet(DocumentParseResultEntity::new);
        entity.setDocumentId(documentId);
        entity.setProcessingRunId(processingRunId);
        entity.setNormalizedResultJson(writeJson(result));
        entity.setQualityOutcome(outcome.name());
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
    }

    public Optional<NormalizedParserResult> findActiveResult(String documentId) {
        return repository.findById(documentId).map(entity -> readJson(entity.getNormalizedResultJson()));
    }

    private String writeJson(NormalizedParserResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize parse result for storage", ex);
        }
    }

    private NormalizedParserResult readJson(String json) {
        try {
            return objectMapper.readValue(json, NormalizedParserResult.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize stored parse result", ex);
        }
    }
}
