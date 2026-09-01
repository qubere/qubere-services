package ai.qubere.agent.persistence.config;

import ai.qubere.agent.async.AgentAsyncQueue;
import ai.qubere.agent.async.config.AgentAsyncAutoConfiguration;
import ai.qubere.agent.persistence.AgentAsyncQueueCommandRepository;
import ai.qubere.agent.persistence.AgentExecutionRecordEntity;
import ai.qubere.agent.persistence.AgentExecutionRecordRepository;
import ai.qubere.agent.persistence.JpaAgentAsyncQueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@AutoConfigureBefore(AgentAsyncAutoConfiguration.class)
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
}
