package ai.qubere.document.agent.document.classification;

/**
 * The canonical, database-persisted document-type vocabulary (mirrors the source project's
 * {@code DocumentType} Prisma enum), ported from {@code lib/documents/classificationMapping.ts}.
 * <p>
 * <strong>This is a deliberately different, smaller vocabulary from
 * {@code ai.qubere.document.agent.document.DocumentTypeCatalog}</strong>, not an oversight. The
 * source project itself carries (and its own {@code fieldDictionary.ts} explicitly documents)
 * multiple document-type vocabularies that never fully reconcile: this eleven-value enum is what
 * gets persisted and reconciled against, while {@code DocumentTypeCatalog} is a much richer
 * ~25-entry reference catalog (with CFR citations, required-for-filing flags, keyword sets) used
 * for intake-time classification detail. Codes do not line up 1:1 — for example this enum's
 * {@code BILL_OF_LADING} corresponds to the catalog's {@code OCEAN_BILL_OF_LADING}, and this enum
 * has no {@code AIR_WAYBILL}-vs-{@code ARRIVAL_NOTICE} distinction the catalog has.
 * <p>
 * This is recorded here as an inherited design gap, not silently perpetuated: see
 * {@code qubere-document-agent/MIGRATION.md} for the explicit note that unifying these two
 * vocabularies (or building a documented, tested mapping between them) is future work once a
 * reconciliation engine consuming both exists in this module.
 */
public enum DocumentType {
    COMMERCIAL_INVOICE,
    PACKING_LIST,
    BILL_OF_LADING,
    AIR_WAYBILL,
    CERTIFICATE_OF_ORIGIN,
    PHYTOSANITARY_CERTIFICATE,
    FUMIGATION_CERTIFICATE,
    CUSTOMS_BOND,
    POWER_OF_ATTORNEY,
    ENTRY_SUMMARY,
    ISF,
    OTHER
}
