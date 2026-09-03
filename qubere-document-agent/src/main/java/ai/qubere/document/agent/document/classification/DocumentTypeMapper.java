package ai.qubere.document.agent.document.classification;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps free-text document-type strings (as returned by an AI classifier) to the canonical
 * {@link DocumentType} vocabulary, ported from {@code lib/documents/classificationMapping.ts}.
 * The classifier prompt never guarantees a controlled vocabulary, so the lookup is keyword-based
 * and case-insensitive. Unknown types resolve to {@link DocumentType#OTHER} — callers decide what
 * to do when confidence is below {@link #CLASSIFICATION_CONFIDENCE_THRESHOLD}.
 */
public final class DocumentTypeMapper {

    /** Confidence below this (0-1 scale) routes the document to human review. */
    public static final double CLASSIFICATION_CONFIDENCE_THRESHOLD = 0.7;

    private static final Map<DocumentType, String[]> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(DocumentType.COMMERCIAL_INVOICE, new String[]{"commercial invoice", "invoice"});
        KEYWORDS.put(DocumentType.PACKING_LIST, new String[]{"packing list", "packing slip", "weight list"});
        KEYWORDS.put(DocumentType.BILL_OF_LADING, new String[]{"bill of lading", "bl", "ocean bill", "master bill", "house bill"});
        KEYWORDS.put(DocumentType.AIR_WAYBILL, new String[]{"air waybill", "airway bill", "awb", "mawb", "hawb"});
        KEYWORDS.put(DocumentType.CERTIFICATE_OF_ORIGIN, new String[]{"certificate of origin", "co", "coo", "usmca", "nafta", "form a", "gsp certificate"});
        KEYWORDS.put(DocumentType.PHYTOSANITARY_CERTIFICATE, new String[]{"phytosanitary", "plant health", "plant certificate"});
        KEYWORDS.put(DocumentType.FUMIGATION_CERTIFICATE, new String[]{"fumigation", "treatment certificate", "heat treatment"});
        KEYWORDS.put(DocumentType.CUSTOMS_BOND, new String[]{"customs bond", "surety bond", "import bond"});
        KEYWORDS.put(DocumentType.POWER_OF_ATTORNEY, new String[]{"power of attorney", "poa"});
        KEYWORDS.put(DocumentType.ENTRY_SUMMARY, new String[]{"entry summary", "cbp form 7501", "7501", "entry type"});
        KEYWORDS.put(DocumentType.ISF, new String[]{"importer security filing", "isf", "10+2", "10 2"});
    }

    private DocumentTypeMapper() {
    }

    /** Maps a raw classifier string to a {@link DocumentType}. Returns {@code OTHER} when no matcher fires. */
    public static DocumentType mapToDocumentType(String raw) {
        String lower = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
        for (Map.Entry<DocumentType, String[]> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return DocumentType.OTHER;
    }

    /**
     * Normalizes a classifier confidence value to the 0-1 scale this module uses. An AI classifier
     * typically returns 0-100; everything else is clamped into range.
     */
    public static double normalizeConfidence(Double raw) {
        if (raw == null) {
            return 0.0d;
        }
        if (raw > 1) {
            return Math.min(raw / 100.0, 1.0);
        }
        return Math.min(Math.max(raw, 0.0), 1.0);
    }
}
