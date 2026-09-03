package ai.qubere.document.agent.document.context;

import ai.qubere.document.agent.document.parser.DocumentChunk;
import ai.qubere.document.agent.document.parser.DocumentChunkingService;
import ai.qubere.document.agent.document.parser.NormalizedParserResult;
import ai.qubere.document.agent.document.parser.SelectionBudget;
import ai.qubere.document.agent.document.parser.SelectionResult;
import ai.qubere.document.agent.document.parser.config.ParserProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a budget-enforced {@link DocumentContext} from a document's parsed result, the
 * missing connective piece (Phase 4 in {@code MIGRATION.md}) between the parser/quality-gate
 * pipeline and an extraction agent.
 * <p>
 * <strong>Deliberately scoped down from {@code qubereDocumentContext.ts}</strong>: the source
 * orders chunks by relevance to the requesting agent's specific purpose
 * (`orderChunksForPurpose()`) before applying the budget, so the most relevant material survives
 * truncation first. This builder selects chunks in the order {@link DocumentChunkingService}
 * produces them — document order, sections then tables — which is a reasonable default absent a
 * purpose-specific ranking, but is not equivalent to the source's behavior. Building real
 * purpose-aware ordering is real follow-up work, not attempted here, and is recorded as such in
 * {@code MIGRATION.md} rather than silently presented as complete.
 */
public final class QubereDocumentContextBuilder {

    private QubereDocumentContextBuilder() {
    }

    public static DocumentContext build(NormalizedParserResult result, ParserProperties.ContextBudget budget) {
        List<DocumentChunk> chunks = DocumentChunkingService.buildChunks(result, null);
        SelectionBudget selectionBudget = new SelectionBudget(budget.getMaxTokens(), budget.getMaxBytes(), budget.getMaxChunks());
        SelectionResult selection = DocumentChunkingService.selectWithinBudget(chunks, selectionBudget);

        StringBuilder content = new StringBuilder();
        List<String> headingPaths = new ArrayList<>();
        for (DocumentChunk chunk : selection.selected()) {
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            content.append(chunk.content());
            if (!chunk.headingPath().isEmpty()) {
                headingPaths.add(String.join(" > ", chunk.headingPath()));
            }
        }

        return new DocumentContext(
                content.toString(),
                selection.selected().size(),
                selection.droppedChunkCount(),
                selection.limitReached() == null ? null : selection.limitReached().name(),
                headingPaths
        );
    }
}
