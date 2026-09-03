package ai.qubere.document.agent.api;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.document.agent.document.DocumentIntakeAgent;
import ai.qubere.document.agent.document.duplicate.CrossShipmentDuplicate;
import ai.qubere.document.agent.document.duplicate.DuplicateDetectionService;
import ai.qubere.document.agent.document.intake.IntakeSource;
import ai.qubere.document.agent.document.intake.UnassignedIntakeEntity;
import ai.qubere.document.agent.document.intake.UnassignedIntakeRecorder;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingReason;
import ai.qubere.document.agent.document.processing.DocumentBytesSource;
import ai.qubere.document.agent.document.processing.DocumentProcessingWorker;
import ai.qubere.document.agent.document.processing.ProcessingRunEntity;
import ai.qubere.document.agent.document.processing.ProcessingRunService;
import ai.qubere.document.agent.document.storage.DocumentBytesWriter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts a document upload, classifies it via {@code document.intake}, and enqueues it for
 * processing — the entry point that was missing entirely before this:
 * {@link ProcessingRunService#enqueue} previously had no caller except tests, nothing existed to
 * make a document's bytes retrievable by {@code DocumentBytesSource}/{@code DocumentProcessingWorker}
 * in the first place, and {@code document.intake} was never wired into the actual submission flow
 * at all — it existed only as an agent invokable in isolation by tests.
 * <p>
 * The document id is generated here (a caller cannot influence what path segment gets used by the
 * storage backend) rather than accepted from the caller, unlike the TypeScript source where
 * {@code documentId} is an existing database primary key created earlier in that system's own
 * upload flow. This module does not own a "document" domain entity upstream of processing, so there
 * is no pre-existing id to accept; an orchestrating caller that needs to correlate this id with its
 * own record should capture {@link DocumentSubmissionResponse#documentId()} from the response.
 * <p>
 * Bytes are accepted as a raw request body rather than {@code multipart/form-data}: this keeps the
 * endpoint trivial to call from any HTTP client (including the orchestrating framework's own
 * {@code RemoteAgentClient}-style callers) without a multipart codec, at the cost of metadata
 * (filename, MIME type) moving to headers instead of multipart form fields.
 * <p>
 * <strong>A missing shipment id never proceeds to processing</strong> — ported from
 * {@code unassignedIntake.ts}'s core rule: the source used to fall back to the account's newest
 * shipment, and a document filed against the wrong shipment is worse than one that was never filed,
 * because nothing in the record says the target was a guess. Bytes are still stored (nothing is
 * lost), but no {@code ProcessingRunEntity} is created and {@code document.intake} is not invoked;
 * instead an {@link UnassignedIntakeEntity} is recorded for an operator to resolve. This is
 * deliberately narrower than the TS source's {@code IntakePipelineRouter}, which also branches on
 * {@code sourceApp} to dispatch into an entirely separate TMS freight-extraction module in a
 * different application — this service has no such second domain to route into, and reaching into
 * another application's module tree is exactly the kind of thing a separate agent service should
 * not do; see {@code MIGRATION.md} §7's module-boundary decision.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentSubmissionController {

    private static final Logger log = LoggerFactory.getLogger(DocumentSubmissionController.class);

    private final DocumentBytesSource bytesSource;
    private final ProcessingRunService runService;
    private final AgentRuntimeService runtimeService;
    private final UnassignedIntakeRecorder unassignedIntakeRecorder;
    private final DuplicateDetectionService duplicateDetectionService;
    private final ObjectProvider<DocumentProcessingWorker> processingWorkerProvider;

    public DocumentSubmissionController(
            DocumentBytesSource bytesSource,
            ProcessingRunService runService,
            AgentRuntimeService runtimeService,
            UnassignedIntakeRecorder unassignedIntakeRecorder,
            DuplicateDetectionService duplicateDetectionService,
            ObjectProvider<DocumentProcessingWorker> processingWorkerProvider
    ) {
        this.bytesSource = bytesSource;
        this.runService = runService;
        this.runtimeService = runtimeService;
        this.unassignedIntakeRecorder = unassignedIntakeRecorder;
        this.duplicateDetectionService = duplicateDetectionService;
        this.processingWorkerProvider = processingWorkerProvider;
    }

    @PostMapping
    public ResponseEntity<DocumentSubmissionResponse> submit(
            @RequestBody byte[] body,
            @RequestHeader(name = "X-Document-Filename", required = false) String fileName,
            @RequestHeader(name = "X-Document-Mime-Type", required = false) String mimeType,
            @RequestHeader(name = "X-Shipment-Id", required = false) String shipmentId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(name = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @RequestParam(name = "profile", required = false, defaultValue = "STANDARD") ProcessingProfile profile,
            @RequestParam(name = "reason", required = false, defaultValue = "INITIAL") ProcessingReason reason
    ) {
        if (!(bytesSource instanceof DocumentBytesWriter writer)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No writable document storage is configured (document-agent.storage.type=none or a read-only backend); "
                            + "uploads cannot be accepted.");
        }
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("Request body must contain the document bytes.");
        }

        String documentId = UUID.randomUUID().toString();
        writer.store(documentId, fileName, mimeType, body);
        String checksum = sha256Hex(body);

        if (shipmentId == null || shipmentId.isBlank()) {
            UnassignedIntakeEntity unassigned = unassignedIntakeRecorder.record(
                    tenantId, IntakeSource.DOCUMENT_UPLOAD, fileName, null, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(DocumentSubmissionResponse.unassigned(documentId, unassigned.getId(), unassigned.getDescription()));
        }

        String detectedDocType = classify(documentId, fileName, mimeType, shipmentId, tenantId, actorId, correlationId);
        List<CrossShipmentDuplicate> duplicates = duplicateDetectionService.findCrossShipmentDuplicates(
                tenantId, checksum, shipmentId, documentId);

        ProcessingRunEntity run = runService.enqueue(documentId, shipmentId, checksum, tenantId, actorId, correlationId, profile, reason);
        advanceProcessing();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DocumentSubmissionResponse.enqueued(documentId, run.getId(), run.getState().name(), detectedDocType, duplicates));
    }

    /**
     * Ported from {@code advanceProcessing.ts}'s intent (documents should not sit idle for up to a
     * full scheduler tick after upload), adapted rather than copied: the source defers a queue drain
     * until after the HTTP response is flushed, via a Next.js request-scoped {@code after()} hook.
     * Spring MVC has no equivalent primitive, so this runs one {@link DocumentProcessingWorker#tick()}
     * synchronously, before the response is returned — the response carries a little of that tick's
     * latency (one submission attempt) in exchange for guaranteeing the new run is not left waiting
     * for the next {@code @Scheduled} interval. A tick failure here must never fail the upload
     * response: the run is already durably enqueued regardless of whether this immediate attempt
     * succeeds, and the scheduled tick remains the reliable fallback path.
     */
    private void advanceProcessing() {
        DocumentProcessingWorker worker = processingWorkerProvider.getIfAvailable();
        if (worker == null) {
            return;
        }
        try {
            worker.tick();
        } catch (RuntimeException ex) {
            log.warn("Immediate post-upload processing tick failed; the scheduled tick will retry: {}", ex.getMessage());
        }
    }

    /**
     * Best-effort classification: a failure here must never block enqueueing the processing run —
     * intake is a classification signal for operators, not a gate on whether extraction proceeds.
     */
    private String classify(
            String documentId, String fileName, String mimeType, String shipmentId,
            String tenantId, String actorId, String correlationId
    ) {
        try {
            AgentExecutionContext context = new AgentExecutionContext(
                    UUID.randomUUID().toString(), tenantId, actorId, correlationId, Instant.now(), Map.of());
            Map<String, Object> input = new java.util.LinkedHashMap<>();
            input.put("documentId", documentId);
            input.put("shipmentId", shipmentId);
            if (fileName != null) {
                input.put("fileName", fileName);
            }
            if (mimeType != null) {
                input.put("mimeType", mimeType);
            }
            AgentOutput output = runtimeService.run(
                    DocumentIntakeAgent.AGENT_ID, null, new GenericAgentInput(input), context, null);
            if (output instanceof AgentResult<?> result && result.value() instanceof Map<?, ?> value) {
                Object detectedTypes = value.get("detectedTypes");
                if (detectedTypes instanceof java.util.List<?> list && !list.isEmpty()) {
                    return String.valueOf(list.get(0));
                }
            }
            return null;
        } catch (RuntimeException ex) {
            log.warn("Document intake classification failed for document {}: {}", documentId, ex.getMessage());
            return null;
        }
    }

    private String sha256Hex(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is a JDK-mandated algorithm (every conforming JVM provides it), so this is
            // unreachable in practice; a null checksum simply disables duplicate detection for this
            // upload rather than failing it.
            log.error("SHA-256 is unavailable in this JVM; duplicate detection is disabled for this upload.", ex);
            return null;
        }
    }
}
