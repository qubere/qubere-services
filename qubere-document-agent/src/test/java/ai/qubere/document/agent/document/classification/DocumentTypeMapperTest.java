package ai.qubere.document.agent.document.classification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeMapperTest {

    @Test
    void mapsKnownKeywordsToTheirDocumentType() {
        assertThat(DocumentTypeMapper.mapToDocumentType("Commercial Invoice No. 1234")).isEqualTo(DocumentType.COMMERCIAL_INVOICE);
        assertThat(DocumentTypeMapper.mapToDocumentType("Master Bill of Lading")).isEqualTo(DocumentType.BILL_OF_LADING);
        assertThat(DocumentTypeMapper.mapToDocumentType("USMCA Certificate")).isEqualTo(DocumentType.CERTIFICATE_OF_ORIGIN);
        assertThat(DocumentTypeMapper.mapToDocumentType("Importer Security Filing 10+2")).isEqualTo(DocumentType.ISF);
    }

    @Test
    void unrecognizedTextMapsToOther() {
        assertThat(DocumentTypeMapper.mapToDocumentType("random scanned page")).isEqualTo(DocumentType.OTHER);
        assertThat(DocumentTypeMapper.mapToDocumentType("")).isEqualTo(DocumentType.OTHER);
    }

    @Test
    void normalizeConfidenceScalesFromZeroToHundredIntoZeroToOne() {
        assertThat(DocumentTypeMapper.normalizeConfidence(90.0)).isEqualTo(0.9);
        assertThat(DocumentTypeMapper.normalizeConfidence(0.9)).isEqualTo(0.9);
        assertThat(DocumentTypeMapper.normalizeConfidence(150.0)).isEqualTo(1.0);
        assertThat(DocumentTypeMapper.normalizeConfidence(-5.0)).isEqualTo(0.0);
        assertThat(DocumentTypeMapper.normalizeConfidence(null)).isEqualTo(0.0);
    }
}
