package ai.qubere.agent.memory.config;

import ai.qubere.agent.memory.AgentMemoryService;
import ai.qubere.agent.memory.AgentVectorMemoryStore;
import ai.qubere.agent.memory.DefaultAgentMemoryService;
import ai.qubere.agent.memory.InMemoryAgentVectorMemoryStore;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AgentMemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentVectorMemoryStore agentVectorMemoryStore() {
        return new InMemoryAgentVectorMemoryStore();
    }

    @Bean
    @ConditionalOnMissingBean
    AgentMemoryService agentMemoryService(AgentVectorMemoryStore vectorMemoryStore) {
        return new DefaultAgentMemoryService(vectorMemoryStore);
    }
}
