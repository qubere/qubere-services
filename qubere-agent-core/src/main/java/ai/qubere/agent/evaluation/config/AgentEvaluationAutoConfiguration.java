package ai.qubere.agent.evaluation.config;

import ai.qubere.agent.evaluation.AgentEvaluator;
import ai.qubere.agent.evaluation.AgentReplayService;
import ai.qubere.agent.evaluation.GoldenDatasetRepository;
import ai.qubere.agent.evaluation.EvaluationResultStore;
import ai.qubere.agent.evaluation.PromptRegressionService;
import ai.qubere.agent.prompts.PromptVersionStore;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(
        value = AgentRuntimeAutoConfiguration.class,
        name = "ai.qubere.agent.persistence.config.AgentPersistenceAutoConfiguration"
)
public class AgentEvaluationAutoConfiguration {

    @Bean
    @ConditionalOnBean(AgentRuntimeService.class)
    @ConditionalOnMissingBean
    AgentEvaluator agentEvaluator(
            AgentRuntimeService runtimeService,
            GoldenDatasetRepository datasetRepository,
            EvaluationResultStore resultStore
    ) {
        return new AgentEvaluator(runtimeService, datasetRepository, resultStore);
    }

    @Bean
    @ConditionalOnBean({AgentRuntimeService.class, AgentExecutionStore.class, ObjectMapper.class})
    @ConditionalOnMissingBean
    AgentReplayService agentReplayService(AgentExecutionStore executionStore, AgentRuntimeService runtimeService, ObjectMapper objectMapper) {
        return new AgentReplayService(executionStore, runtimeService, objectMapper);
    }

    @Bean
    @ConditionalOnBean(PromptVersionStore.class)
    @ConditionalOnMissingBean
    PromptRegressionService promptRegressionService(PromptVersionStore promptVersionStore) {
        return new PromptRegressionService(promptVersionStore);
    }
}
