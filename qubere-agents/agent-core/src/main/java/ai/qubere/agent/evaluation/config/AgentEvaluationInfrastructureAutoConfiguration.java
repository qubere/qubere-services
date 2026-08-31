package ai.qubere.agent.evaluation.config;

import ai.qubere.agent.evaluation.EvaluationResultStore;
import ai.qubere.agent.evaluation.GoldenDatasetRepository;
import ai.qubere.agent.evaluation.InMemoryAgentGovernanceService;
import ai.qubere.agent.evaluation.InMemoryAgentObservabilityService;
import ai.qubere.agent.evaluation.InMemoryEvaluationResultStore;
import ai.qubere.agent.evaluation.InMemoryGoldenDatasetRepository;
import ai.qubere.agent.runtime.AgentGovernanceService;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(AgentRuntimeAutoConfiguration.class)
public class AgentEvaluationInfrastructureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    GoldenDatasetRepository goldenDatasetRepository() {
        return new InMemoryGoldenDatasetRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    EvaluationResultStore evaluationResultStore() {
        return new InMemoryEvaluationResultStore();
    }

    @Bean
    @ConditionalOnMissingBean
    InMemoryAgentObservabilityService inMemoryAgentObservabilityService() {
        return new InMemoryAgentObservabilityService(1000);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentGovernanceService agentGovernanceService(AgentPlatformProperties properties) {
        return new InMemoryAgentGovernanceService(properties);
    }
}
