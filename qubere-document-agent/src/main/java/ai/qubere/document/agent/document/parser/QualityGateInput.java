package ai.qubere.document.agent.document.parser;

/**
 * @param expectedPageCount how many pages were expected, when known independently of the parser; {@code null} when unknown
 * @param isOcrRetry        {@code true} when this run is already an OCR retry, so the evaluator does not loop forever
 * @param usedFullPageOcr   {@code true} when the run already used full-page OCR
 */
public record QualityGateInput(
        NormalizedParserResult result,
        Integer expectedPageCount,
        boolean isOcrRetry,
        boolean usedFullPageOcr
) {
}
