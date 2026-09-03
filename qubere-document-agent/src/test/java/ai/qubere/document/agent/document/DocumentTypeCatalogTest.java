package ai.qubere.document.agent.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeCatalogTest {

    @Test
    void returnsBuiltInCatalogWithExpectedEntryCount() {
        assertThat(DocumentTypeCatalog.documentTypes()).hasSize(25);
    }

    @Test
    void byCodeIsCaseInsensitiveAndTrims() {
        assertThat(DocumentTypeCatalog.byCode(" commercial_invoice ")).isPresent();
        assertThat(DocumentTypeCatalog.byCode("COMMERCIAL_INVOICE")).isPresent();
        assertThat(DocumentTypeCatalog.byCode("does-not-exist")).isEmpty();
    }

    @Test
    void matchDocumentTypeResolvesExactCatalogCode() {
        DocumentTypeDefinition matched = DocumentTypeCatalog.matchDocumentType("COMMERCIAL_INVOICE");
        assertThat(matched.code()).isEqualTo("COMMERCIAL_INVOICE");
    }

    @Test
    void matchDocumentTypeResolvesKnownAliases() {
        assertThat(DocumentTypeCatalog.matchDocumentType("BOL").code()).isEqualTo("OCEAN_BILL_OF_LADING");
        assertThat(DocumentTypeCatalog.matchDocumentType("COO").code()).isEqualTo("GENERAL_CERTIFICATE_OF_ORIGIN");
        assertThat(DocumentTypeCatalog.matchDocumentType("GSP_FORM_A").code()).isEqualTo("GENERAL_CERTIFICATE_OF_ORIGIN");
    }

    @Test
    void matchDocumentTypeAppliesHighPrecedenceKeywordChecksBeforeScoring() {
        // "certificate of origin" phrasing must resolve to GENERAL_CERTIFICATE_OF_ORIGIN even
        // though the raw text isn't an exact catalog code or alias.
        assertThat(DocumentTypeCatalog.matchDocumentType("Chamber Certificate of Origin - Form A").code())
                .isEqualTo("GENERAL_CERTIFICATE_OF_ORIGIN");
        assertThat(DocumentTypeCatalog.matchDocumentType("Ocean Bill of Lading MAWB 12345").code())
                .isEqualTo("OCEAN_BILL_OF_LADING");
        assertThat(DocumentTypeCatalog.matchDocumentType("Packing List and Weight List").code())
                .isEqualTo("PACKING_LIST");
        assertThat(DocumentTypeCatalog.matchDocumentType("Pro Forma Invoice draft").code())
                .isEqualTo("COMMERCIAL_INVOICE");
    }

    @Test
    void matchDocumentTypeFallsBackToKeywordScoringWhenNoDirectCheckMatches() {
        DocumentTypeDefinition matched = DocumentTypeCatalog.matchDocumentType("USMCA CUSMA Certifier Producer statement");
        assertThat(matched.code()).isEqualTo("USMCA_CERTIFICATE_OF_ORIGIN");
    }

    @Test
    void matchDocumentTypeReturnsUnverifiedRatherThanGuessing() {
        DocumentTypeDefinition matched = DocumentTypeCatalog.matchDocumentType("random_file_name_1234");
        assertThat(matched.code()).isEqualTo(DocumentTypeCatalog.OTHER_UNVERIFIED_DOCUMENT);
    }

    @Test
    void matchDocumentTypeNeverReturnsNullForBlankInput() {
        assertThat(DocumentTypeCatalog.matchDocumentType("")).isNotNull();
        assertThat(DocumentTypeCatalog.matchDocumentType(null)).isNotNull();
    }
}
