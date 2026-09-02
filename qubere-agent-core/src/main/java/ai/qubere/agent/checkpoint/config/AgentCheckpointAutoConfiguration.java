package ai.qubere.agent.checkpoint.config;

import ai.qubere.agent.checkpoint.AgentCheckpointStore;
import ai.qubere.agent.checkpoint.InMemoryAgentCheckpointStore;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

/**
 * Registers the checkpoint store that makes approval-interrupted multi-step agents resumable.
 * <p>
 * The in-memory default keeps local development working without a database, but it is explicitly
 * not resume-safe across restarts; {@code qubere-agent-storage} contributes a JPA-backed store
 * that takes precedence when present.
 */
@AutoConfiguration
@AutoConfigureBefore(AgentRuntimeAutoConfiguration.class)
public class AgentCheckpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentCheckpointStore agentCheckpointStore() {
        return new InMemoryAgentCheckpointStore();
    }
}
