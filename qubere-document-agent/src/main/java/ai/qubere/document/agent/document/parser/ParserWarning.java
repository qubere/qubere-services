package ai.qubere.document.agent.document.parser;

/**
 * @param code stable, low-cardinality code — safe as a metric label
 * @param page 1-based page, or {@code null} when not page-scoped
 */
public record ParserWarning(String code, String message, Integer page) {
}
