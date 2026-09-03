package ai.qubere.document.agent.document.processing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a minimal JPA-only context for this module's own entities, against a schema created
 * <em>solely</em> from the manual PostgreSQL DDL scripts (never from Hibernate's own
 * {@code ddl-auto}), with {@code ddl-auto=validate}. If {@link ProcessingRunEntity}'s or
 * {@link DocumentParseResultEntity}'s column mapping ever drifts from
 * {@code db/manual/postgres/001_document_processing_run.sql} /
 * {@code 002_document_parse_result.sql}, Hibernate's schema validation fails here rather than only
 * being discovered against a real Postgres deployment. Both entities live in this same package, so
 * {@code @EntityScan}/{@code @EnableJpaRepositories} necessarily cover both -- both manual DDL
 * scripts are loaded together for that reason.
 * <p>
 * Deliberately scoped to a bespoke minimal configuration rather than the full application context:
 * the framework's own {@code agent_execution_record} manual DDL uses a Postgres partial index
 * ({@code create unique index ... where ...}), which is valid, correct PostgreSQL but not
 * something H2's PostgreSQL-compatibility mode can execute — a limitation of testing against H2,
 * not a defect in that script. Booting the full app would pull that script in as well (every
 * entity/table is validated together) and fail for a reason unrelated to what this test verifies.
 * <p>
 * {@code spring.jpa.database-platform} is pinned to {@code PostgreSQLDialect} even though the
 * actual connection is H2: Spring Boot autodetects the Hibernate dialect from the JDBC driver, so
 * without this override an H2 connection gets {@code H2Dialect} regardless of the datasource URL's
 * {@code MODE=PostgreSQL}. That matters for {@link DocumentParseResultEntity#getNormalizedResultJson()}'s
 * {@code @Lob} mapping: {@code PostgreSQLDialect} is what makes Hibernate expect a {@code @Lob}
 * String to validate against a VARCHAR-category column (matching how both H2's PostgreSQL
 * compatibility mode and the real PostgreSQL JDBC driver report a {@code text} column) instead of a
 * true CLOB category, which only Oracle's JDBC driver actually reports for its {@code clob} columns.
 */
@SpringBootTest(
        classes = ProcessingRunDdlValidationTest.MinimalJpaConfig.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/manual/postgres/001_document_processing_run.sql,classpath:db/manual/postgres/002_document_parse_result.sql",
                "spring.datasource.url=jdbc:h2:mem:document_processing_run_ddl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
        }
)
class ProcessingRunDdlValidationTest {

    @Autowired
    private ProcessingRunRepository repository;

    @Autowired
    private DocumentParseResultRepository parseResultRepository;

    @Test
    void manualPostgresDdlMatchesTheJpaEntityMapping() {
        // Reaching this point means Hibernate validated ProcessingRunEntity's mapping against a
        // schema created solely from the manual DDL script -- the real assertion already happened
        // at context startup. A round-trip persist+read confirms the table is actually usable, not
        // merely schema-valid.
        ProcessingRunEntity run = new ProcessingRunEntity();
        run.setId("ddl-check-1");
        run.setIdempotencyKey("ddl-check-key-1");
        run.setDocumentId("doc-1");
        run.setShipmentId("shipment-1");
        run.setContentSha256("abc123");
        run.setTenantId("tenant-1");
        run.setState(ai.qubere.document.agent.document.parser.ProcessingRunState.QUEUED);
        run.setReason(ai.qubere.document.agent.document.parser.ProcessingReason.INITIAL);
        run.setProfile(ai.qubere.document.agent.document.parser.ProcessingProfile.STANDARD);
        run.setAttemptCount(0);
        run.setPollAttemptCount(0);
        run.setCreatedAt(java.time.Instant.now());
        run.setUpdatedAt(java.time.Instant.now());

        repository.saveAndFlush(run);

        assertThat(repository.findById("ddl-check-1")).isPresent();
    }

    @Test
    void manualPostgresDdlMatchesTheDocumentParseResultEntityMapping() {
        DocumentParseResultEntity result = new DocumentParseResultEntity();
        result.setDocumentId("doc-1");
        result.setProcessingRunId("ddl-check-1");
        result.setNormalizedResultJson("{}");
        result.setQualityOutcome("ACCEPT");
        result.setCreatedAt(java.time.Instant.now());

        parseResultRepository.saveAndFlush(result);

        assertThat(parseResultRepository.findById("doc-1")).isPresent();
    }

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = ProcessingRunEntity.class)
    @EnableJpaRepositories(basePackageClasses = ProcessingRunRepository.class)
    static class MinimalJpaConfig {
    }
}
