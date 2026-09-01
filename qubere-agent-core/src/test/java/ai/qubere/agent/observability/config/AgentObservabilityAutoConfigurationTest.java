package ai.qubere.agent.observability.config;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.observability.AgentTelemetryExporter;
import ai.qubere.agent.observability.InMemoryAgentTelemetryExporter;
import ai.qubere.agent.runtime.AgentPipelineEvent;
import ai.qubere.agent.runtime.AgentPipelineListener;
import ai.qubere.agent.runtime.AgentPipelineStep;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgentObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgentObservabilityAutoConfiguration.class,
                    AgentRuntimeAutoConfiguration.class
            ));

    @Test
    void openTelemetryPipelineListenerIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AgentTelemetryExporter.class);
            assertThat(context).doesNotHaveBean(AgentPipelineListener.class);
        });
    }

    @Test
    void enablesOpenTelemetryShapedListenerAndInMemoryExporter() {
        contextRunner
                .withPropertyValues(
                        "agent-platform.observability.open-telemetry.enabled=true",
                        "agent-platform.observability.open-telemetry.service-name=agent-test",
                        "agent-platform.observability.open-telemetry.include-actor=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentTelemetryExporter.class);
                    assertThat(context).hasBean("openTelemetryAgentPipelineListener");
                    assertThat(context.getBean(AgentTelemetryExporter.class)).isInstanceOf(InMemoryAgentTelemetryExporter.class);

                    AgentPipelineListener listener = context.getBean("openTelemetryAgentPipelineListener", AgentPipelineListener.class);
                    listener.onEvent(event());

                    InMemoryAgentTelemetryExporter exporter = (InMemoryAgentTelemetryExporter) context.getBean(AgentTelemetryExporter.class);
                    assertThat(exporter.recentEvents(10))
                            .singleElement()
                            .satisfies(telemetry -> {
                                assertThat(telemetry.type()).isEqualTo("span.event");
                                assertThat(telemetry.name()).isEqualTo("agent.pipeline.execution-started");
                                assertThat(telemetry.executionId()).isEqualTo("exec-otel");
                                assertThat(telemetry.tenantId()).isEqualTo("tenant-1");
                                assertThat(telemetry.actorId()).isEqualTo("actor-1");
                                assertThat(telemetry.attributes())
                                        .containsEntry("service.name", "agent-test")
                                        .containsEntry("agent.pipeline.step", "EXECUTION_STARTED")
                                        .containsEntry("agent.custom", "value");
                            });
                });
    }

    @Test
    void customExporterCanReplaceDefaultExporter() {
        AtomicReference<Object> exported = new AtomicReference<>();
        AgentTelemetryExporter customExporter = exported::set;

        contextRunner
                .withPropertyValues("agent-platform.observability.open-telemetry.enabled=true")
                .withBean(AgentTelemetryExporter.class, () -> customExporter)
                .run(context -> {
                    assertThat(context.getBean(AgentTelemetryExporter.class)).isSameAs(customExporter);
                    context.getBean("openTelemetryAgentPipelineListener", AgentPipelineListener.class).onEvent(event());
                    assertThat(exported.get()).isNotNull();
                });
    }

    @Test
    void canSuppressActorFromExportedTelemetry() {
        contextRunner
                .withPropertyValues(
                        "agent-platform.observability.open-telemetry.enabled=true",
                        "agent-platform.observability.open-telemetry.include-actor=false"
                )
                .run(context -> {
                    context.getBean("openTelemetryAgentPipelineListener", AgentPipelineListener.class).onEvent(event());
                    InMemoryAgentTelemetryExporter exporter = (InMemoryAgentTelemetryExporter) context.getBean(AgentTelemetryExporter.class);
                    assertThat(exporter.recentEvents(1).get(0).actorId()).isNull();
                    assertThat(exporter.recentEvents(1).get(0).attributes()).doesNotContainKey("agent.actor.id");
                });
    }

    private AgentPipelineEvent event() {
        return new AgentPipelineEvent(
                AgentPipelineStep.EXECUTION_STARTED,
                new AgentExecutionContext("exec-otel", "tenant-1", "actor-1", "corr-1", Instant.parse("2026-09-01T00:00:00Z"), Map.of()),
                new AgentDescriptor("agent.one", "Agent One", "1.0.0", "test", AgentRiskLevel.LOW, Set.of("test")),
                "started",
                Map.of("agent.custom", "value"),
                Instant.parse("2026-09-01T00:00:01Z")
        );
    }
}