package ai.qubere.document.agent.document.processing;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The current active parse result for a document, keyed by document id so each new qualifying
 * parse (see {@code QualityGateEvaluator#qualifiesAsActive}) simply replaces the previous row —
 * mirroring the source's {@code promoteToActive()} concept without needing a separate
 * "which version is active" pointer.
 * <p>
 * <strong>Deliberately a single row per document, not the source's full multi-artifact scheme</strong>
 * ({@code artifactStore.ts} persists 6 distinct artifact types: canonical JSON, normalized JSON,
 * markdown, tables JSON, per-table HTML, quality report). This stores only the normalized result —
 * exactly what {@link ai.qubere.document.agent.document.context.QubereDocumentContextBuilder} needs
 * to assemble extraction context — and is a deliberately smaller scope than the full artifact store,
 * which remains a documented follow-up in {@code MIGRATION.md}.
 * <p>
 * <strong>{@code @JdbcTypeCode(SqlTypes.LONGVARCHAR)}, not {@code @Lob}, on
 * {@code normalizedResultJson}.</strong> Verified against a real
 * {@code ddl-auto=validate} boot (see {@code ProcessingRunDdlValidationTest}): Hibernate 6's
 * {@code PostgreSQLDialect} maps a plain {@code @Lob} String to Postgres's {@code oid} large-object
 * type, not {@code text} — so a manual DDL script declaring {@code text} (as this module's does,
 * matching the convention already used for the framework's own {@code @Lob} JSON columns in
 * {@code qubere-agent-storage}) fails schema validation at startup against a real PostgreSQL
 * database. {@code LONGVARCHAR} is the mapping Hibernate actually expects for a {@code text}
 * column on both {@code PostgreSQLDialect} and H2's PostgreSQL-compatibility mode. This is a real,
 * framework-wide finding — the same {@code @Lob}+{@code text} pattern is used in roughly a dozen
 * other entities in {@code qubere-agent-storage}'s persistence layer, which were not changed here
 * (out of scope for this module, and there is no live PostgreSQL instance in this environment to
 * verify a broader fix) but should be revisited; see {@code MIGRATION.md}. Oracle's manual DDL
 * still declares {@code clob} for this column, matching Oracle's own JDBC driver reporting; that
 * combination has not been verified against a live Oracle instance.
 */
@Entity
@Table(name = "document_parse_result")
public class DocumentParseResultEntity {

    @Id
    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Column(name = "processing_run_id", length = 64, nullable = false)
    private String processingRunId;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "normalized_result_json", nullable = false)
    private String normalizedResultJson;

    @Column(name = "quality_outcome", length = 32, nullable = false)
    private String qualityOutcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getProcessingRunId() {
        return processingRunId;
    }

    public void setProcessingRunId(String processingRunId) {
        this.processingRunId = processingRunId;
    }

    public String getNormalizedResultJson() {
        return normalizedResultJson;
    }

    public void setNormalizedResultJson(String normalizedResultJson) {
        this.normalizedResultJson = normalizedResultJson;
    }

    public String getQualityOutcome() {
        return qualityOutcome;
    }

    public void setQualityOutcome(String qualityOutcome) {
        this.qualityOutcome = qualityOutcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
