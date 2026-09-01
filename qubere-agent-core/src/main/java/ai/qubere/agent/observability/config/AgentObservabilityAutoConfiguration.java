package ai.qubere.agent.observability.config;

import ai.qubere.agent.observability.AgentTelemetryExporter;
import ai.qubere.agent.observability.InMemoryAgentTelemetryExporter;
import ai.qubere.agent.observability.OpenTelemetryAgentPipelineListener;
import ai.qubere.agent.runtime.AgentPipelineListener;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(AgentRuntimeAutoConfiguration.class)
@EnableConfigurationProperties(AgentPlatformProperties.class)
public class AgentObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentTelemetryExporter.class)
    @ConditionalOnProperty(prefix = "agent-platform.observability.open-telemetry", name = "enabled", havingValue = "true")
    AgentTelemetryExporter agentTelemetryExporter(AgentPlatformProperties properties) {
        return new InMemoryAgentTelemetryExporter(
                properties.getObservability().getOpenTelemetry().getMaxBufferedEvents()
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "openTelemetryAgentPipelineListener")
    @ConditionalOnProperty(prefix = "agent-platform.observability.open-telemetry", name = "enabled", havingValue = "true")
    AgentPipelineListener openTelemetryAgentPipelineListener(AgentTelemetryExporter exporter, AgentPlatformProperties properties) {
        return new OpenTelemetryAgentPipelineListener(exporter, properties);
    }
}