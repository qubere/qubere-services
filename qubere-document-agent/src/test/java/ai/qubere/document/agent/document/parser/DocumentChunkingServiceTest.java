package ai.qubere.document.agent.document.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkingServiceTest {

    @Test
    void producesOneChunkPerSectionWhenUnderTheSizeLimit() {
        NormalizedParserResult result = resultWithSections("Short section body.");

        List<DocumentChunk> chunks = DocumentChunkingService.buildChunks(result, null);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).kind()).isEqualTo(ChunkKind.SECTION);
        assertThat(chunks.get(0).content()).isEqualTo("Short section body.");
    }

    @Test
    void splitsAtLineBoundariesRatherThanMidLine() {
        String longBody = "line one is reasonably long\n".repeat(5) + "final short line";
        NormalizedParserResult result = resultWithSections(longBody);

        List<DocumentChunk> chunks = DocumentChunkingService.buildChunks(result, new ChunkingOptions(60));

        assertThat(chunks).hasSizeGreaterThan(1);
        // Every piece must be a clean concatenation of whole lines -- never a mid-line cut.
        for (DocumentChunk chunk : chunks) {
            assertThat(longBody).contains(chunk.content());
        }
    }

    @Test
    void aHeadingWithNoBodyStillProducesAChunkFromTheHeadingItself() {
        NormalizedParserResult result = new NormalizedParserResult(
                NormalizedParserResult.CONTRACT_VERSION, ProcessingProfile.STANDARD,
                metadata(),
                null,
                List.of(new ParsedSection("s1", List.of("CERTIFICATE OF ORIGIN"), "", List.of())),
                List.of(), List.of(), List.of()
        );

        List<DocumentChunk> chunks = DocumentChunkingService.buildChunks(result, null);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("CERTIFICATE OF ORIGIN");
    }

    @Test
    void chunkIdsAreDeterministicForTheSameContent() {
        NormalizedParserResult result = resultWithSections("Stable content.");

        List<DocumentChunk> first = DocumentChunkingService.buildChunks(result, null);
        List<DocumentChunk> second = DocumentChunkingService.buildChunks(result, null);

        assertThat(first.get(0).id()).isEqualTo(second.get(0).id());
        assertThat(first.get(0).id()).startsWith("chk_");
    }

    @Test
    void differentContentProducesDifferentChunkIds() {
        List<DocumentChunk> a = DocumentChunkingService.buildChunks(resultWithSections("Content A."), null);
        List<DocumentChunk> b = DocumentChunkingService.buildChunks(resultWithSections("Content B."), null);

        assertThat(a.get(0).id()).isNotEqualTo(b.get(0).id());
    }

    @Test
    void selectWithinBudgetStopsAtChunkLimitAndReportsWhatWasDropped() {
        List<DocumentChunk> chunks = List.of(chunk("a"), chunk("b"), chunk("c"));

        SelectionResult result = DocumentChunkingService.selectWithinBudget(chunks, new SelectionBudget(10_000, 10_000, 2));

        assertThat(result.selected()).hasSize(2);
        assertThat(result.droppedChunkCount()).isEqualTo(1);
        assertThat(result.limitReached()).isEqualTo(SelectionResult.LimitReached.CHUNKS);
    }

    @Test
    void selectWithinBudgetPreservesGivenOrderRatherThanReranking() {
        List<DocumentChunk> chunks = List.of(chunk("first"), chunk("second"), chunk("third"));

        SelectionResult result = DocumentChunkingService.selectWithinBudget(chunks, new SelectionBudget(10_000, 10_000, 3));

        assertThat(result.selected()).extracting(DocumentChunk::content).containsExactly("first", "second", "third");
        assertThat(result.limitReached()).isNull();
    }

    @Test
    void tableToMarkdownRendersAHeaderSeparatorAfterTheLastHeaderRow() {
        ParsedTable table = new ParsedTable(
                "tbl1", 0, null, 1, null, 2, 2,
                List.of(
                        new ParsedTableCell(0, 0, 1, 1, true, "Name", null),
                        new ParsedTableCell(0, 1, 1, 1, true, "Value", null),
                        new ParsedTableCell(1, 0, 1, 1, false, "HTS Code", null),
                        new ParsedTableCell(1, 1, 1, 1, false, "8471.30", null)
                ),
                null
        );

        String markdown = DocumentChunkingService.tableToMarkdown(table);

        assertThat(markdown).isEqualTo(
                "| Name | Value |\n| --- | --- |\n| HTS Code | 8471.30 |"
        );
    }

    @Test
    void tableToMarkdownReturnsEmptyStringForATableWithNoCells() {
        ParsedTable emptyTable = new ParsedTable("tbl1", 0, null, null, null, 0, 0, List.of(), null);

        assertThat(DocumentChunkingService.tableToMarkdown(emptyTable)).isEmpty();
    }

    @Test
    void estimateTokensIsConservativeFourCharsPerToken() {
        assertThat(DocumentChunkingService.estimateTokens("12345678")).isEqualTo(2);
        assertThat(DocumentChunkingService.estimateTokens("123")).isEqualTo(1);
    }

    private DocumentChunk chunk(String content) {
        return new DocumentChunk("chk_" + content, ChunkKind.SECTION, "s1", List.of(), content,
                null, null, content.getBytes().length, DocumentChunkingService.estimateTokens(content), "hash", List.of());
    }

    private NormalizedParserResult resultWithSections(String body) {
        return new NormalizedParserResult(
                NormalizedParserResult.CONTRACT_VERSION, ProcessingProfile.STANDARD,
                metadata(),
                null,
                List.of(new ParsedSection("s1", List.of(), body, List.of())),
                List.of(), List.of(), List.of()
        );
    }

    private ParserMetadata metadata() {
        return new ParserMetadata("MOCK", null, null, null, null, 1, null, null, null, null, null);
    }
}
