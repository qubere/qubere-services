package ai.qubere.document.agent.document.duplicate;

import ai.qubere.document.agent.document.processing.ProcessingRunEntity;
import ai.qubere.document.agent.document.processing.ProcessingRunRepository;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * Cross-shipment duplicate detection by content checksum, ported from {@code duplicateDetection.ts}.
 * <p>
 * {@code ShipmentDocument.checksum} (SHA-256 of the stored bytes) was, in the source, only ever
 * compared for parse-run idempotency, never queried against other rows — so the same bytes landing
 * on two different shipments (the same invoice attached twice, or emailed and then also uploaded
 * through the portal) went undetected. This is a <strong>non-blocking signal only</strong>: the
 * upload always proceeds regardless of what this returns (see
 * {@code DocumentSubmissionController}), and it is up to the caller whether/how to surface it.
 * <p>
 * Reuses {@link ProcessingRunEntity#getContentSha256()} (computed once at submission time) rather
 * than hashing bytes a second time or introducing a separate "document" table this module does not
 * otherwise need — {@code ProcessingRunEntity} is already the closest thing to a per-upload record
 * this module owns.
 */
@Service
public class DuplicateDetectionService {

    /** Mirrors the source's fetch-20-then-keep-5 shape: look a little further back than what is
     *  ultimately returned, so a handful of same-shipment rows in the window don't starve the
     *  cross-shipment result down to fewer than the intended 5. */
    private static final int FETCH_LIMIT = 20;
    private static final int RESULT_LIMIT = 5;

    private final ProcessingRunRepository repository;

    public DuplicateDetectionService(ProcessingRunRepository repository) {
        this.repository = repository;
    }

    /**
     * Finds other documents in this tenant with the same content checksum, excluding the document's
     * own shipment (a document appearing more than once on its own shipment is not a
     * cross-shipment concern) and, when supplied, its own document id (so a document is never
     * reported as its own duplicate on re-submission).
     *
     * @return up to 5 most recent matches, newest first; empty when {@code checksum} is blank
     */
    public List<CrossShipmentDuplicate> findCrossShipmentDuplicates(
            String tenantId, String checksum, String excludeShipmentId, String excludeDocumentId
    ) {
        if (checksum == null || checksum.isBlank()) {
            return List.of();
        }
        List<ProcessingRunEntity> candidates = repository.findByTenantIdAndContentSha256OrderByCreatedAtDesc(
                tenantId, checksum, Limit.of(FETCH_LIMIT));

        java.util.LinkedHashMap<String, ProcessingRunEntity> newestRunPerDocument = new java.util.LinkedHashMap<>();
        for (ProcessingRunEntity run : candidates) {
            // A single document can have several ProcessingRunEntity rows (e.g. an OCR-retry
            // re-enqueue creates a new run for the same document) -- keep only the newest per
            // document id so a document is never reported as its own duplicate.
            newestRunPerDocument.putIfAbsent(run.getDocumentId(), run);
        }

        return newestRunPerDocument.values().stream()
                .filter(run -> !Objects.equals(run.getShipmentId(), excludeShipmentId))
                .filter(run -> excludeDocumentId == null || !excludeDocumentId.equals(run.getDocumentId()))
                .limit(RESULT_LIMIT)
                .map(run -> new CrossShipmentDuplicate(run.getDocumentId(), run.getShipmentId(), run.getCreatedAt()))
                .toList();
    }
}
