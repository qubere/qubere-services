package ai.qubere.document.agent.document.parser;

/**
 * @param page       1-based page number, or {@code null} when unknown — never fabricated
 * @param bbox       bounding box, or {@code null} when the parser did not report one
 * @param elementRef provider's own element reference (e.g. a JSON pointer), when it has one
 */
public record Provenance(Integer page, BoundingBox bbox, String elementRef) {
}
