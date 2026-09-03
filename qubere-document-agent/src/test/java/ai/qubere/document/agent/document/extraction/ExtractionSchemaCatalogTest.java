package ai.qubere.document.agent.document.extraction;

import ai.qubere.document.agent.document.classification.DocumentType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionSchemaCatalogTest {

    @Test
    void otherDocumentTypeHasNoSchemaSinceExtractionIsOpportunistic() {
        assertThat(ExtractionSchemaCatalog.schemaFor(DocumentType.OTHER)).isEmpty();
        assertThat(ExtractionSchemaCatalog.schemaFor(null)).isEmpty();
    }

    @Test
    void commercialInvoiceRequiresLineItemsAndInvoiceNumber() {
        var required = ExtractionSchemaCatalog.requiredFieldsFor(DocumentType.COMMERCIAL_INVOICE);

        assertThat(required).extracting(ExtractionFieldSchema::fieldName)
                .contains("invoice_number", "line_items", "total_value");
        assertThat(required).extracting(ExtractionFieldSchema::fieldName).doesNotContain("incoterm");
    }

    @Test
    void everyDocumentTypeExceptOtherHasASchema() {
        for (DocumentType type : DocumentType.values()) {
            if (type == DocumentType.OTHER) {
                continue;
            }
            assertThat(ExtractionSchemaCatalog.schemaFor(type)).isNotEmpty();
        }
    }
}
