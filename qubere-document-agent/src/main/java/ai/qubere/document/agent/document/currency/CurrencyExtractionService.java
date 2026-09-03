package ai.qubere.document.agent.document.currency;

import ai.qubere.document.agent.document.review.ExtractionReviewService;
import ai.qubere.document.agent.document.review.ReviewField;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * The currency a set of documents is denominated in, resolved from each document's current
 * {@code currency} extraction field. Ported from {@code extractedCurrency.ts}, adapted to this
 * module's storage: the source reads a raw {@code extractedJson} blob per document and searches six
 * JSON paths for a currency value; this reads the already-normalized-into-fields
 * {@link ExtractionReviewService} instead, since {@code DocumentIntelligenceAgent}'s output is
 * already recorded there field-by-field rather than as one opaque JSON blob per document.
 * <p>
 * Nothing on {@code ProcessingRunEntity} stores a currency directly -- this is deliberately the only
 * honest source, matching the source module's own reasoning: "the only honest source is what the
 * extractor read off the documents themselves."
 */
@Service
public class CurrencyExtractionService {

    private final ExtractionReviewService reviewService;

    public CurrencyExtractionService(ExtractionReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Every distinct currency these documents' extractions agree on individually, sorted. */
    public List<String> distinctCurrencies(List<String> documentIds) {
        return CurrencyAgreement.distinctCurrencies(rawCurrencyValues(documentIds));
    }

    /** The single currency these documents agree on, or {@code null} (see {@link CurrencyAgreement#agreedCurrency}). */
    public String agreedCurrency(List<String> documentIds) {
        return CurrencyAgreement.agreedCurrency(rawCurrencyValues(documentIds));
    }

    private List<String> rawCurrencyValues(List<String> documentIds) {
        return documentIds.stream()
                .map(this::currencyFieldValue)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String currencyFieldValue(String documentId) {
        for (ReviewField field : reviewService.reviewFieldsFor(documentId)) {
            if ("currency".equals(field.fieldName())) {
                return field.currentValue();
            }
        }
        return null;
    }
}
