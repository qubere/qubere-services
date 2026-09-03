package ai.qubere.document.agent.document.parser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Document quality gate, ported from {@code parser/qualityGate.ts}.
 * <p>
 * Decides, from objective signals only, whether a parse is good enough to become the document's
 * active version. There are no invented percentages here: every input is a count, a flag the
 * parser actually set, or a warning the parser actually emitted. A judgement is expressed as an
 * outcome plus the reasons that produced it, so a reviewer sees <em>why</em> a document is being
 * held rather than a bare score.
 * <p>
 * Stateless and side-effect-free by design (no database access), so the policy can be tested
 * directly against constructed {@link NormalizedParserResult} fixtures.
 */
public final class QualityGateEvaluator {

    /**
     * A page with fewer than this many extracted characters is "low text". Chosen because a
     * customs document page carrying only a stamp, a signature, or a scan artifact lands well
     * below it, while even a sparse continuation page of a packing list clears it. It is a
     * threshold on a measured count, not a confidence score.
     */
    private static final int LOW_TEXT_PAGE_CHARS = 40;

    /** A page with no extracted characters at all. */
    private static final int BLANK_PAGE_CHARS = 0;

    /**
     * Fraction of pages that must carry meaningful text for a parse to pass without an OCR retry.
     * Below this, the text layer is presumed insufficient.
     */
    private static final double MIN_TEXT_COVERAGE = 0.6;

    /**
     * Warning codes that mean the parse is materially incomplete rather than merely imperfect, so
     * a human has to look at the document.
     */
    private static final Set<String> REVIEW_WARNING_CODES = Set.of(
            "NO_STRUCTURED_DOCUMENT", "IMAGE_ONLY_RESULT", "PROVIDER_REPORTED_ERROR"
    );

    private QualityGateEvaluator() {
    }

