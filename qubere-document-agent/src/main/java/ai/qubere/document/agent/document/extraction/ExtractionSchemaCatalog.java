package ai.qubere.document.agent.document.extraction;

import ai.qubere.document.agent.document.classification.DocumentType;

import java.util.List;
import java.util.Map;

import static ai.qubere.document.agent.document.extraction.ExtractionFieldType.ARRAY;
import static ai.qubere.document.agent.document.extraction.ExtractionFieldType.DATE;
import static ai.qubere.document.agent.document.extraction.ExtractionFieldType.NUMBER;
import static ai.qubere.document.agent.document.extraction.ExtractionFieldType.STRING;

/**
 * Per-document-type extraction field schemas, ported from {@code lib/documents/extractionSchemas.ts}.
 * Each entry names a field the extraction pipeline must attempt to locate; {@code required} fields
 * missing from an extraction should raise a missing-data exception item. {@link DocumentType#OTHER}
 * has no schema — extraction against it is opportunistic.
 */
public final class ExtractionSchemaCatalog {

    private static final Map<DocumentType, List<ExtractionFieldSchema>> SCHEMAS = Map.ofEntries(
            Map.entry(DocumentType.COMMERCIAL_INVOICE, List.of(
                    new ExtractionFieldSchema("seller_name", "Seller / Exporter", true, STRING),
                    new ExtractionFieldSchema("buyer_name", "Buyer / Consignee", true, STRING),
                    new ExtractionFieldSchema("invoice_number", "Invoice Number", true, STRING),
                    new ExtractionFieldSchema("invoice_date", "Invoice Date", true, DATE),
                    new ExtractionFieldSchema("currency", "Currency", true, STRING),
                    new ExtractionFieldSchema("total_value", "Total Invoice Value", true, NUMBER),
                    new ExtractionFieldSchema("incoterm", "Incoterm", false, STRING),
                    new ExtractionFieldSchema("line_items", "Line Items", true, ARRAY)
            )),
            Map.entry(DocumentType.PACKING_LIST, List.of(
                    new ExtractionFieldSchema("carton_count", "Carton Count", true, NUMBER),
                    new ExtractionFieldSchema("gross_weight", "Gross Weight", true, NUMBER),
                    new ExtractionFieldSchema("net_weight", "Net Weight", false, NUMBER),
                    new ExtractionFieldSchema("package_marks", "Package Marks", false, STRING)
            )),
            Map.entry(DocumentType.BILL_OF_LADING, List.of(
                    new ExtractionFieldSchema("bl_number", "B/L Number", true, STRING),
                    new ExtractionFieldSchema("vessel_name", "Vessel Name", true, STRING),
                    new ExtractionFieldSchema("voyage", "Voyage Number", false, STRING),
                    new ExtractionFieldSchema("port_of_loading", "Port of Loading", true, STRING),
                    new ExtractionFieldSchema("port_of_discharge", "Port of Discharge", true, STRING),
                    new ExtractionFieldSchema("container_numbers", "Container Numbers", false, ARRAY),
                    new ExtractionFieldSchema("on_board_date", "On-Board Date", true, DATE)
            )),
            Map.entry(DocumentType.AIR_WAYBILL, List.of(
                    new ExtractionFieldSchema("awb_number", "AWB Number", true, STRING),
                    new ExtractionFieldSchema("shipper_name", "Shipper", true, STRING),
                    new ExtractionFieldSchema("consignee_name", "Consignee", true, STRING),
                    new ExtractionFieldSchema("airport_of_origin", "Airport of Departure", true, STRING),
                    new ExtractionFieldSchema("airport_of_dest", "Airport of Destination", true, STRING),
                    new ExtractionFieldSchema("gross_weight", "Gross Weight", true, NUMBER)
            )),
            Map.entry(DocumentType.CERTIFICATE_OF_ORIGIN, List.of(
                    new ExtractionFieldSchema("exporter_name", "Exporter", true, STRING),
                    new ExtractionFieldSchema("consignee_name", "Consignee", true, STRING),
                    new ExtractionFieldSchema("country_of_origin", "Country of Origin", true, STRING),
                    new ExtractionFieldSchema("goods_description", "Description of Goods", true, STRING),
                    new ExtractionFieldSchema("hs_code", "HS Code", false, STRING)
            )),
            Map.entry(DocumentType.PHYTOSANITARY_CERTIFICATE, List.of(
                    new ExtractionFieldSchema("issuing_authority", "Issuing Authority", true, STRING),
                    new ExtractionFieldSchema("exporter_name", "Exporter", true, STRING),
                    new ExtractionFieldSchema("goods_description", "Description of Goods", true, STRING),
                    new ExtractionFieldSchema("issue_date", "Issue Date", true, DATE)
            )),
            Map.entry(DocumentType.FUMIGATION_CERTIFICATE, List.of(
                    new ExtractionFieldSchema("treatment_method", "Treatment Method", true, STRING),
                    new ExtractionFieldSchema("chemical_used", "Chemical / Fumigant", true, STRING),
                    new ExtractionFieldSchema("treatment_date", "Treatment Date", true, DATE),
                    new ExtractionFieldSchema("goods_description", "Description of Goods", true, STRING)
            )),
            Map.entry(DocumentType.CUSTOMS_BOND, List.of(
                    new ExtractionFieldSchema("bond_number", "Bond Number", true, STRING),
                    new ExtractionFieldSchema("surety_name", "Surety Company", true, STRING),
                    new ExtractionFieldSchema("bond_amount", "Bond Amount", true, NUMBER),
                    new ExtractionFieldSchema("effective_date", "Effective Date", true, DATE)
            )),
            Map.entry(DocumentType.POWER_OF_ATTORNEY, List.of(
                    new ExtractionFieldSchema("grantor_name", "Grantor", true, STRING),
                    new ExtractionFieldSchema("grantee_name", "Grantee (Broker)", true, STRING),
                    new ExtractionFieldSchema("effective_date", "Effective Date", false, DATE)
            )),
            Map.entry(DocumentType.ENTRY_SUMMARY, List.of(
                    new ExtractionFieldSchema("entry_number", "Entry Number", true, STRING),
                    new ExtractionFieldSchema("entry_date", "Entry Date", true, DATE),
                    new ExtractionFieldSchema("importer_name", "Importer of Record", true, STRING),
                    new ExtractionFieldSchema("port_of_entry", "Port of Entry", true, STRING),
                    new ExtractionFieldSchema("total_duties", "Total Duties & Taxes", true, NUMBER)
            )),
            Map.entry(DocumentType.ISF, List.of(
                    new ExtractionFieldSchema("importer_of_record", "Importer of Record", true, STRING),
                    new ExtractionFieldSchema("seller_name", "Seller", true, STRING),
                    new ExtractionFieldSchema("buyer_name", "Buyer", true, STRING),
                    new ExtractionFieldSchema("manufacturer_name", "Manufacturer", true, STRING),
                    new ExtractionFieldSchema("ship_to_party", "Ship-to Party", true, STRING),
                    new ExtractionFieldSchema("country_of_origin", "Country of Origin", true, STRING),
                    new ExtractionFieldSchema("hs_6_code", "HS-6 Code", true, STRING),
                    new ExtractionFieldSchema("container_stuffing", "Container Stuffing Location", true, STRING),
                    new ExtractionFieldSchema("consolidator_name", "Consolidator", false, STRING)
            ))
    );

    private ExtractionSchemaCatalog() {
    }

    /** Returns the extraction schema for a document type. Empty for {@code OTHER}/unknown/{@code null}. */
    public static List<ExtractionFieldSchema> schemaFor(DocumentType docType) {
        if (docType == null) {
            return List.of();
        }
        return SCHEMAS.getOrDefault(docType, List.of());
    }

    /** Returns only the fields marked {@code required} for a document type. */
    public static List<ExtractionFieldSchema> requiredFieldsFor(DocumentType docType) {
        return schemaFor(docType).stream().filter(ExtractionFieldSchema::required).toList();
    }
}
