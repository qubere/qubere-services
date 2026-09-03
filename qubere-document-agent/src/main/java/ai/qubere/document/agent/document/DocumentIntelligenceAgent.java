package ai.qubere.document.agent.document;

import ai.qubere.agent.ai.AgentAiClient;
import ai.qubere.agent.ai.AgentAiRequestMetadata;
import ai.qubere.agent.ai.AgentPrompt;
import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentDecisionDraft;
import ai.qubere.agent.core.AgentEvidenceDraft;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.document.agent.document.review.ExtractedFieldReading;
import ai.qubere.document.agent.document.review.ExtractionReviewService;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Extracts evidence-backed trade metadata from a document's parsed context, ported from
 * {@code documentIntelligenceAgent.ts}. Reads a {@code documentContext} string (see
 * {@code QubereDocumentContextBuilder}), never raw document bytes — the same "agents read a bounded
 * Qubere-owned context, never the parser's own schema" property {@code classificationExtraction.ts}
 * enforces, just expressed as this agent's own input contract rather than a separate wrapper module.
 * <p>
 * <strong>Deliberately scoped down from the 900+ line TypeScript source</strong>, not a line-for-line
 * port — three real capabilities are intentionally absent, each because it depends on something this
 * pass does not build:
 * <ol>
 *   <li><strong>Visual/layout analysis</strong> (stamps, seals, handwriting, bounding boxes, table
 *       cell geometry) — the source calls Gemini's multimodal vision API directly on the document
 *       image; this pipeline's context is parsed OCR text and tables (see
 *       {@code QubereDocumentContextBuilder}), not raw image bytes, and no vision-capable model is
 *       wired in yet (open question in {@code MIGRATION.md} §12 Q2). The prompt below is honest
 *       about reading text, not instructing a text model to pretend it saw a stamp.</li>
 *   <li><strong>Filing/agency determination</strong> (CBP/FDA/USDA/etc. routing with per-agency
 *       reasoning) — this is a business classification decision the source itself says belongs to
 *       downstream agents this framework has not built (Product Intelligence, HTS Classification,
 *       Compliance &amp; Audit Risk); fabricating an agency determination with no consuming agent to
 *       act on it would be evidence with no purpose.</li>
 *   <li><strong>Entity/relationship graph extraction</strong> (parties, shipment references, and the
 *       relationships between them) — the source's {@code EntityResolutionService}/
 *       {@code ShipmentPartyService} it feeds into do not exist in this framework; extracting a graph
 *       nothing consumes would be untested, unused surface area.</li>
 * </ol>
 * What <em>is</em> preserved: the non-negotiable grounding rules (ground every value in the supplied
 * context, null beats a guess, an unreadable document is reported as {@code failed} rather than
 * padded with placeholder data, low-confidence values are reported not discarded), the document-type
 * taxonomy, the full trade-metadata field list, and structured line items — all through
 * {@link AgentAiClient}'s provider-neutral structured output rather than a Gemini-specific
 * {@code responseSchema}.
 */
@Component
public class DocumentIntelligenceAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {

    public static final String AGENT_ID = "document.intelligence";

    /**
     * Matches {@code DocumentIntakeAgent}'s own 70% review threshold for internal consistency. This
     * is a distinct, separate threshold from Extraction Review's 80% correction-flag cutoff
     * (see {@code MIGRATION.md} §3.4's explicit warning not to conflate the two) — Extraction
     * Review is a human-correction workflow this module has not built yet, not this agent's own
     * pass/fail gate.
     */
    private static final int REVIEW_THRESHOLD = 70;

