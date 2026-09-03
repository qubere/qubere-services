package ai.qubere.document.agent.document.parser;

import java.util.List;

/**
 * @param page   1-based page, or {@code null} when unknown
 * @param html   loss-minimizing derivative the provider supplied; {@code null} when it did not
 */
public record ParsedTable(
        String id,
        int index,
        String caption,
        Integer page,
        BoundingBox bbox,
        int rowCount,
        int columnCount,
        List<ParsedTableCell> cells,
        String html
) {
    public ParsedTable {
        cells = cells == null ? List.of() : List.copyOf(cells);
    }
}
