package ai.qubere.document.agent.document.parser.ibm;

import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.NormalizedParserResult;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ParserResult;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingRunState;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoclingAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void translateTaskStatusRecognizesAllDocumentedStatusValues() {
        assertThat(DoclingAdapter.translateTaskStatus("success").state()).isEqualTo(ProcessingRunState.SUCCEEDED);
        assertThat(DoclingAdapter.translateTaskStatus("SUCCEEDED").state()).isEqualTo(ProcessingRunState.SUCCEEDED);
        assertThat(DoclingAdapter.translateTaskStatus("failed").state()).isEqualTo(ProcessingRunState.FAILED);
        assertThat(DoclingAdapter.translateTaskStatus("revoked").state()).isEqualTo(ProcessingRunState.FAILED);
        assertThat(DoclingAdapter.translateTaskStatus("running").state()).isEqualTo(ProcessingRunState.POLLING);
        assertThat(DoclingAdapter.translateTaskStatus("running").recognized()).isTrue();
    }

    @Test
    void unrecognizedStatusKeepsPollingRatherThanResolving() {
        DoclingAdapter.TaskStatusTranslation translation = DoclingAdapter.translateTaskStatus("some_future_status");

        assertThat(translation.state()).isEqualTo(ProcessingRunState.POLLING);
        assertThat(translation.recognized()).isFalse();
    }

    @Test
    void adaptsAFullResultWithSectionsHeadingsAndTables() throws Exception {
        String json = """
                {
                  "document": {
                    "json_content": {
                      "schema_name": "DoclingDocument",
                      "version": "1.2.0",
                      "texts": [
                        {"self_ref": "#/texts/0", "label": "section_header", "level": 1, "text": "COMMERCIAL INVOICE", "prov": [{"page_no": 1}]},
                        {"self_ref": "#/texts/1", "label": "text", "text": "Invoice No. 1234", "prov": [{"page_no": 1}]},
                        {"self_ref": "#/texts/2", "label": "text", "text": "Total: $500.00", "prov": [{"page_no": 1}]}
                      ],
                      "tables": [
                        {
                          "self_ref": "#/tables/0",
                          "prov": [{"page_no": 1}],
                          "data": {
                            "num_rows": 2,
                            "num_cols": 2,
                            "table_cells": [
                              {"text": "Item", "start_row_offset_idx": 0, "start_col_offset_idx": 0, "column_header": true},
                              {"text": "Qty", "start_row_offset_idx": 0, "start_col_offset_idx": 1, "column_header": true},
                              {"text": "Widget", "start_row_offset_idx": 1, "start_col_offset_idx": 0},
                              {"text": "10", "start_row_offset_idx": 1, "start_col_offset_idx": 1}
                            ]
                          }
                        }
                      ],
                      "pages": {"1": {"page_no": 1}}
                    },
                    "md_content": "COMMERCIAL INVOICE\\nInvoice No. 1234"
                  }
                }
                """;
        JsonNode payload = mapper.readTree(json);

        ParserResult result = DoclingAdapter.adaptDoclingResult(payload, ProcessingProfile.STANDARD);
        NormalizedParserResult normalized = result.normalized();

        assertThat(normalized.sections()).hasSize(1);
        assertThat(normalized.sections().get(0).headingPath()).containsExactly("COMMERCIAL INVOICE");
        assertThat(normalized.sections().get(0).content()).isEqualTo("Invoice No. 1234\nTotal: $500.00");
        assertThat(normalized.sections().get(0).id()).startsWith("sec_0000_");

        assertThat(normalized.tables()).hasSize(1);
        assertThat(normalized.tables().get(0).rowCount()).isEqualTo(2);
        assertThat(normalized.tables().get(0).columnCount()).isEqualTo(2);
        assertThat(normalized.tables().get(0).cells()).hasSize(4);
        assertThat(normalized.tables().get(0).html()).contains("<th>Item</th>").contains("<td>Widget</td>");

        assertThat(normalized.metadata().parserName()).isEqualTo("DoclingDocument");
        assertThat(normalized.metadata().parserVersion()).isEqualTo("1.2.0");
        assertThat(normalized.metadata().provider()).isEqualTo("IBM_DOCLING");
        assertThat(normalized.markdown()).isEqualTo("COMMERCIAL INVOICE\nInvoice No. 1234");
    }

    @Test
    void ridsOfInvalidPayloadWithNonRetryableError() {
        assertThatThrownBy(() -> DoclingAdapter.adaptDoclingResult(null, ProcessingProfile.STANDARD))
                .isInstanceOfSatisfying(DocumentParserException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ParserErrorCode.PARSER_RESULT_INVALID);
                    assertThat(ex.retryable()).isFalse();
                });
    }

    @Test
    void reportsIncompleteWhenSuccessfulEnvelopeCarriesNoContentAtAll() throws Exception {
        JsonNode payload = mapper.readTree("{\"document\": {}}");

        assertThatThrownBy(() -> DoclingAdapter.adaptDoclingResult(payload, ProcessingProfile.STANDARD))
                .isInstanceOfSatisfying(DocumentParserException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ParserErrorCode.PARSER_RESULT_INCOMPLETE));
    }

    @Test
    void mapConfidencePrefersMeanScoreOverParseScoreAndNeverFabricatesOcrUsed() throws Exception {
        JsonNode withMean = mapper.readTree("{\"mean_score\": 0.9, \"parse_score\": 0.5, \"ocr_score\": 0.8}");
        DoclingAdapter.ConfidenceMapping mapping = DoclingAdapter.mapConfidence(withMean);
        assertThat(mapping.parserConfidence()).isEqualTo(0.9);
        assertThat(mapping.ocrConfidence()).isEqualTo(0.8);
        assertThat(mapping.ocrUsed()).isTrue();

        JsonNode noOcrScore = mapper.readTree("{\"parse_score\": 0.5}");
        DoclingAdapter.ConfidenceMapping withoutOcr = DoclingAdapter.mapConfidence(noOcrScore);
        assertThat(withoutOcr.parserConfidence()).isEqualTo(0.5);
        assertThat(withoutOcr.ocrConfidence()).isNull();
        // Null ocr_score means "not measured", not "OCR did not run" -- must stay null, not false.
        assertThat(withoutOcr.ocrUsed()).isNull();

        DoclingAdapter.ConfidenceMapping missing = DoclingAdapter.mapConfidence(null);
        assertThat(missing.parserConfidence()).isNull();
        assertThat(missing.ocrUsed()).isNull();
    }

    @Test
    void headingWithNoBodyStillProducesASectionFromTheHeadingAlone() throws Exception {
        String json = """
                {
                  "document": {
                    "json_content": {
                      "texts": [
                        {"self_ref": "#/texts/0", "label": "section_header", "level": 1, "text": "CERTIFICATE OF ORIGIN"}
                      ]
                    }
                  }
                }
                """;
        JsonNode payload = mapper.readTree(json);

        ParserResult result = DoclingAdapter.adaptDoclingResult(payload, ProcessingProfile.STANDARD);

        assertThat(result.normalized().sections()).hasSize(1);
        assertThat(result.normalized().sections().get(0).headingPath()).containsExactly("CERTIFICATE OF ORIGIN");
        assertThat(result.normalized().sections().get(0).content()).isEmpty();
    }

    @Test
    void nestedHeadingLevelsPopTheStackCorrectly() throws Exception {
        String json = """
                {
                  "document": {
                    "json_content": {
                      "texts": [
                        {"label": "section_header", "level": 1, "text": "Part A"},
                        {"label": "section_header", "level": 2, "text": "Section 1"},
                        {"label": "text", "text": "Body under 1"},
                        {"label": "section_header", "level": 2, "text": "Section 2"},
                        {"label": "text", "text": "Body under 2"}
                      ]
                    }
                  }
                }
                """;
        JsonNode payload = mapper.readTree(json);

        ParserResult result = DoclingAdapter.adaptDoclingResult(payload, ProcessingProfile.STANDARD);
        var sections = result.normalized().sections();

        // Section 2 must not carry Section 1 in its path -- level-2 pops the previous level-2 off
        // the stack rather than nesting under it.
        var section2 = sections.stream().filter(s -> s.content().equals("Body under 2")).findFirst().orElseThrow();
        assertThat(section2.headingPath()).containsExactly("Part A", "Section 2");
    }

    @Test
    void bboxWithMissingCoordinatesNormalizesToNullRatherThanZero() throws Exception {
        ObjectNode incompleteBbox = mapper.createObjectNode();
        incompleteBbox.put("l", 1.0);
        incompleteBbox.put("t", 2.0);
        // r and b intentionally omitted.

        assertThat(DoclingAdapter.normalizeBbox(incompleteBbox)).isNull();
        assertThat(DoclingAdapter.normalizeBbox(null)).isNull();
    }

    @Test
    void stableIdIsDeterministicForIdenticalContent() {
        String first = DoclingAdapter.stableId("sec", 0, "same content");
        String second = DoclingAdapter.stableId("sec", 0, "same content");
        String differentOrdinal = DoclingAdapter.stableId("sec", 1, "same content");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(differentOrdinal);
        assertThat(first).matches("sec_0000_[0-9a-f]{12}");
    }
}