    private static final String SYSTEM_PROMPT = """
            ROLE
            You are Qubere Document Intelligence, an AI engine that reads trade and customs \
            documents the way an experienced customs analyst would. You are given a parsed, \
            budget-bounded textual representation of the document (OCR text and extracted \
            tables) -- not the original image, not a filename, not upload metadata. You are the \
            first agent in a multi-stage pipeline; downstream agents handle HTS classification, \
            origin/trade-agreement determination, valuation, and compliance risk. Do not \
            classify products, assign HTS codes, determine filing agency, or make compliance \
            determinations yourself -- extract and classify only.

            NON-NEGOTIABLE GROUNDING RULES
            1. Every value you output must be traceable to text actually present in the supplied \
            context. Never infer a value from general knowledge of what this document type \
            "usually" contains.
            2. If a field is not present, or you are not confident you read it correctly, output \
            null and list it in missingCriticalFields or warnings. Null beats a plausible-looking \
            guess, every time.
            3. If the supplied context is empty, truncated beyond usability, or does not resemble \
            a trade document at all, set extractionStatus to "failed" and explain why in \
            warnings. Do not fill the schema with placeholder or example data.
            4. Do not discard low-confidence extractions -- include them with an honest \
            confidence score rather than omitting them.
            5. If the context indicates multiple distinct documents were stitched together, say \
            so explicitly in warnings rather than merging them into one classification.

            DOCUMENT CLASSIFICATION
            Classify using this taxonomy, or "other" with a one-line note if nothing genuinely fits:
              Commercial: Commercial Invoice, Pro Forma Invoice, Packing List, Shipper's Letter of \
            Instruction, Letter of Credit, Insurance Certificate
              Transport: Bill of Lading (Ocean), Airway Bill, Inland/Truck Bill of Lading (CMR), \
            Dock/Warehouse Receipt, Arrival Notice, Delivery Order
              Origin & preference: Certificate of Origin, USMCA/NAFTA Certificate, GSP \
            Certificate, other FTA Certificate
              Customs & regulatory filings: Customs Entry Summary (CBP Form 7501), Importer \
            Security Filing (ISF/10+2), Power of Attorney, Binding Ruling Letter/HTS \
            Classification Ruling, Duty Drawback Claim, Anti-Dumping/Countervailing Duty \
            Documentation
              Partner government agency (PGA): Phytosanitary Certificate, FDA Prior Notice, \
            Material Safety Data Sheet (MSDS/SDS), Fish & Wildlife Declaration (Form 3-177), \
            Import/Export License or Permit

            TRADE METADATA
            Extract, only where explicitly present in the context: country of origin (never \
            inferred from shipping route, language, or currency), country of export, country of \
            destination, HS/HTS code, shipper, consignee, importer of record, notify party, \
            invoice number, PO number, document date, currency, total value, incoterms, port of \
            loading, port of discharge, vessel name, voyage number, container number, carrier, \
            transport document number, total weight, net weight, total quantity, carton count, \
            on-board date, end-use statement (verbatim), and any other narrative/remarks text not \
            captured by another field.

            LINE ITEMS
            Extract every line item table row you find: line number, SKU, description, quantity, \
            unit price, total amount, unit of measure, country of origin, HTS code -- in the order \
            given, never re-ordered or de-duplicated by guesswork.

            VALIDATION
            Report arithmetic or consistency issues you notice (line items vs. stated totals, \
            inconsistent dates, currency inconsistency) as warnings; never alter the extracted \
            values themselves because of a validation finding.

            CONFIDENCE
            documentClassification.confidence is 0-100, an honest reflection of how certain the \
            classification is from the supplied text -- not an aspirational number.
            """;

    private final ObjectProvider<AgentAiClient> aiClientProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;
    private final ObjectProvider<ExtractionReviewService> reviewServiceProvider;

