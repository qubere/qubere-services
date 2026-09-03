package ai.qubere.document.agent.document;

/**
 * Broad grouping used by the document type catalog, mirrored from the source
 * {@code DocumentTypeDefinition.category} union in {@code documentTypeCatalog.ts}.
 */
public enum DocumentTypeCategory {
    COMMERCIAL,
    CUSTOMS_CBP,
    PARTNER_GOVERNMENT_AGENCY,
    TRANSPORT,
    POST_ENTRY,
    COMPLIANCE
}
