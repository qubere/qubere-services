package ai.qubere.document.agent.api;

import ai.qubere.document.agent.document.duplicate.CrossShipmentDuplicate;

import java.util.List;

/**
 * {@code processingRunId}/{@code state}/{@code detectedDocType} are populated when the document was
 * enqueued for processing; {@code unassignedIntakeId}/{@code unassignedIntakeDescription} are
 * populated instead when no shipment id was supplied (see
 * {@link DocumentSubmissionController}'s javadoc for why that never falls back to a guess). Exactly
 * one of the two pairs is non-null in any given response. {@code crossShipmentDuplicates} is always
 * present (possibly empty) on an enqueued response — a non-blocking signal only; the upload always
 * proceeds regardless of what it contains (see {@code DuplicateDetectionService}).
 */
public record DocumentSubmissionResponse(
        String documentId,
        String processingRunId,
        String state,
        String detectedDocType,
        List<CrossShipmentDuplicate> crossShipmentDuplicates,
        String unassignedIntakeId,
        String unassignedIntakeDescription
) {
    public static DocumentSubmissionResponse enqueued(
            String documentId, String processingRunId, String state, String detectedDocType,
            List<CrossShipmentDuplicate> crossShipmentDuplicates
    ) {
        return new DocumentSubmissionResponse(documentId, processingRunId, state, detectedDocType, crossShipmentDuplicates, null, null);
    }

    public static DocumentSubmissionResponse unassigned(String documentId, String unassignedIntakeId, String description) {
        return new DocumentSubmissionResponse(documentId, null, null, null, List.of(), unassignedIntakeId, description);
    }
}
