package ai.qubere.agent.persistenceautoconfig;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.async.AgentAsyncQueue;
import ai.qubere.agent.persistence.JpaAgentAsyncQueue;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "agent-platform.async.queue.type=database",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:asyncqueueconfig;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class AgentPersistenceAsyncQueueAutoConfigurationTest {

    @Autowired
    private AgentAsyncQueue queue;

    @Test
    void databaseQueuePropertySelectsJpaQueue() {
        assertThat(queue).isInstanceOf(JpaAgentAsyncQueue.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AgentExecutionStore agentExecutionStore() {
            return new AgentExecutionStore() {
                @Override
                public void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
                }

                @Override
                public void markCompleted(String executionId, AgentOutput output) {
                }

                @Override
                public void markFailed(String executionId, Throwable failure) {
                }

                @Override
                public Optional<AgentExecutionRecord> findByExecutionId(String executionId) {
                    return Optional.empty();
                }
            };
        }
    }
}


