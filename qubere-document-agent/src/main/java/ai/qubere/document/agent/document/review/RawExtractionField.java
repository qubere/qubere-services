package ai.qubere.document.agent.document.review;

import java.time.Instant;

/**
 * One stored reading of one field, ported from {@code extractionReview.ts}'s
 * {@code RawExtractionField}. {@code bbox} is kept as the raw deserialized value (a {@code Map} when
 * it came from JSON, or {@code null}) rather than parsed up front, mirroring the source's
 * {@code unknown} — parsing only happens where it's actually needed, via
 * {@link ExtractionReviewFields#parseBoundingBox(Object)}.
 */
public record RawExtractionField(
        String id,
        String fieldName,
        String value,
        Integer confidence,
        Integer pageNumber,
        Object bbox,
        ExtractionFieldSource source,
        Instant createdAt
) {
}
