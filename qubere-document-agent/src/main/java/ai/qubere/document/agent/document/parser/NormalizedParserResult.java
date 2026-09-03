package ai.qubere.document.agent.document.parser;

import java.util.List;

/**
 * Provider-neutral normalized parse output. Ported from {@code parser/contracts.ts}'s
 * {@code parserResultSchema}.
 *
 * @param markdown         derivative full-document Markdown, when the provider produced it; else {@code null}
 * @param pageTextLengths  per-page text length, used by the quality gate; empty when unavailable
 */
public record NormalizedParserResult(
        String contractVersion,
        ProcessingProfile profile,
        ParserMetadata metadata,
        String markdown,
        List<ParsedSection> sections,
        List<ParsedTable> tables,
        List<ParserWarning> warnings,
        List<Integer> pageTextLengths
) {
    /** Bump when the meaning of a field here changes; both the provider's own wire contract and this contract version independently. */
    public static final String CONTRACT_VERSION = "qubere.parser/1";

    public NormalizedParserResult {
        sections = sections == null ? List.of() : List.copyOf(sections);
        tables = tables == null ? List.of() : List.copyOf(tables);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        pageTextLengths = pageTextLengths == null ? List.of() : List.copyOf(pageTextLengths);
    }
}
