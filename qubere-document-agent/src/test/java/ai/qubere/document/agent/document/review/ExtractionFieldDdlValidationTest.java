package ai.qubere.document.agent.document.review;

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
 * Boots a minimal JPA-only context for {@link ExtractionFieldEntity} against a schema created
 * solely from the manual PostgreSQL DDL script, with {@code ddl-auto=validate} -- same proof
 * pattern as {@code ProcessingRunDdlValidationTest}/{@code UnassignedIntakeDdlValidationTest}, and
 * the same reason for pinning {@code PostgreSQLDialect}: {@code value}/{@code bbox_json} use
 * {@code @JdbcTypeCode(SqlTypes.LONGVARCHAR)}, which only validates against a {@code text} column
 * under the real Postgres dialect (see {@code DocumentParseResultEntity}'s javadoc and
 * {@code MIGRATION.md} §13/§17 for the full finding).
 */
@SpringBootTest(
        classes = ExtractionFieldDdlValidationTest.MinimalJpaConfig.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/manual/postgres/004_extraction_field.sql",
                "spring.datasource.url=jdbc:h2:mem:extraction_field_ddl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
        }
)
class ExtractionFieldDdlValidationTest {

    @Autowired
    private ExtractionFieldRepository repository;

    @Test
    void manualPostgresDdlMatchesTheJpaEntityMapping() {
        ExtractionFieldEntity entity = new ExtractionFieldEntity();
        entity.setId("ddl-check-1");
        entity.setDocumentId("doc-1");
        entity.setFieldName("invoiceNumber");
        entity.setValue("INV-1001");
        entity.setConfidence(90);
        entity.setPageNumber(1);
        entity.setBboxJson("{\"x\":1.0,\"y\":2.0,\"width\":3.0,\"height\":4.0}");
        entity.setSource(ExtractionFieldSource.MACHINE);
        entity.setCreatedAt(java.time.Instant.now());

        repository.saveAndFlush(entity);

        assertThat(repository.findById("ddl-check-1")).isPresent();
    }

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = ExtractionFieldEntity.class)
    @EnableJpaRepositories(basePackageClasses = ExtractionFieldRepository.class)
    static class MinimalJpaConfig {
    }
}
