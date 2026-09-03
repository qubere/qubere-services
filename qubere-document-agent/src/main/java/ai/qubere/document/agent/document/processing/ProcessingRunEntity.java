package ai.qubere.document.agent.document.processing;

import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingReason;
import ai.qubere.document.agent.document.parser.ProcessingRunState;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Durable state for one document-parsing attempt, ported from {@code processingRuns.ts}'s
 * {@code documentParseVersion} row shape.
 * <p>
 * <strong>Concurrency correctness uses JPA's built-in {@link #version} (optimistic locking)</strong>
 * rather than the source's hand-rolled {@code WHERE state = expected} conditional-update SQL. Both
 * achieve the same property — a concurrent or stale writer cannot silently clobber a transition —
 * but {@code @Version} is the idiomatic JPA mechanism for it: Hibernate raises
 * {@code OptimisticLockException} on a conflicting save, which {@code ProcessingRunService} treats
 * as "someone else already advanced this run" rather than a hard failure. Reimplementing the raw
 * conditional-update SQL by hand here would have been the "blind migration" mistake — porting a
 * mechanism the target platform already has a first-class answer for.
 */
@Entity
@Table(name = "document_processing_run")
public class ProcessingRunEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    /** Uniquely identifies "this document, this profile, this reason" so re-submission is idempotent. */
    @Column(name = "idempotency_key", length = 128, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    /**
     * Which shipment this document belongs to. Added so downstream extraction
     * ({@code document.intelligence}, and any future compliance-agent consumer) can correlate a
     * parse result back to a shipment without a second lookup — a run divorced from its shipment is
     * not meaningful in a trade-compliance system. Nullable because a caller-supplied value is not
     * guaranteed (see {@code UnassignedIntakeEntity}, which is where a genuinely missing shipment id
     * is recorded instead of silently proceeding).
     */
    @Column(name = "shipment_id", length = 64)
    private String shipmentId;

    /**
     * SHA-256 of the uploaded bytes, computed once at submission time. Ported from
     * {@code documentProcessingWorker.ts}'s {@code contentSha256} column, but with a second use the
     * source's own column never had until {@code duplicateDetection.ts} was written against it: the
     * same hash also backs {@link ai.qubere.document.agent.document.duplicate.DuplicateDetectionService}'s
     * cross-shipment duplicate lookup, so both concerns share the one value computed at upload time
     * rather than hashing the bytes twice.
     */
    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 32, nullable = false)
    private ProcessingRunState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 32, nullable = false)
    private ProcessingReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile", length = 32, nullable = false)
    private ProcessingProfile profile;

    @Column(name = "external_task_id", length = 200)
    private String externalTaskId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "poll_attempt_count", nullable = false)
    private int pollAttemptCount;

    @Column(name = "next_poll_at")
    private Instant nextPollAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /** Bumped every time a worker claims/advances this run; drives stale-run reclaim. */
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = contentSha256;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public ProcessingRunState getState() {
        return state;
    }

    public void setState(ProcessingRunState state) {
        this.state = state;
    }

    public ProcessingReason getReason() {
        return reason;
    }

    public void setReason(ProcessingReason reason) {
        this.reason = reason;
    }

    public ProcessingProfile getProfile() {
        return profile;
    }

    public void setProfile(ProcessingProfile profile) {
        this.profile = profile;
    }

    public String getExternalTaskId() {
        return externalTaskId;
    }

    public void setExternalTaskId(String externalTaskId) {
        this.externalTaskId = externalTaskId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getPollAttemptCount() {
        return pollAttemptCount;
    }

    public void setPollAttemptCount(int pollAttemptCount) {
        this.pollAttemptCount = pollAttemptCount;
    }

    public Instant getNextPollAt() {
        return nextPollAt;
    }

    public void setNextPollAt(Instant nextPollAt) {
        this.nextPollAt = nextPollAt;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public void setHeartbeatAt(Instant heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
