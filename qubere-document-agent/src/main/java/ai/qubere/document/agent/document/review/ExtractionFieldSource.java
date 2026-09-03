package ai.qubere.document.agent.document.review;

/**
 * Where a stored field reading came from, ported from {@code extractionReview.ts}'s
 * {@code HUMAN_CORRECTION_SOURCE} constant (there was no machine-reading counterpart constant in
 * the source; every non-human {@code source} string was treated as "a machine read").
 */
public enum ExtractionFieldSource {
    MACHINE,
    HUMAN_CORRECTION
}
