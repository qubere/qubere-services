package ai.qubere.document.agent.document.review;

/**
 * A located field's bounding box, ported from {@code extractionReview.ts}'s {@code BoundingBox}.
 * Only ever constructed via {@link ExtractionReviewFields#parseBoundingBox(Object)}, which rejects
 * anything that isn't four finite numbers with a positive width/height.
 */
public record BoundingBox(double x, double y, double width, double height) {
}
