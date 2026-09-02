package ai.qubere.agent.observability.config;

import ai.qubere.agent.observability.AgentTelemetryExporter;
import ai.qubere.agent.observability.InMemoryAgentTelemetryExporter;
import ai.qubere.agent.observability.OpenTelemetryAgentPipelineListener;
import ai.qubere.agent.observability.OtlpAgentTelemetryExporter;
import ai.qubere.agent.runtime.AgentPipelineListener;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(AgentRuntimeAutoConfiguration.class)
@EnableConfigurationProperties(AgentPlatformProperties.class)
public class AgentObservabilityAutoConfiguration {

    /**
     * Real OTLP span exporter, registered only when the OpenTelemetry SDK/OTLP exporter classes
     * are present on the classpath and both {@code agent-platform.observability.open-telemetry.enabled=true}
     * and {@code agent-platform.observability.open-telemetry.otlp.enabled=true}. Takes precedence
     * over {@link InMemoryAgentTelemetryExporter} via {@code @AutoConfigureBefore} ordering plus
     * {@code @ConditionalOnMissingBean}.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AgentTelemetryExporter.class)
    @ConditionalOnClass(OpenTelemetrySdk.class)
    @ConditionalOnProperty(prefix = "agent-platform.observability.open-telemetry", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "agent-platform.observability.open-telemetry.otlp", name = "enabled", havingValue = "true")
    OtlpAgentTelemetryExporter otlpAgentTelemetryExporter(AgentPlatformProperties properties) {
        return new OtlpAgentTelemetryExporter(properties.getObservability().getOpenTelemetry());
    }

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
