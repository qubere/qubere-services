package ai.qubere.document.agent.document.context;

import java.util.List;

/**
 * Assembled, budget-enforced context handed to an extraction agent, built from a document's
 * {@code NormalizedParserResult} chunks.
 *
 * @param content            selected chunks' text, joined in the order they were given
 * @param selectedChunkCount how many chunks made it into {@code content}
 * @param droppedChunkCount  chunks the budget excluded; truncation is always reported, never silent
 * @param limitReached       which budget dimension stopped selection, or {@code null} if nothing was dropped
 */
public record DocumentContext(
        String content,
        int selectedChunkCount,
        int droppedChunkCount,
        String limitReached,
        List<String> headingPaths
) {
    public DocumentContext {
        headingPaths = headingPaths == null ? List.of() : List.copyOf(headingPaths);
    }

    public boolean wasTruncated() {
        return droppedChunkCount > 0;
    }
}
