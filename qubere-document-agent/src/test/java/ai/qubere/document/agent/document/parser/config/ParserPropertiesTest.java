package ai.qubere.document.agent.document.parser.config;

import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ProcessingProfile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserPropertiesTest {

    @Test
    void reportsWhichSettingsAreMissingWithoutExposingValues() {
        ParserProperties properties = new ParserProperties();

        assertThatThrownBy(properties::validatedIbmDoclingConfig)
                .isInstanceOfSatisfying(DocumentParserException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ParserErrorCode.PARSER_NOT_CONFIGURED);
                    assertThat(ex.retryable()).isFalse();
                    assertThat(ex.getMessage()).contains("base-url");
                    assertThat(ex.getMessage()).contains("api-key");
                });
    }

    @Test
    void isIbmDoclingConfiguredIsFalseUntilRequiredFieldsAreSet() {
        ParserProperties properties = new ParserProperties();
        assertThat(properties.isIbmDoclingConfigured()).isFalse();

        properties.getIbmDocling().setBaseUrl("https://docling.example.com");
        properties.getIbmDocling().setApiKey("secret-key");
        assertThat(properties.isIbmDoclingConfigured()).isTrue();
    }

    @Test
    void submitEncodingDefaultsFromSubmitPathWhenNotExplicitlySet() {
        ParserProperties.IbmDocling ibmDocling = new ParserProperties.IbmDocling();

        ibmDocling.setSubmitPath("/v1/convert/file/async");
        assertThat(ibmDocling.getSubmitEncoding()).isEqualTo(ParserProfileCatalog.SubmitEncoding.MULTIPART);

        ibmDocling.setSubmitPath("/v1/convert/source/async");
        assertThat(ibmDocling.getSubmitEncoding()).isEqualTo(ParserProfileCatalog.SubmitEncoding.JSON);
    }

    @Test
    void explicitSubmitEncodingOverridesTheDerivedValue() {
        ParserProperties.IbmDocling ibmDocling = new ParserProperties.IbmDocling();
        ibmDocling.setSubmitPath("/v1/convert/source/async");

        ibmDocling.setSubmitEncoding(ParserProfileCatalog.SubmitEncoding.MULTIPART);

        assertThat(ibmDocling.getSubmitEncoding()).isEqualTo(ParserProfileCatalog.SubmitEncoding.MULTIPART);
    }

    @Test
    void processingLimitsClampOutOfRangeValuesToTheirBounds() {
        ParserProperties.ProcessingLimits limits = new ParserProperties.ProcessingLimits();

        limits.setMaxAttempts(9999);
        assertThat(limits.getMaxAttempts()).isEqualTo(20);

        limits.setMaxAttempts(0);
        assertThat(limits.getMaxAttempts()).isEqualTo(1);

        limits.setBatchSize(-5);
        assertThat(limits.getBatchSize()).isEqualTo(1);
    }

    @Test
    void profileCatalogAlwaysEnablesTableStructureAndOnlyFullPageOcrForcesReOcr() {
        assertThat(ParserProfileCatalog.optionsFor(ProcessingProfile.STANDARD).forceOcr()).isFalse();
        assertThat(ParserProfileCatalog.optionsFor(ProcessingProfile.OCR_FALLBACK).forceOcr()).isFalse();
        assertThat(ParserProfileCatalog.optionsFor(ProcessingProfile.FULL_PAGE_OCR).forceOcr()).isTrue();

        for (ProcessingProfile profile : ProcessingProfile.values()) {
            assertThat(ParserProfileCatalog.optionsFor(profile).doTableStructure()).isTrue();
        }
    }
}
