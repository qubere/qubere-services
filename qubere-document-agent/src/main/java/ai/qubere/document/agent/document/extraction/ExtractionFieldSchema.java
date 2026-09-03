package ai.qubere.document.agent.document.extraction;

/**
 * @param required {@code true} means the field's absence should raise a missing-data exception item
 */
public record ExtractionFieldSchema(String fieldName, String label, boolean required, ExtractionFieldType type) {
}
