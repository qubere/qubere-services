package ai.qubere.document.agent.document.review;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Connects {@link ExtractionReviewFields}' pure grouping/precedence logic to persistence, ported
 * from the database-touching parts of {@code extractionReview.ts} (the file itself is pure; this is
 * the caller the source expected a route handler to be).
 */
@Service
public class ExtractionReviewService {

    private final ExtractionFieldRepository repository;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;

    public ExtractionReviewService(ExtractionFieldRepository repository, ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.repository = repository;
        this.objectMapperProvider = objectMapperProvider;
    }

    /**
     * Records an extraction agent's readings as new {@code MACHINE}-sourced rows. Blank/null values
     * are not recorded at all -- an absent field is not a "reading of nothing," it is simply not a
     * reading, and recording it would give {@code buildReviewFields} a phantom row to reason about.
     */
    public void recordMachineReadings(String documentId, List<ExtractedFieldReading> readings) {
        Instant now = Instant.now();
        for (ExtractedFieldReading reading : readings) {
            if (reading.value() == null || reading.value().isBlank()) {
                continue;
            }
            ExtractionFieldEntity entity = new ExtractionFieldEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setDocumentId(documentId);
            entity.setFieldName(reading.fieldName());
            entity.setValue(reading.value());
            entity.setConfidence(reading.confidence());
            entity.setSource(ExtractionFieldSource.MACHINE);
            entity.setCreatedAt(now);
            repository.save(entity);
        }
        repository.flush();
    }

    public List<ReviewField> reviewFieldsFor(String documentId) {
        List<RawExtractionField> rows = repository.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .map(this::toRaw)
                .toList();
        return ExtractionReviewFields.buildReviewFields(rows);
    }

    /**
     * Stores a human correction as a brand-new row -- the machine's original reading is never
     * overwritten. {@code fieldName} must already have at least one reading; a correction to a
     * field nobody ever extracted is not a correction, it has nothing to correct.
     */
    public ExtractionFieldEntity submitCorrection(String documentId, String fieldName, String rawValue) {
        List<ReviewField> fields = reviewFieldsFor(documentId);
        ReviewField field = fields.stream()
                .filter(candidate -> candidate.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No extraction field named '" + fieldName + "' exists for document " + documentId));

        CorrectionValidation validation = ExtractionReviewFields.validateCorrection(rawValue, field.currentValue());
        if (!validation.ok()) {
            throw new IllegalArgumentException(validation.reason());
        }

        ExtractionFieldEntity entity = new ExtractionFieldEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setDocumentId(documentId);
        entity.setFieldName(fieldName);
        entity.setValue(validation.value());
        // A reviewed value is not a model prediction, so it carries no model score.
        entity.setConfidence(null);
        entity.setSource(ExtractionFieldSource.HUMAN_CORRECTION);
        entity.setCreatedAt(Instant.now());
        return repository.saveAndFlush(entity);
    }

    private RawExtractionField toRaw(ExtractionFieldEntity entity) {
        Object bbox = parseBboxJson(entity.getBboxJson());
        return new RawExtractionField(
                entity.getId(), entity.getFieldName(), entity.getValue(), entity.getConfidence(),
                entity.getPageNumber(), bbox, entity.getSource(), entity.getCreatedAt());
    }

    private Object parseBboxJson(String bboxJson) {
        if (bboxJson == null || bboxJson.isBlank()) {
            return null;
        }
        try {
            return objectMapperProvider.getIfAvailable(ObjectMapper::new).readValue(bboxJson, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
