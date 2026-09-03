package ai.qubere.document.agent.document.intake;

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
 * Boots a minimal JPA-only context for {@link UnassignedIntakeEntity} against a schema created
 * solely from the manual PostgreSQL DDL script, with {@code ddl-auto=validate} -- same proof
 * pattern as {@code ProcessingRunDdlValidationTest}, kept in this package (not that one) so its
 * {@code @EntityScan} does not also try to validate {@code ProcessingRunEntity}/
 * {@code DocumentParseResultEntity} against a schema that was never asked to include them.
 */
@SpringBootTest(
        classes = UnassignedIntakeDdlValidationTest.MinimalJpaConfig.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/manual/postgres/003_unassigned_document_intake.sql",
                "spring.datasource.url=jdbc:h2:mem:unassigned_document_intake_ddl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
        }
)
class UnassignedIntakeDdlValidationTest {

    @Autowired
    private UnassignedIntakeRepository repository;

    @Test
    void manualPostgresDdlMatchesTheJpaEntityMapping() {
        UnassignedIntakeEntity entity = new UnassignedIntakeEntity();
        entity.setId("ddl-check-1");
        entity.setTenantId("tenant-1");
        entity.setSource(IntakeSource.DOCUMENT_UPLOAD);
        entity.setFileName("invoice.pdf");
        entity.setDocType("COMMERCIAL_INVOICE");
        entity.setRequestedShipmentId(null);
        entity.setDescription("invoice.pdf was uploaded through the document upload form without naming a shipment.");
        entity.setStatus("Open");
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
    @EntityScan(basePackageClasses = UnassignedIntakeEntity.class)
    @EnableJpaRepositories(basePackageClasses = UnassignedIntakeRepository.class)
    static class MinimalJpaConfig {
    }
}
