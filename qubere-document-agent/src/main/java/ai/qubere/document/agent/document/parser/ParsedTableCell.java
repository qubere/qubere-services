package ai.qubere.document.agent.document.parser;

public record ParsedTableCell(
        int row,
        int column,
        int rowSpan,
        int columnSpan,
        boolean isHeader,
        String text,
        Provenance provenance
) {
}
