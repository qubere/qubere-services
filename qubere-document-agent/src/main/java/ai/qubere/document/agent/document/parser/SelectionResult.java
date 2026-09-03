package ai.qubere.document.agent.document.parser;

import java.util.List;

/**
 * @param droppedChunkCount chunks the budget excluded; reported so truncation is never silent
 * @param limitReached      which limit stopped selection, or {@code null} when nothing was dropped
 */
public record SelectionResult(
        List<DocumentChunk> selected,
        int droppedChunkCount,
        LimitReached limitReached,
        int totalEstimatedTokens,
        int totalBytes
) {
    public SelectionResult {
        selected = selected == null ? List.of() : List.copyOf(selected);
    }

    public enum LimitReached {
        TOKENS,
        BYTES,
        CHUNKS
    }
}
