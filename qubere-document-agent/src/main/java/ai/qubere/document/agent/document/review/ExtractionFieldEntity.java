package ai.qubere.document.agent.document.review;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable stored reading of one extracted field, ported from {@code extractionReview.ts}'s
 * {@code ExtractionField} row shape. A correction is stored as a <strong>new row</strong>, never an
 * update to an existing one — the machine's original reading must never be overwritten, and the
 * correction history is immutable by construction. {@code ExtractionReviewFields.buildReviewFields}
 * (not this entity) decides which reading is "current."
 * <p>
 * {@code bboxJson} uses {@code @JdbcTypeCode(SqlTypes.LONGVARCHAR)}, not {@code @Lob}, for the same
 * verified reason as {@code DocumentParseResultEntity.normalizedResultJson} — see that entity's
 * javadoc and {@code MIGRATION.md} §13/§17 for the full Postgres/Hibernate finding.
 */
@Entity
@Table(name = "extraction_field")
public class ExtractionFieldEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "field_name", length = 128, nullable = false)
    private String fieldName;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "field_value", nullable = false)
    private String value;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "page_number")
    private Integer pageNumber;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "bbox_json")
    private String bboxJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 32, nullable = false)
    private ExtractionFieldSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getBboxJson() {
        return bboxJson;
    }

    public void setBboxJson(String bboxJson) {
        this.bboxJson = bboxJson;
    }

    public ExtractionFieldSource getSource() {
        return source;
    }

    public void setSource(ExtractionFieldSource source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
