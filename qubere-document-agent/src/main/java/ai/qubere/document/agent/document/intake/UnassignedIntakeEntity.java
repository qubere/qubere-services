package ai.qubere.document.agent.document.intake;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A document intake whose target shipment could not be determined, ported from
 * {@code unassignedIntake.ts}. The original comment there is worth preserving verbatim as the
 * reason this exists at all: <em>"The resolver used to guess the account's newest shipment. A
 * document filed against the wrong shipment is worse than one that was never filed, because
 * nothing in the record says the target was a guess."</em> This module never guesses a shipment;
 * a document that arrives without one is recorded here instead of silently proceeding, so an
 * operator can assign it (or close it out) deliberately.
 * <p>
 * A dedicated table in this module, not a shared "exceptions" table in {@code qubere-agent-storage}
 * — no such shared exception-queue concept exists in this framework yet, and inventing one
 * speculatively for a single caller would be the same mistake already avoided elsewhere (see
 * {@code MIGRATION.md}'s fact-store question, which this is related to but distinct from). If
 * {@code qubere-compliance-agent} later needs an equivalent queue, promoting this to shared
 * infrastructure is a deliberate follow-up, not something to guess at now.
 */
@Entity
@Table(name = "unassigned_document_intake")
public class UnassignedIntakeEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 32, nullable = false)
    private IntakeSource source;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "doc_type", length = 64)
    private String docType;

    @Column(name = "requested_shipment_id", length = 64)
    private String requestedShipmentId;

    @Column(name = "description", length = 1000, nullable = false)
    private String description;

    /** {@code Open} until an operator assigns a shipment or explicitly closes the item. */
    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public IntakeSource getSource() {
        return source;
    }

    public void setSource(IntakeSource source) {
        this.source = source;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getRequestedShipmentId() {
        return requestedShipmentId;
    }

    public void setRequestedShipmentId(String requestedShipmentId) {
        this.requestedShipmentId = requestedShipmentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
