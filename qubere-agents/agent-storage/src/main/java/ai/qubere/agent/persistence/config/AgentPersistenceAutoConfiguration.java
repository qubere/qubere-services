package ai.qubere.agent.persistence.config;

import ai.qubere.agent.persistence.AgentExecutionRecordEntity;
import ai.qubere.agent.persistence.AgentExecutionRecordRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@EntityScan(basePackageClasses = AgentExecutionRecordEntity.class)
@EnableJpaRepositories(basePackageClasses = AgentExecutionRecordRepository.class)
public class AgentPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper agentPersistenceObjectMapper() {
        return new ObjectMapper();
    }
}
