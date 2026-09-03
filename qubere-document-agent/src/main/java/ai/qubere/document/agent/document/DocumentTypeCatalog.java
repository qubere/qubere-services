package ai.qubere.document.agent.document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Trade-document type catalog, ported from {@code apps/custom/src/modules/intake/documentTypeCatalog.ts}
 * in the source TypeScript project. Per-account custom document types are not stored yet, so
 * {@link #documentTypes()} always returns the built-in system catalog, matching the source's
 * current behavior (no {@code accountId} parameter is honored there either).
 * <p>
 * {@link #matchDocumentType(String)} intentionally preserves the exact three-tier matching order
 * from the source: alias/exact-code match, then a small set of high-precedence direct keyword
 * checks (certificate-of-origin family, bill of lading, packing list, invoice), then a
 * keyword-scored search over the whole catalog. This ordering matters — collapsing it into a
 * single scored pass would change which document type wins on ambiguous text.
 */
public final class DocumentTypeCatalog {

    public static final String OTHER_UNVERIFIED_DOCUMENT = "OTHER_UNVERIFIED_DOCUMENT";

    private static final List<DocumentTypeDefinition> SYSTEM_DOCUMENT_TYPES = List.of(
            // --- COMMERCIAL DOCUMENTS ---
            new DocumentTypeDefinition(
                    "COMMERCIAL_INVOICE", "Commercial Invoice", DocumentTypeCategory.COMMERCIAL,
                    "19 CFR § 141.86", true,
                    List.of("commercial invoice", "invoice no", "unit price", "total amount", "seller", "consignee"),
                    "Standard bill for goods sold by exporter to buyer; required for customs valuation."
            ),
            new DocumentTypeDefinition(
                    "PRO_FORMA_INVOICE", "Pro Forma Invoice", DocumentTypeCategory.COMMERCIAL,
                    "19 CFR § 141.85", false,
                    List.of("pro forma", "proforma invoice", "estimated value", "draft invoice"),
                    "Preliminary invoice sent to buyers in advance of shipment."
            ),
            new DocumentTypeDefinition(
                    "PACKING_LIST", "Packing List / Weight List", DocumentTypeCategory.COMMERCIAL,
                    "19 CFR § 141.86(a)", false,
                    List.of("packing list", "gross weight", "net weight", "cartons", "pallets", "dimensions"),
                    "Itemized list of shipment contents, package counts, and weights."
            ),
            new DocumentTypeDefinition(
                    "PURCHASE_ORDER", "Purchase Order (PO)", DocumentTypeCategory.COMMERCIAL,
                    null, false,
                    List.of("purchase order", "po number", "buyer terms", "order confirmation"),
                    "Commercial contract order issued by buyer to supplier."
            ),
            new DocumentTypeDefinition(
                    "MANUFACTURER_ASSIST_DECLARATION", "Manufacturer Assist & Tooling Declaration", DocumentTypeCategory.COMMERCIAL,
                    "19 U.S.C. § 1401a(b)(1)(C)", false,
                    List.of("assist declaration", "buyer tooling", "dies", "molds", "design work"),
                    "Declaration of buyer-furnished assists, tooling, or engineering services."
            ),
            new DocumentTypeDefinition(
                    "END_USE_STATEMENT", "End-Use Statement / Certificate", DocumentTypeCategory.COMPLIANCE,
                    null, false,
                    List.of("end-use statement", "end use certificate", "statement of end use", "intended use",
                            "final destination and end use", "end user certificate"),
                    "Buyer/consignee declaration of the intended end-use of the goods, used for restricted end-use and military end-use screening."
            ),

            // --- TRANSPORT & LOGISTICS DOCUMENTS ---
            new DocumentTypeDefinition(
                    "OCEAN_BILL_OF_LADING", "Ocean Bill of Lading (B/L)", DocumentTypeCategory.TRANSPORT,
                    "19 CFR Part 141", true,
                    List.of("bol", "b/l", "bill of lading", "ocean bill of lading", "master bl", "house bl",
                            "vessel", "voyage", "port of loading"),
                    "Contract of carriage for maritime cargo."
            ),
            new DocumentTypeDefinition(
                    "AIR_WAYBILL", "Air Waybill (AWB)", DocumentTypeCategory.TRANSPORT,
                    "19 CFR Part 141", true,
                    List.of("air waybill", "mawb", "hawb", "flight no", "airport of discharge"),
                    "Contract of carriage for air freight."
            ),
            new DocumentTypeDefinition(
                    "ARRIVAL_NOTICE", "Arrival Notice & Freight Delivery Order", DocumentTypeCategory.TRANSPORT,
                    null, false,
                    List.of("arrival notice", "freight release", "delivery order", "demurrage", "terminal"),
                    "Notice sent by carrier detailing vessel arrival and port storage terms."
            ),
            new DocumentTypeDefinition(
                    "IN_BOND_MANIFEST_7512", "CBP Form 7512 - Transportation Entry & Manifest", DocumentTypeCategory.TRANSPORT,
                    "19 CFR Part 18", false,
                    List.of("form 7512", "in-bond", "it entry", "t&e", "bonded carrier"),
                    "Customs form for in-bond movements between U.S. ports without duty payment."
            ),

            // --- CBP CUSTOMS FORMS & ENTRIES ---
            new DocumentTypeDefinition(
                    "CBP_FORM_7501_ENTRY_SUMMARY", "CBP Form 7501 - Entry Summary", DocumentTypeCategory.CUSTOMS_CBP,
                    "19 CFR § 141.61", true,
                    List.of("form 7501", "entry summary", "duty paid", "filer code", "entry number"),
                    "Official CBP form declaring entered value, HTS codes, and duties due."
            ),
            new DocumentTypeDefinition(
                    "CBP_FORM_3461_ENTRY_DELIVERY", "CBP Form 3461 - Entry / Immediate Delivery", DocumentTypeCategory.CUSTOMS_CBP,
                    "19 CFR § 142.3", true,
                    List.of("form 3461", "immediate delivery", "cargo release", "customs port"),
                    "Customs release document authorizing cargo removal from port."
            ),
            new DocumentTypeDefinition(
                    "IMPORTER_SECURITY_FILING_ISF", "Importer Security Filing (ISF 10+2)", DocumentTypeCategory.CUSTOMS_CBP,
                    "19 CFR Part 149", false,
                    List.of("isf filing", "10+2", "seller name", "stuffer", "consolidator"),
                    "Advance ocean cargo security filing required 24h prior to vessel loading."
            ),

            // --- RULES OF ORIGIN & FREE TRADE AGREEMENTS ---
            new DocumentTypeDefinition(
                    "USMCA_CERTIFICATE_OF_ORIGIN", "USMCA / CUSMA / T-MEC Certificate of Origin", DocumentTypeCategory.COMPLIANCE,
                    "19 CFR Part 181", false,
                    List.of("usmca", "cusma", "t-mec", "preference criterion", "certifier", "producer"),
                    "Certification of origin qualifying goods for USMCA 0% preferential duty."
            ),
            new DocumentTypeDefinition(
                    "GENERAL_CERTIFICATE_OF_ORIGIN", "General / GSP Certificate of Origin (Form A)", DocumentTypeCategory.COMPLIANCE,
                    "19 CFR § 10.31", false,
                    List.of("certificate of origin", "generalized system of preferences", "gsp", "form a",
                            "combined declaration and certificate", "chamber of commerce", "country of origin stamp",
                            "origin certificate", "made in china", "preference criterion"),
                    "Third-party chamber or official government notarized certificate attesting to manufacturing origin (e.g. GSP Form A)."
            ),

            // --- PARTNER GOVERNMENT AGENCY (PGA) DISCLOSURES ---
            new DocumentTypeDefinition(
                    "FDA_PRIOR_NOTICE_CONFIRMATION", "FDA Prior Notice Confirmation (PNC)", DocumentTypeCategory.PARTNER_GOVERNMENT_AGENCY,
                    "21 CFR Part 1", false,
                    List.of("fda prior notice", "pnc number", "fda product code", "food facility registration"),
                    "Required FDA advance filing for food, medical devices, and cosmetics."
            ),
            new DocumentTypeDefinition(
                    "EPA_FORM_3540_1_IMPORT_REPORT", "EPA Form 3540-1 - Pesticide Notice of Arrival", DocumentTypeCategory.PARTNER_GOVERNMENT_AGENCY,
                    "19 CFR § 12.112", false,
                    List.of("epa form 3540", "pesticide", "active ingredient", "registration number"),
                    "EPA clearance filing for pesticides and antimicrobial devices."
            ),
            new DocumentTypeDefinition(
                    "TSCA_SECTION_13_DECLARATION", "TSCA Section 13 Chemical Certification", DocumentTypeCategory.PARTNER_GOVERNMENT_AGENCY,
                    "19 CFR § 12.121", false,
                    List.of("tsca certification", "toxic substances control", "positive cert", "negative cert"),
                    "Statement certifying compliance with Toxic Substances Control Act."
            ),
            new DocumentTypeDefinition(
                    "USDA_PHYTOSANITARY_CERTIFICATE", "USDA Phytosanitary Certificate", DocumentTypeCategory.PARTNER_GOVERNMENT_AGENCY,
                    "7 CFR Part 319", false,
                    List.of("phytosanitary", "plant protection", "usda inspection", "quarantine"),
                    "Official plant health certificate for agricultural and timber imports."
            ),
            new DocumentTypeDefinition(
                    "FCC_FORM_740_DISCLOSURE", "FCC Form 740 - RF Device Statement", DocumentTypeCategory.PARTNER_GOVERNMENT_AGENCY,
                    "47 CFR § 2.1203", false,
                    List.of("fcc form 740", "radio frequency", "fcc id", "grant of authorization"),
                    "FCC declaration for imported electronic and wireless devices."
            ),

            // --- POST-ENTRY & AUDIT DOCUMENTS ---
            new DocumentTypeDefinition(
                    "CBP_FORM_28_REQUEST_FOR_INFORMATION", "CBP Form 28 - Request for Information", DocumentTypeCategory.POST_ENTRY,
                    "19 CFR § 151.11", false,
                    List.of("form 28", "request for information", "cbp auditor", "30 days response"),
                    "Formal CBP inquiry requesting technical specs, invoices, or cost breakdowns."
            ),
            new DocumentTypeDefinition(
                    "CBP_FORM_29_NOTICE_OF_ACTION", "CBP Form 29 - Notice of Action", DocumentTypeCategory.POST_ENTRY,
                    "19 CFR § 152.2", false,
                    List.of("form 29", "notice of action", "rate advance", "reclassification"),
                    "CBP notice proposing or taking adverse action on classification/value."
            ),
            new DocumentTypeDefinition(
                    "POST_SUMMARY_CORRECTION_PSC_DECK", "Post-Summary Correction (PSC) Submission", DocumentTypeCategory.POST_ENTRY,
                    "19 CFR § 173.4", false,
                    List.of("psc filing", "post summary correction", "delta duty refund"),
                    "Electronic entry summary modification filed prior to CBP liquidation."
            ),
            new DocumentTypeDefinition(
                    "DUTY_DRAWBACK_CLAIM_DOCUMENT", "CBP Form 7551 - Duty Drawback Claim", DocumentTypeCategory.POST_ENTRY,
                    "19 CFR Part 190", false,
                    List.of("form 7551", "duty drawback", "refund claim", "export match"),
                    "Claim for 99% refund of duties paid on imported goods subsequently exported."
            ),
            new DocumentTypeDefinition(
                    OTHER_UNVERIFIED_DOCUMENT, "Other / Unverified Document", DocumentTypeCategory.COMPLIANCE,
                    null, false,
                    List.of(),
                    "Document whose type could not be verified from layout or text content without guessing."
            )
    );

    private static final Map<String, String> ALIAS_TO_CODE = new LinkedHashMap<>();

    static {
        ALIAS_TO_CODE.put("BILL_OF_LADING", "OCEAN_BILL_OF_LADING");
        ALIAS_TO_CODE.put("BOL", "OCEAN_BILL_OF_LADING");
        ALIAS_TO_CODE.put("CERTIFICATE_OF_ORIGIN", "GENERAL_CERTIFICATE_OF_ORIGIN");
        ALIAS_TO_CODE.put("COO", "GENERAL_CERTIFICATE_OF_ORIGIN");
        ALIAS_TO_CODE.put("FORM_A", "GENERAL_CERTIFICATE_OF_ORIGIN");
        ALIAS_TO_CODE.put("GSP", "GENERAL_CERTIFICATE_OF_ORIGIN");
        ALIAS_TO_CODE.put("GSP_FORM_A", "GENERAL_CERTIFICATE_OF_ORIGIN");
    }

    private DocumentTypeCatalog() {
    }

    /**
     * Returns the built-in system catalog. Per-account custom document types are not stored or
     * read yet (matching the source project's current behavior), so no account scoping applies.
     */
    public static List<DocumentTypeDefinition> documentTypes() {
        return SYSTEM_DOCUMENT_TYPES;
    }

    public static Optional<DocumentTypeDefinition> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String upper = code.trim().toUpperCase();
        return SYSTEM_DOCUMENT_TYPES.stream().filter(d -> d.code().equals(upper)).findFirst();
    }

    /**
     * Matches raw text (a file name, header excerpt, or other classification signal) against the
     * catalog. Never returns {@code null}: falls back to {@value #OTHER_UNVERIFIED_DOCUMENT}
     * rather than guessing.
     * <p>
     * Matching order, preserved exactly from the source TypeScript implementation:
     * <ol>
     *   <li>alias/exact catalog-code match</li>
     *   <li>a small set of high-precedence direct keyword checks (certificate-of-origin family,
     *       bill of lading, packing list, invoice) that resolve common ambiguous phrasing before
     *       the general scored pass runs</li>
     *   <li>a keyword-scored search over the whole catalog (name match worth 10 points, each
     *       matching keyword worth 5), returning the highest-scoring definition or
     *       {@value #OTHER_UNVERIFIED_DOCUMENT} when nothing scores above zero</li>
     * </ol>
     */
    public static DocumentTypeDefinition matchDocumentType(String textOrName) {
        String raw = textOrName == null ? "" : textOrName.trim();
        String upper = raw.toUpperCase();
        String norm = raw.toLowerCase().replace('-', ' ').replace('_', ' ');

        String targetCode = ALIAS_TO_CODE.getOrDefault(upper, upper);
        Optional<DocumentTypeDefinition> exactCode = byCode(targetCode);
        if (exactCode.isPresent()) {
            return exactCode.get();
        }

        if (norm.contains("form a") || norm.contains("generalized system of preferences")
                || norm.contains("certificate of origin") || norm.contains("gsp") || norm.contains("coo")) {
            Optional<DocumentTypeDefinition> coo = byCode("GENERAL_CERTIFICATE_OF_ORIGIN");
            if (coo.isPresent()) {
                return coo.get();
            }
        }
        if (norm.contains("bill of lading") || norm.contains("bol") || norm.contains("b/l")) {
            Optional<DocumentTypeDefinition> bol = byCode("OCEAN_BILL_OF_LADING");
            if (bol.isPresent()) {
                return bol.get();
            }
        }
        if (norm.contains("packing list") || norm.contains("weight list")) {
            Optional<DocumentTypeDefinition> packingList = byCode("PACKING_LIST");
            if (packingList.isPresent()) {
                return packingList.get();
            }
        }
        if (norm.contains("invoice") || norm.contains("pro forma") || norm.contains("proforma")) {
            Optional<DocumentTypeDefinition> invoice = byCode("COMMERCIAL_INVOICE");
            if (invoice.isPresent()) {
                return invoice.get();
            }
        }

        DocumentTypeDefinition unverified = byCode(OTHER_UNVERIFIED_DOCUMENT).orElseThrow();
        DocumentTypeDefinition bestMatch = unverified;
        int highestScore = 0;
        for (DocumentTypeDefinition definition : SYSTEM_DOCUMENT_TYPES) {
            if (definition.code().equals(OTHER_UNVERIFIED_DOCUMENT)) {
                continue;
            }
            int score = 0;
            if (norm.contains(definition.name().toLowerCase())) {
                score += 10;
            }
            for (String keyword : definition.keywords()) {
                if (norm.contains(keyword.toLowerCase())) {
                    score += 5;
                }
            }
            if (score > highestScore) {
                highestScore = score;
                bestMatch = definition;
            }
        }
        return bestMatch;
    }
}
