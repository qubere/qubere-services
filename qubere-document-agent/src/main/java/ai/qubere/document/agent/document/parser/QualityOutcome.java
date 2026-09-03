package ai.qubere.document.agent.document.parser;

/** Outcome of {@link QualityGateEvaluator#assessQuality}. */
public enum QualityOutcome {
    PASS,
    PASS_WITH_WARNINGS,
    RETRY_WITH_OCR,
    NEEDS_REVIEW,
    FAILED
}
