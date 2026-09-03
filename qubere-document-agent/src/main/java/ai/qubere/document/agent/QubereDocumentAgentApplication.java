package ai.qubere.document.agent;

import ai.qubere.document.agent.document.intake.UnassignedIntakeEntity;
import ai.qubere.document.agent.document.intake.UnassignedIntakeRepository;
import ai.qubere.document.agent.document.processing.ProcessingRunEntity;
import ai.qubere.document.agent.document.processing.ProcessingRunRepository;
import ai.qubere.document.agent.document.review.ExtractionFieldEntity;
import ai.qubere.document.agent.document.review.ExtractionFieldRepository;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableJpaRepositories}/{@code @EntityScan} are declared explicitly here because
 * {@code qubere-agent-storage}'s own {@code AgentPersistenceAutoConfiguration} already declares
 * both, scoped to its own package. Once any {@code @EnableJpaRepositories}/{@code @EntityScan}
 * is present in the context, Spring Boot's implicit "scan the application's own package" behavior
 * is replaced by the explicitly declared base packages — every one of them, merged across however
 * many are declared — so this module's own entities/repositories need their own explicit
 * declaration rather than relying on being picked up implicitly. {@code document.intake}/
 * {@code document.review} are listed separately from {@code document.processing} because they are
 * sibling packages, not subpackages — entity/repository scanning is recursive into subpackages but
 * does not otherwise cross package boundaries, so each distinct entity package needs its own
 * {@code basePackageClasses} entry.
 */
@SpringBootApplication(scanBasePackages = {"ai.qubere.agent", "ai.qubere.document.agent"})
@EnableScheduling
@EnableJpaRepositories(basePackageClasses = {ProcessingRunRepository.class, UnassignedIntakeRepository.class, ExtractionFieldRepository.class})
@EntityScan(basePackageClasses = {ProcessingRunEntity.class, UnassignedIntakeEntity.class, ExtractionFieldEntity.class})
public class QubereDocumentAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(QubereDocumentAgentApplication.class, args);
    }
}
