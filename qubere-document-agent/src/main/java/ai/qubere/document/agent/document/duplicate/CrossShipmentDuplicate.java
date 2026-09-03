package ai.qubere.document.agent.document.duplicate;

/**
 * A cross-shipment duplicate finding: another document with the same content checksum, filed
 * against a different shipment. Ported from {@code duplicateDetection.ts}'s
 * {@code CrossShipmentDuplicate}, deliberately narrower: the source also resolves
 * {@code shipmentNumber} and {@code fileName} via joins into {@code Shipment}/{@code ShipmentDocument}
 * tables this module does not own (shipment data lives in the orchestrating system, not here; file
 * names live in whichever {@code DocumentBytesSource} is configured). Exposing {@code documentId}/
 * {@code shipmentId} is enough for a caller that does own that data to resolve a human-readable label
 * itself — inventing a join into data this service will never own would be the same mistake already
 * avoided for the fact-store and cloud-storage questions elsewhere in this migration.
 */
public record CrossShipmentDuplicate(
        String documentId,
        String shipmentId,
        java.time.Instant createdAt
) {
}
