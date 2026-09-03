package ai.qubere.document.agent.document.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityGateEvaluatorTest {

    @Test
    void retriesWithFullPageOcrWhenNothingUsableCameBackAndOcrNotYetAttempted() {
        QualityAssessment assessment = QualityGateEvaluator.assessQuality(new QualityGateInput(
                emptyResult(), null, false, false
        ));

        assertThat(assessment.outcome()).isEqualTo(QualityOutcome.RETRY_WITH_OCR);
        assertThat(assessment.suggestedRetryProfile()).isEqualTo(ProcessingProfile.FULL_PAGE_OCR);
    }

    @Test
    void needsReviewWhenNothingUsableCameBackAndFullPageOcrAlreadyAttempted() {
        QualityAssessment assessment = QualityGateEvaluator.assessQuality(new QualityGateInput(
                emptyResult(), null, false, true
        ));

        assertThat(assessment.outcome()).isEqualTo(QualityOutcome.NEEDS_REVIEW);
        assertThat(assessment.suggestedRetryProfile()).isNull();
    }

    @Test
    void suggestsOcrFallbackOnFirstLowCoverageRetryAndFullPageOcrOnSecond() {
        NormalizedParserResult lowCoverage = resultWithPageLengths(List.of(200, 5, 3, 2, 1));

        QualityAssessment first = QualityGateEvaluator.assessQuality(new QualityGateInput(lowCoverage, null, false, false));
        assertThat(first.outcome()).isEqualTo(QualityOutcome.RETRY_WITH_OCR);
        assertThat(first.suggestedRetryProfile()).isEqualTo(ProcessingProfile.OCR_FALLBACK);

        QualityAssessment second = QualityGateEvaluator.assessQuality(new QualityGateInput(lowCoverage, null, true, false));
        assertThat(second.outcome()).isEqualTo(QualityOutcome.RETRY_WITH_OCR);
        assertThat(second.suggestedRetryProfile()).isEqualTo(ProcessingProfile.FULL_PAGE_OCR);
    }

    @Test
    void needsReviewWhenLowCoverageSurvivesFullPageOcr() {
        NormalizedParserResult lowCoverage = resultWithPageLengths(List.of(200, 5, 3, 2, 1));

        QualityAssessment assessment = QualityGateEvaluator.assessQuality(new QualityGateInput(lowCoverage, null, false, true));

        assertThat(assessment.outcome()).isEqualTo(QualityOutcome.NEEDS_REVIEW);
    }

    @Test
    void passesCleanlyWhenCoverageIsHighAndNoWarnings() {
        // 4 of 5 pages have >= 40 chars -> 80% coverage, above the 60% threshold.
        NormalizedParserResult goodResult = resultWithPageLengths(List.of(200, 200, 200, 200, 1));

        QualityAssessment assessment = QualityGateEvaluator.assessQuality(new QualityGateInput(goodResult, null, false, false));

        assertThat(assessment.outcome()).isEqualTo(QualityOutcome.PASS);
        assertThat(QualityGateEvaluator.qualifiesAsActive(assessment.outcome())).isTrue();
    }

    @Test
    void passWithWarningsWhenBlankPagesExistDespiteGoodCoverage() {
        NormalizedParserResult resultWithBlankPage = resultWithPageLengths(List.of(200, 200, 200, 200, 0));

        QualityAssessment assessment = QualityGateEvaluator.assessQuality(new QualityGateInput(resultWithBlankPage, null, false, false));

        assertThat(assessment.outcome()).isEqualTo(QualityOutcome.PASS_WITH_WARNINGS);
        assertThat(assessment.blankPageCount()).isEqualTo(1);
        assertThat(QualityGateEvaluator.qualifiesAsActive(assessment.outcome())).isTrue();
    }

    @Test
    void needsReviewWhenAReviewWarningCodeIsPresentEvenWithGoodCoverage() {
        NormalizedParserResult resultWithReviewWarning = new NormalizedParserResult(
                NormalizedParserResult.CONTRACT_VERSION, ProcessingProfile.STANDARD,
                new ParserMetadata("MOCK", null, null, null, null, 1, null, null, null, null, null),
                null,
                List.of(new ParsedSection("s1", List.of(), "some real content here", List.of())),
                List.of(),
                List.of(new ParserWarning("IMAGE_ONLY_RESULT", "image only", null)),
                List.of(200)
        );

        QualityAssessment assessment = QualityGateEvaluator.assessQuality(new QualityGateInput(resultWithReviewWarning, null, false, false));

        assertThat(assessment.outcome()).isEqualTo(QualityOutcome.NEEDS_REVIEW);
        assertThat(QualityGateEvaluator.qualifiesAsActive(assessment.outcome())).isFalse();
    }

    @Test
    void retryWithOcrNeverQualifiesAsActive() {
        assertThat(QualityGateEvaluator.qualifiesAsActive(QualityOutcome.RETRY_WITH_OCR)).isFalse();
        assertThat(QualityGateEvaluator.qualifiesAsActive(QualityOutcome.FAILED)).isFalse();
    }

    private NormalizedParserResult emptyResult() {
        return new NormalizedParserResult(
                NormalizedParserResult.CONTRACT_VERSION, ProcessingProfile.STANDARD,
                new ParserMetadata("MOCK", null, null, null, null, 0, null, null, null, null, null),
                null, List.of(), List.of(), List.of(), List.of()
        );
    }

    private NormalizedParserResult resultWithPageLengths(List<Integer> pageLengths) {
        return new NormalizedParserResult(
                NormalizedParserResult.CONTRACT_VERSION, ProcessingProfile.STANDARD,
                new ParserMetadata("MOCK", null, null, null, null, pageLengths.size(), null, null, null, null, null),
                null,
                List.of(new ParsedSection("s1", List.of(), "some real content here", List.of())),
                List.of(),
                List.of(),
                pageLengths
        );
    }
}
