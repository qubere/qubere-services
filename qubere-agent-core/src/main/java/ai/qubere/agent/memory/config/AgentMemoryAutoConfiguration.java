package ai.qubere.agent.memory.config;

import ai.qubere.agent.memory.AgentMemoryService;
import ai.qubere.agent.memory.AgentVectorMemoryStore;
import ai.qubere.agent.memory.DefaultAgentMemoryService;
import ai.qubere.agent.memory.InMemoryAgentVectorMemoryStore;
import ai.qubere.agent.memory.SpringAiVectorMemoryStore;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
public class AgentMemoryAutoConfiguration {

    /**
     * When the application supplies a Spring AI {@link VectorStore} bean (by adding a vector
     * store starter such as pgvector, Redis, or Chroma), agent memory is backed by that real
     * durable store instead of the in-memory default. Nested in its own configuration class so
     * the {@code VectorStore} class reference is only loaded when Spring AI's vector store module
     * is actually on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(VectorStore.class)
    static class VectorStoreBackedMemoryConfiguration {

        @Bean
        @ConditionalOnBean(VectorStore.class)
        @ConditionalOnMissingBean(AgentVectorMemoryStore.class)
        AgentVectorMemoryStore springAiVectorMemoryStore(VectorStore vectorStore) {
            return new SpringAiVectorMemoryStore(vectorStore);
        }
    }

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
