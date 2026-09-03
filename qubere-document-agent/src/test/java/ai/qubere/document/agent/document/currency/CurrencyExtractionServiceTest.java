package ai.qubere.document.agent.document.currency;

import ai.qubere.document.agent.document.review.ExtractedFieldReading;
import ai.qubere.document.agent.document.review.ExtractionReviewService;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "agent-platform.prompts.provider=in-memory",
        "agent-platform.async.worker-enabled=false",
        "document-agent.parser.processing-limits.poll-initial-delay-millis=600000"
})
class CurrencyExtractionServiceTest {

    @Autowired
    private ExtractionReviewService reviewService;

    @Autowired
    private CurrencyExtractionService currencyExtractionService;

    @Test
    void resolvesTheAgreedCurrencyAcrossDocumentsFromTheirCurrencyField() {
        String doc1 = uniqueDocumentId();
        String doc2 = uniqueDocumentId();
        reviewService.recordMachineReadings(doc1, List.of(new ExtractedFieldReading("currency", "$100", 90)));
        reviewService.recordMachineReadings(doc2, List.of(new ExtractedFieldReading("currency", "USD 200", 90)));

        assertThat(currencyExtractionService.agreedCurrency(List.of(doc1, doc2))).isEqualTo("USD");
    }

    @Test
    void returnsNullWhenDocumentsDisagreeOnCurrency() {
        String doc1 = uniqueDocumentId();
        String doc2 = uniqueDocumentId();
        reviewService.recordMachineReadings(doc1, List.of(new ExtractedFieldReading("currency", "$100", 90)));
        reviewService.recordMachineReadings(doc2, List.of(new ExtractedFieldReading("currency", "€50", 90)));

        assertThat(currencyExtractionService.agreedCurrency(List.of(doc1, doc2))).isNull();
        assertThat(currencyExtractionService.distinctCurrencies(List.of(doc1, doc2))).containsExactly("EUR", "USD");
    }

    @Test
    void returnsNullWhenNoDocumentDeclaredACurrency() {
        String doc1 = uniqueDocumentId();
        reviewService.recordMachineReadings(doc1, List.of(new ExtractedFieldReading("invoiceNumber", "INV-1", 90)));

        assertThat(currencyExtractionService.agreedCurrency(List.of(doc1))).isNull();
    }

    private String uniqueDocumentId() {
        return "currency-doc-" + UUID.randomUUID();
    }
}