    public DocumentIntelligenceAgent(
            ObjectProvider<AgentAiClient> aiClientProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<ExtractionReviewService> reviewServiceProvider
    ) {
        this.aiClientProvider = aiClientProvider;
        this.objectMapperProvider = objectMapperProvider;
        this.reviewServiceProvider = reviewServiceProvider;
    }

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            AGENT_ID,
            "Document Intelligence Agent",
            "0.2.0",
            "Extracts evidence-backed trade metadata and line-item facts from parsed document context.",
            AgentRiskLevel.MEDIUM,
            Set.of("document-extraction", "trade-metadata", "line-items", "evidence")
    );

    @Override
    public AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
        Map<String, Object> values = input.values();
        String documentText = text(values.get("documentContext"));
        String fileName = text(values.get("fileName"));
        AgentAiClient aiClient = aiClientProvider.getIfAvailable();

        DocumentIntelligenceExtraction extraction = aiClient == null
                ? notConfiguredPlaceholder()
                : aiExtraction(aiClient, context, fileName, documentText);

        Integer confidence = extraction.documentClassification() == null ? null : extraction.documentClassification().confidence();
        boolean reviewRequired = "failed".equals(extraction.extractionStatus())
                || confidence == null
                || confidence < REVIEW_THRESHOLD;
        String status = reviewRequired ? "Review Required" : "Completed";

        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        Map<String, Object> output = new java.util.LinkedHashMap<>();
        output.put("packetId", textOrDefault(values.get("packetId"), "pkt_pending"));
        output.put("shipmentId", text(values.get("shipmentId")));
        output.put("documentId", text(values.get("documentId")));
        output.put("status", status);
        output.put("extractionStatus", extraction.extractionStatus());
        output.put("documentClassification", toMap(objectMapper, extraction.documentClassification()));
        output.put("tradeMetadata", toMap(objectMapper, extraction.tradeMetadata()));
        output.put("lineItems", extraction.lineItems() == null ? List.of()
                : extraction.lineItems().stream().map(item -> toMap(objectMapper, item)).toList());
        output.put("missingCriticalFields", extraction.missingCriticalFields() == null ? List.of() : extraction.missingCriticalFields());
        output.put("warnings", extraction.warnings() == null ? List.of() : extraction.warnings());
        output.put("migrationStatus", aiClient == null ? "placeholder-no-ai-client" : "ai-backed");

        String documentId = text(values.get("documentId"));
        if (!documentId.isBlank() && extraction.tradeMetadata() != null) {
            ExtractionReviewService reviewService = reviewServiceProvider.getIfAvailable();
            if (reviewService != null) {
                // Best-effort: recording readings for review must never fail the extraction itself.
                try {
                    reviewService.recordMachineReadings(documentId, flatten(extraction.tradeMetadata(), confidence));
                } catch (RuntimeException ex) {
                    // Swallowed deliberately -- see the try block's own comment.
                }
            }
        }

        double decisionConfidence = confidence == null ? 0.0d : confidence / 100.0d;
        return new AgentResult<>(
                output,
                new AgentDecisionDraft(
                        reviewRequired ? "DOCUMENT_EXTRACTION_REVIEW_REQUIRED" : "DOCUMENT_EXTRACTION_COMPLETED",
                        reviewRequired
                                ? "Extraction confidence is below the review threshold or extraction failed; a human should confirm the reading."
                                : "Document Intelligence extracted trade metadata with sufficient confidence to proceed.",
                        decisionConfidence
                ),
                List.of(new AgentEvidenceDraft(
                        "documentContext",
                        documentText.isBlank() ? "missing" : "supplied",
                        "Extraction is grounded in the supplied documentContext text; a missing context always yields a failed/low-confidence extraction, never a guess."
                )),
                Map.of("agentId", AGENT_ID, "source", "app-frontend migration", "aiBacked", aiClient != null)
        );
    }

    private DocumentIntelligenceExtraction aiExtraction(AgentAiClient aiClient, AgentExecutionContext context, String fileName, String documentText) {
        if (documentText.isBlank()) {
            // Never send an empty context to the model and let it fabricate a plausible-looking
            // document out of nothing -- the grounding rule is enforced here, not just requested
            // in the prompt text.
            return new DocumentIntelligenceExtraction(
                    "failed", null, null, List.of(), List.of(),
                    List.of("No document context was supplied; nothing was available to extract from.")
            );
        }
        AgentPrompt prompt = new AgentPrompt(
                SYSTEM_PROMPT,
                "File name: " + fileName + "\n\nDocument context:\n" + documentText,
                Map.of("fileName", fileName, "hasDocumentContext", true)
        );
        return aiClient.generate(prompt, DocumentIntelligenceExtraction.class, AgentAiRequestMetadata.from(context));
    }

    /**
     * Every field null, not empty strings -- the previous scaffold's placeholder used {@code ""}
     * for unknown fields, which is exactly the fabricated-absence anti-pattern
     * {@code MIGRATION.md} §3.4 warns against ("no confidence becomes 0 or 50 ... absence is
     * preserved, never fabricated"). An unconfigured AI provider is not evidence of an empty
     * document; it is evidence of nothing at all, which null represents honestly.
     */
    private DocumentIntelligenceExtraction notConfiguredPlaceholder() {
        return new DocumentIntelligenceExtraction(
                "failed", null, null, List.of(), List.of(),
                List.of("No AgentAiClient is configured; no extraction was attempted.")
        );
    }

    private Map<String, Object> toMap(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * Flattens the 28-field {@link TradeMetadataExtraction} record into individual field readings
     * for {@link ExtractionReviewService}. There is no field-level confidence in this record (only
     * {@code documentClassification.confidence} exists, unlike the source's per-entity confidence,
     * which this pass does not extract — see the class javadoc's entity/relationship omission) so
     * the overall classification confidence is used as every field's confidence; a deliberate
     * simplification, not a claim that each field was independently scored.
     */
    private List<ExtractedFieldReading> flatten(TradeMetadataExtraction metadata, Integer classificationConfidence) {
        List<ExtractedFieldReading> readings = new ArrayList<>();
        readings.add(new ExtractedFieldReading("countryOfOrigin", metadata.countryOfOrigin(), classificationConfidence));
        readings.add(new ExtractedFieldReading("countryOfExport", metadata.countryOfExport(), classificationConfidence));
        readings.add(new ExtractedFieldReading("countryOfDestination", metadata.countryOfDestination(), classificationConfidence));
        readings.add(new ExtractedFieldReading("hsHtsCode", metadata.hsHtsCode(), classificationConfidence));
        readings.add(new ExtractedFieldReading("shipper", metadata.shipper(), classificationConfidence));
        readings.add(new ExtractedFieldReading("consignee", metadata.consignee(), classificationConfidence));
        readings.add(new ExtractedFieldReading("importerOfRecord", metadata.importerOfRecord(), classificationConfidence));
        readings.add(new ExtractedFieldReading("notifyParty", metadata.notifyParty(), classificationConfidence));
        readings.add(new ExtractedFieldReading("invoiceNumber", metadata.invoiceNumber(), classificationConfidence));
        readings.add(new ExtractedFieldReading("poNumber", metadata.poNumber(), classificationConfidence));
        readings.add(new ExtractedFieldReading("documentDate", metadata.documentDate(), classificationConfidence));
        readings.add(new ExtractedFieldReading("currency", metadata.currency(), classificationConfidence));
        readings.add(new ExtractedFieldReading("totalValue", metadata.totalValue() == null ? null : String.valueOf(metadata.totalValue()), classificationConfidence));
        readings.add(new ExtractedFieldReading("incoterms", metadata.incoterms(), classificationConfidence));
        readings.add(new ExtractedFieldReading("portOfLoading", metadata.portOfLoading(), classificationConfidence));
        readings.add(new ExtractedFieldReading("portOfDischarge", metadata.portOfDischarge(), classificationConfidence));
        readings.add(new ExtractedFieldReading("vesselName", metadata.vesselName(), classificationConfidence));
        readings.add(new ExtractedFieldReading("voyageNumber", metadata.voyageNumber(), classificationConfidence));
        readings.add(new ExtractedFieldReading("containerNumber", metadata.containerNumber(), classificationConfidence));
        readings.add(new ExtractedFieldReading("carrier", metadata.carrier(), classificationConfidence));
        readings.add(new ExtractedFieldReading("transportDocumentNumber", metadata.transportDocumentNumber(), classificationConfidence));
        readings.add(new ExtractedFieldReading("totalWeight", metadata.totalWeight(), classificationConfidence));
        readings.add(new ExtractedFieldReading("netWeight", metadata.netWeight(), classificationConfidence));
        readings.add(new ExtractedFieldReading("totalQuantity", metadata.totalQuantity(), classificationConfidence));
        readings.add(new ExtractedFieldReading("cartonCount", metadata.cartonCount(), classificationConfidence));
        readings.add(new ExtractedFieldReading("onBoardDate", metadata.onBoardDate(), classificationConfidence));
        readings.add(new ExtractedFieldReading("endUseStatement", metadata.endUseStatement(), classificationConfidence));
        readings.add(new ExtractedFieldReading("documentNarrativeText", metadata.documentNarrativeText(), classificationConfidence));
        return readings;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    /** The AI response schema. Nullable boxed types throughout -- absence must survive as null. */
    public record DocumentIntelligenceExtraction(
            String extractionStatus,
            DocumentClassification documentClassification,
            TradeMetadataExtraction tradeMetadata,
            List<LineItemExtraction> lineItems,
            List<String> missingCriticalFields,
            List<String> warnings
    ) {
    }

    public record DocumentClassification(
            String documentType,
            Integer confidence,
            String notes
    ) {
    }

    public record TradeMetadataExtraction(
            String countryOfOrigin,
            String countryOfExport,
            String countryOfDestination,
            String hsHtsCode,
            String shipper,
            String consignee,
            String importerOfRecord,
            String notifyParty,
            String invoiceNumber,
            String poNumber,
            String documentDate,
            String currency,
            Double totalValue,
            String incoterms,
            String portOfLoading,
            String portOfDischarge,
            String vesselName,
            String voyageNumber,
            String containerNumber,
            String carrier,
            String transportDocumentNumber,
            String totalWeight,
            String netWeight,
            String totalQuantity,
            String cartonCount,
            String onBoardDate,
            String endUseStatement,
            String documentNarrativeText
    ) {
    }

    public record LineItemExtraction(
            Integer lineNumber,
            String sku,
            String description,
            Double quantity,
            Double unitPrice,
            Double totalAmount,
            String unitOfMeasure,
            String countryOfOrigin,
            String htsCode
    ) {
    }
}

