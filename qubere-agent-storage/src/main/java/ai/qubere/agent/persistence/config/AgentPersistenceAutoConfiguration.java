package ai.qubere.agent.persistence.config;

import ai.qubere.agent.async.AgentAsyncQueue;
import ai.qubere.agent.async.config.AgentAsyncAutoConfiguration;
import ai.qubere.agent.evaluation.CompositeGoldenDatasetRepository;
import ai.qubere.agent.evaluation.GoldenDatasetRepository;
import ai.qubere.agent.evaluation.ResourceGoldenDatasetRepository;
import ai.qubere.agent.evaluation.config.AgentEvaluationInfrastructureAutoConfiguration;
import ai.qubere.agent.persistence.AgentAsyncQueueCommandRepository;
import ai.qubere.agent.persistence.AgentEvaluationDatasetRepository;
import ai.qubere.agent.persistence.AgentExecutionRecordEntity;
import ai.qubere.agent.persistence.AgentExecutionRecordRepository;
import ai.qubere.agent.persistence.JpaAgentAsyncQueue;
import ai.qubere.agent.persistence.JpaGoldenDatasetRepository;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@AutoConfigureBefore({AgentAsyncAutoConfiguration.class, AgentEvaluationInfrastructureAutoConfiguration.class})
@EntityScan(basePackageClasses = AgentExecutionRecordEntity.class)
@EnableJpaRepositories(basePackageClasses = AgentExecutionRecordRepository.class)
public class AgentPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper agentPersistenceObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean(AgentAsyncQueue.class)
    @ConditionalOnProperty(prefix = "agent-platform.async.queue", name = "type", havingValue = "database")
    AgentAsyncQueue jpaAgentAsyncQueue(AgentAsyncQueueCommandRepository repository, ObjectMapper objectMapper) {
        return new JpaAgentAsyncQueue(repository, objectMapper);
    }

    /**
     * Database-only golden datasets. Classpath datasets are not consulted, so this is for
     * deployments whose evaluation corpus lives entirely in the database.
     */
    @Bean
    @ConditionalOnMissingBean(GoldenDatasetRepository.class)
    @ConditionalOnProperty(prefix = "agent-platform.evaluation", name = "dataset-provider", havingValue = "database")
    GoldenDatasetRepository jpaGoldenDatasetRepository(
            AgentEvaluationDatasetRepository repository,
            ObjectMapper objectMapper
    ) {
        return new JpaGoldenDatasetRepository(repository, objectMapper);
    }

    /**
     * Database datasets layered over classpath ones: operationally curated datasets override
     * same-named packaged datasets while packaged datasets stay available.
     */
    @Bean
    @ConditionalOnMissingBean(GoldenDatasetRepository.class)
    @ConditionalOnProperty(prefix = "agent-platform.evaluation", name = "dataset-provider", havingValue = "database-then-classpath")
    GoldenDatasetRepository compositeGoldenDatasetRepository(
            AgentEvaluationDatasetRepository repository,
            ObjectMapper objectMapper,
            AgentPlatformProperties properties,
            ResourceLoader resourceLoader
    ) {
        return new CompositeGoldenDatasetRepository(
                new JpaGoldenDatasetRepository(repository, objectMapper),
                new ResourceGoldenDatasetRepository(
                        properties.getEvaluation().getDatasetLocations(),
                        resourceLoader,
                        properties.getEvaluation().isFailOnInvalidDataset()
                )
        );
    }
}
