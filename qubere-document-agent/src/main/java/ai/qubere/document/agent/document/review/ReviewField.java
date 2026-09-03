package ai.qubere.document.agent.document.review;

import java.util.List;

/**
 * One field's review state, ported from {@code extractionReview.ts}'s {@code ReviewField}. Built
 * only by {@link ExtractionReviewFields#buildReviewFields(List)}.
 */
public record ReviewField(
        String fieldName,
        /** The reading in force: the newest human correction, else the best machine read. */
        String currentValue,
        /** The machine's first reading. Null when the field only ever came from a human. */
        String originalValue,
        /** Best machine-read confidence. Null when never scored. */
        Integer confidence,
        Integer pageNumber,
        BoundingBox bbox,
        boolean corrected,
        /** True when a reviewer should look at it: low or absent confidence, uncorrected. */
        boolean needsReview,
        /** Newest first. Every reading ever stored for this field. */
        List<FieldRevision> history
) {
}
