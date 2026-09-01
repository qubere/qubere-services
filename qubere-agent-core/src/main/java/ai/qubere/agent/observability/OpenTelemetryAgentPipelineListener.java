package ai.qubere.agent.observability;

import ai.qubere.agent.runtime.AgentPipelineEvent;
import ai.qubere.agent.runtime.AgentPipelineListener;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenTelemetryAgentPipelineListener implements AgentPipelineListener {

    private final AgentTelemetryExporter exporter;
    private final AgentPlatformProperties.Observability.OpenTelemetry properties;

    public OpenTelemetryAgentPipelineListener(AgentTelemetryExporter exporter, AgentPlatformProperties properties) {
        this.exporter = exporter == null ? AgentTelemetryExporter.noop() : exporter;
        this.properties = properties == null
                ? new AgentPlatformProperties.Observability.OpenTelemetry()
                : properties.getObservability().getOpenTelemetry();
    }

    @Override
    public void onEvent(AgentPipelineEvent event) {
        if (event == null || !properties.isEnabled()) {
            return;
        }
        exporter.export(toTelemetryEvent(event));
    }

    private AgentTelemetryEvent toTelemetryEvent(AgentPipelineEvent event) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("service.name", properties.getServiceName());
        attributes.put("telemetry.provider", "opentelemetry");
        attributes.put("agent.pipeline.step", event.step().name());
        attributes.put("agent.execution.id", event.context().executionId());
        attributes.put("agent.id", event.descriptor().id());
        attributes.put("agent.version", event.descriptor().version());
        if (properties.isIncludeTenant()) {
            attributes.put("agent.tenant.id", event.context().tenantId());
        }
        if (properties.isIncludeActor()) {
            attributes.put("agent.actor.id", event.context().actorId());
        }
        attributes.put("agent.correlation.id", event.context().correlationId());
        attributes.putAll(event.attributes());

        return new AgentTelemetryEvent(
                "span.event",
                "agent.pipeline." + event.step().name().toLowerCase().replace('_', '-'),
                event.context().executionId(),
                properties.isIncludeTenant() ? event.context().tenantId() : null,
                properties.isIncludeActor() ? event.context().actorId() : null,
                event.context().correlationId(),
                event.descriptor().id(),
                event.descriptor().version(),
                event.step().name(),
                event.message(),
                attributes,
                event.occurredAt()
        );
    }
}