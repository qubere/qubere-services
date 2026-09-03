package ai.qubere.document.agent.document;

import java.util.List;

/**
 * One entry in the trade-document type catalog.
 *
 * @param code                 stable catalog code (e.g. {@code COMMERCIAL_INVOICE})
 * @param name                 human-readable title
 * @param category             broad document category
 * @param cfrRegulation        citing regulation, or {@code null} when none applies
 * @param isRequiredForFiling  whether this document type is normally required to file an entry
 * @param keywords             lowercase phrases used for keyword-scored matching
 * @param description          one-line description of the document's purpose
 */
public record DocumentTypeDefinition(
        String code,
        String name,
        DocumentTypeCategory category,
        String cfrRegulation,
        boolean isRequiredForFiling,
        List<String> keywords,
        String description
) {
    public DocumentTypeDefinition {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
