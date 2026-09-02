package ai.qubere.agent.ai.config;

import ai.qubere.agent.ai.ModelCostBudgetTracker;
import ai.qubere.agent.ai.ModelUsageRecorder;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AgentAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ModelUsageRecorder modelUsageRecorder() {
        return ModelUsageRecorder.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    ModelCostBudgetTracker modelCostBudgetTracker() {
        return new ModelCostBudgetTracker();
    }
}
