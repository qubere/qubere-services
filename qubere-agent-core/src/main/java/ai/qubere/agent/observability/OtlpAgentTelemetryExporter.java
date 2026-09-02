package ai.qubere.agent.observability;

import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Exports {@link AgentTelemetryEvent}s as real OTLP spans over gRPC or HTTP/protobuf, backed by
 * the OpenTelemetry SDK. Each event is modeled as a single, already-completed span named after
 * the event's {@code name}, carrying execution/tenant/actor/agent identifiers and status as span
 * attributes. This complements (does not replace) the framework's provider-neutral
 * {@link AgentTelemetryEvent} model: applications that need a different backend can still
 * implement {@link AgentTelemetryExporter} directly without any OpenTelemetry SDK dependency.
 * <p>
 * Requires {@code opentelemetry-sdk} and {@code opentelemetry-exporter-otlp} on the classpath
 * (declared optional by the framework) and is only auto-configured when
 * {@code agent-platform.observability.open-telemetry.otlp.enabled=true}.
 */
public class OtlpAgentTelemetryExporter implements AgentTelemetryExporter, AutoCloseable {

    private static final AttributeKey<String> EXECUTION_ID = AttributeKey.stringKey("agent.execution_id");
    private static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("agent.tenant_id");
    private static final AttributeKey<String> ACTOR_ID = AttributeKey.stringKey("agent.actor_id");
    private static final AttributeKey<String> CORRELATION_ID = AttributeKey.stringKey("agent.correlation_id");
    private static final AttributeKey<String> AGENT_ID = AttributeKey.stringKey("agent.id");
    private static final AttributeKey<String> AGENT_VERSION = AttributeKey.stringKey("agent.version");
    private static final AttributeKey<String> EVENT_TYPE = AttributeKey.stringKey("agent.event_type");

    private final OpenTelemetrySdk sdk;
    private final Tracer tracer;
    private final AgentPlatformProperties.Observability.OpenTelemetry properties;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public OtlpAgentTelemetryExporter(AgentPlatformProperties.Observability.OpenTelemetry properties) {
        this.properties = properties == null ? new AgentPlatformProperties.Observability.OpenTelemetry() : properties;
        SpanExporter spanExporter = buildSpanExporter(this.properties.getOtlp());
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.builder()
                        .put("service.name", this.properties.getServiceName())
                        .build()))
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();
        this.sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        this.tracer = sdk.getTracer("ai.qubere.agent");
    }

    private SpanExporter buildSpanExporter(AgentPlatformProperties.Observability.Otlp otlp) {
        if ("http".equals(otlp.getProtocol()) || "http/protobuf".equals(otlp.getProtocol())) {
            return OtlpHttpSpanExporter.builder()
                    .setEndpoint(otlp.getEndpoint())
                    .setTimeout(otlp.getTimeoutSeconds(), TimeUnit.SECONDS)
                    .build();
        }
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlp.getEndpoint())
                .setTimeout(otlp.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void export(AgentTelemetryEvent event) {
        if (event == null || closed.get()) {
            return;
        }
        Instant occurredAt = event.occurredAt();
        Span span = tracer.spanBuilder(event.name())
                .setStartTimestamp(occurredAt)
                .setAllAttributes(attributesFor(event))
                .startSpan();
        if (event.status() != null && (event.status().equalsIgnoreCase("FAILED") || event.status().equalsIgnoreCase("ERROR"))) {
            span.setStatus(StatusCode.ERROR, event.message());
        }
        span.end(occurredAt);
    }

    private Attributes attributesFor(AgentTelemetryEvent event) {
        AttributesBuilder builder = Attributes.builder();
        putIfPresent(builder, EXECUTION_ID, event.executionId());
        if (properties.isIncludeTenant()) {
            putIfPresent(builder, TENANT_ID, event.tenantId());
        }
        if (properties.isIncludeActor()) {
            putIfPresent(builder, ACTOR_ID, event.actorId());
        }
        putIfPresent(builder, CORRELATION_ID, event.correlationId());
        putIfPresent(builder, AGENT_ID, event.agentId());
        putIfPresent(builder, AGENT_VERSION, event.agentVersion());
        putIfPresent(builder, EVENT_TYPE, event.type());
        for (Map.Entry<String, Object> attribute : event.attributes().entrySet()) {
            if (attribute.getValue() != null) {
                builder.put(AttributeKey.stringKey("agent." + attribute.getKey()), String.valueOf(attribute.getValue()));
            }
        }
        return builder.build();
    }

    private void putIfPresent(AttributesBuilder builder, AttributeKey<String> key, String value) {
        if (value != null) {
            builder.put(key, value);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            sdk.getSdkTracerProvider().shutdown();
        }
    }
}