    public static QualityAssessment assessQuality(QualityGateInput input) {
        NormalizedParserResult result = input.result();
        List<Integer> pageLengths = result.pageTextLengths();
        boolean hasPageData = !pageLengths.isEmpty();

        int totalTextLength = result.sections().stream().mapToInt(section -> section.content().length()).sum()
                + result.tables().stream()
                .flatMap(table -> table.cells().stream())
                .mapToInt(cell -> cell.text().length())
                .sum();

        Integer blankPageCount = hasPageData
                ? (int) pageLengths.stream().filter(length -> length == BLANK_PAGE_CHARS).count()
                : null;
        Integer lowTextPageCount = hasPageData
                ? (int) pageLengths.stream().filter(length -> length < LOW_TEXT_PAGE_CHARS).count()
                : null;
        Double textCoverage = hasPageData
                ? (double) (pageLengths.size() - lowTextPageCount) / pageLengths.size()
                : null;

        List<String> warningCodes = new ArrayList<>(new LinkedHashSet<>(
                result.warnings().stream().map(ParserWarning::code).toList()));

        Integer pageCount = result.metadata().pageCount();

        // ---- Hard failures: nothing usable came back at all. -----------------------------
        if (result.sections().isEmpty() && result.tables().isEmpty()) {
            boolean canRetry = !input.usedFullPageOcr();
            List<String> reasons = new ArrayList<>();
            reasons.add("The parser returned no text sections and no tables.");
            reasons.add(canRetry
                    ? "Retrying with OCR because no text layer was recovered."
                    : "Full-page OCR has already been attempted, so this document needs a person to look at it.");
            return new QualityAssessment(
                    canRetry ? QualityOutcome.RETRY_WITH_OCR : QualityOutcome.NEEDS_REVIEW,
                    pageCount, textCoverage, blankPageCount, lowTextPageCount,
                    result.tables().size(), result.sections().size(), totalTextLength,
                    result.warnings().size(), warningCodes,
                    result.metadata().ocrUsed(), result.metadata().fullPageOcrUsed(),
                    reasons, canRetry ? ProcessingProfile.FULL_PAGE_OCR : null, Instant.now()
            );
        }

        List<String> reasons = new ArrayList<>();

        // ---- Page-count disagreement ------------------------------------------------------
        if (input.expectedPageCount() != null && pageCount != null && !pageCount.equals(input.expectedPageCount())) {
            reasons.add("The parser reported " + pageCount + " page(s) but " + input.expectedPageCount() + " were expected.");
        }

        // ---- Insufficient text coverage ----------------------------------------------------
        QualityOutcome outcome = QualityOutcome.PASS;
        ProcessingProfile suggestedRetryProfile = null;

        if (textCoverage != null && textCoverage < MIN_TEXT_COVERAGE) {
            reasons.add("Only " + Math.round(textCoverage * 100) + "% of pages carried more than "
                    + LOW_TEXT_PAGE_CHARS + " characters of text.");
            if (input.usedFullPageOcr()) {
                outcome = QualityOutcome.NEEDS_REVIEW;
                reasons.add("Full-page OCR has already run, so remaining gaps are not an OCR problem.");
            } else {
                outcome = QualityOutcome.RETRY_WITH_OCR;
                // The first retry escalates gently; a second escalates to forcing OCR on pages
                // that already claim a text layer.
                suggestedRetryProfile = input.isOcrRetry() ? ProcessingProfile.FULL_PAGE_OCR : ProcessingProfile.OCR_FALLBACK;
                reasons.add("Retrying with the " + suggestedRetryProfile + " profile because the recovered text layer is insufficient.");
            }
        }

        // ---- Warnings that demand review ---------------------------------------------------
        List<String> reviewWarnings = warningCodes.stream().filter(REVIEW_WARNING_CODES::contains).toList();
        if (!reviewWarnings.isEmpty() && outcome != QualityOutcome.RETRY_WITH_OCR) {
            outcome = QualityOutcome.NEEDS_REVIEW;
            reasons.add("The parser raised warning(s) that need review: " + String.join(", ", reviewWarnings) + ".");
        }

        // ---- Blank pages ---------------------------------------------------------------------
        if (blankPageCount != null && blankPageCount > 0 && outcome == QualityOutcome.PASS) {
            outcome = QualityOutcome.PASS_WITH_WARNINGS;
            reasons.add(blankPageCount + " page(s) yielded no text at all.");
        }

        if (outcome == QualityOutcome.PASS && !result.warnings().isEmpty()) {
            outcome = QualityOutcome.PASS_WITH_WARNINGS;
            reasons.add("The parser emitted " + result.warnings().size() + " non-fatal warning(s).");
        }

        if (!hasPageData && outcome == QualityOutcome.PASS) {
            // Content came back but the parser attributed none of it to pages, so page-level
            // quality is simply unknown. Saying so beats claiming a clean pass.
            outcome = QualityOutcome.PASS_WITH_WARNINGS;
            reasons.add("The parser reported no per-page information, so page coverage is unknown.");
        }

        if (reasons.isEmpty()) {
            reasons.add("Text recovered from " + (pageCount != null ? pageCount : pageLengths.size())
                    + " page(s) with " + result.tables().size() + " structured table(s).");
        }

        return new QualityAssessment(
                outcome, pageCount, textCoverage, blankPageCount, lowTextPageCount,
                result.tables().size(), result.sections().size(), totalTextLength,
                result.warnings().size(), warningCodes,
                result.metadata().ocrUsed(), result.metadata().fullPageOcrUsed(),
                reasons, suggestedRetryProfile, Instant.now()
        );
    }

    /**
     * Whether a run with this outcome may become the document's active version.
     * {@code RETRY_WITH_OCR} deliberately does not qualify: the run's artifacts are kept for
     * audit, but a better run is expected to supersede it.
     */
    public static boolean qualifiesAsActive(QualityOutcome outcome) {
        return outcome == QualityOutcome.PASS || outcome == QualityOutcome.PASS_WITH_WARNINGS;
    }
}
