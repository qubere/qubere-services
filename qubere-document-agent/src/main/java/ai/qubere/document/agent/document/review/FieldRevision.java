package ai.qubere.document.agent.document.review;

import java.time.Instant;

/** One entry in a field's revision history, ported from {@code extractionReview.ts}'s {@code FieldRevision}. */
public record FieldRevision(
        String id,
        String value,
        Integer confidence,
        ExtractionFieldSource source,
        Instant createdAt,
        boolean isCorrection
) {
}
