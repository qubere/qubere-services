package ai.qubere.document.agent.document.parser;

import java.time.Instant;
import java.util.List;

/**
 * @param textCoverage        fraction of pages carrying more than the low-text-page threshold; {@code null} when page data is absent
 * @param warningCodes        codes of parser warnings that influenced the outcome
 * @param ocrUsed              as reported by the parser; {@code null} means the parser did not say
 * @param reasons              human-readable, specific reasons for the outcome
 * @param suggestedRetryProfile profile to use when {@code outcome} is {@code RETRY_WITH_OCR}; restricted to
 *                              {@code OCR_FALLBACK} or {@code FULL_PAGE_OCR}, {@code null} otherwise
 */
public record QualityAssessment(
        QualityOutcome outcome,
        Integer pageCount,
        Double textCoverage,
        Integer blankPageCount,
        Integer lowTextPageCount,
        int tableCount,
        int sectionCount,
        int totalTextLength,
        int warningCount,
        List<String> warningCodes,
        Boolean ocrUsed,
        Boolean fullPageOcrUsed,
        List<String> reasons,
        ProcessingProfile suggestedRetryProfile,
        Instant assessedAt
) {
    public QualityAssessment {
        warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
