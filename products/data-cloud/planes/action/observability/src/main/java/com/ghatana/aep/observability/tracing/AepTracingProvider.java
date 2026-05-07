package com.ghatana.aep.observability.tracing;

import java.util.Arrays;
import java.util.UUID;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * @doc.type class
 * @doc.purpose OpenTelemetry distributed tracing initialization and management for AEP engine
 * @doc.layer observability
 * @doc.pattern Service
 */
public class AepTracingProvider {

    private static final Logger log = LoggerFactory.getLogger(AepTracingProvider.class);

    private static final String SERVICE_NAME = "aep-engine";
        private static final String OTLP_ENDPOINT = System.getenv("AEP_OTLP_ENDPOINT") != null
            ? System.getenv("AEP_OTLP_ENDPOINT")
            : System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT") != null
            ? System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
            : "http://localhost:4317";

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final Meter meter;
    private final SdkTracerProvider tracerProvider;

    private static AepTracingProvider instance;

    /**
     * Private constructor - use getInstance()
     */
    private AepTracingProvider() {
        this.tracerProvider = initializeTracerProvider();
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        this.tracer = openTelemetry.getTracer(SERVICE_NAME);
        this.meter = openTelemetry.getMeter(SERVICE_NAME);

        log.info("AEP Tracing Provider initialized - OTLP endpoint: {}", OTLP_ENDPOINT);
    }

    /**
     * Get singleton instance
     */
    public static synchronized AepTracingProvider getInstance() {
        if (instance == null) {
            instance = new AepTracingProvider();
        }
        return instance;
    }

    /**
         * Initialize tracer provider with OTLP exporter
     */
    private SdkTracerProvider initializeTracerProvider() {
        String version = System.getenv("SERVICE_VERSION") != null
                ? System.getenv("SERVICE_VERSION")
                : "0.0.1";

        Resource resource = Resource.getDefault().merge(Resource.create(
            Attributes.builder()
                .put("service.name", SERVICE_NAME)
                .put("service.version", version)
                        .build()
        ));

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(OTLP_ENDPOINT)
            .setCompression("gzip")
                .build();

        return SdkTracerProvider.builder()
                .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.traceIdRatioBased(0.1))  // 10% sampling
                .build();
    }

    /**
     * Start a span for pipeline deployment
     */
    public Span startPipelineDeploymentSpan(String pipelineId, String tenantId) {
        String correlationId = getOrCreateCorrelationId();
        String traceId = UUID.randomUUID().toString();

        Span span = tracer.spanBuilder("aep.pipeline.deploy")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("pipeline.id", pipelineId)
                .setAttribute("tenant.id", tenantId)
                .setAttribute("correlation.id", correlationId)
                .setAttribute("trace.id", traceId)
                .startSpan();

        // Set correlation ID in MDC for logging
        MDC.put("correlationId", correlationId);
        MDC.put("traceId", traceId);
        MDC.put("pipelineId", pipelineId);
        MDC.put("tenantId", tenantId);

        return span;
    }

    /**
     * Start a span for agent execution
     */
    public Span startAgentExecutionSpan(String agentId, String pipelineId, String tenantId) {
        String correlationId = getOrCreateCorrelationId();

        Span span = tracer.spanBuilder("aep.agent.execute")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("agent.id", agentId)
                .setAttribute("pipeline.id", pipelineId)
                .setAttribute("tenant.id", tenantId)
                .setAttribute("correlation.id", correlationId)
                .startSpan();

        MDC.put("agentId", agentId);

        return span;
    }

    /**
     * Start a span for event processing
     */
    public Span startEventProcessingSpan(String eventId, String eventType, String tenantId) {
        String correlationId = getOrCreateCorrelationId();

        Span span = tracer.spanBuilder("aep.event.process")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("event.id", eventId)
                .setAttribute("event.type", eventType)
                .setAttribute("tenant.id", tenantId)
                .setAttribute("correlation.id", correlationId)
                .startSpan();

        MDC.put("eventId", eventId);
        MDC.put("eventType", eventType);

        return span;
    }

    /**
     * Record successful operation
     */
    public void recordSuccess(Span span, long durationMs) {
        if (span != null) {
            span.setStatus(StatusCode.OK);
            span.setAttribute("duration.ms", durationMs);
            span.end();

            log.debug("Operation completed successfully in {}ms", durationMs);
        }
    }

    /**
     * Record failed operation
     */
    public void recordError(Span span, Exception exception, long durationMs) {
        if (span != null) {
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            span.recordException(exception);
            span.setAttribute("duration.ms", durationMs);
            span.setAttribute("error", true);
            span.setAttribute("error.type", exception.getClass().getName());
            span.end();

            log.error("Operation failed after {}ms: {}", durationMs, exception.getMessage(), exception);
        }
    }

    /**
     * Get or create correlation ID
     */
    private String getOrCreateCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }
        return correlationId;
    }

    /**
     * Create a scope for a span to set it as current
     */
    public Scope createScope(Span span) {
        return span.makeCurrent();
    }

    /**
     * Get active tracer
     */
    public Tracer getTracer() {
        return tracer;
    }

    /**
     * Get active meter
     */
    public Meter getMeter() {
        return meter;
    }

    /**
     * Get OpenTelemetry instance
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    /**
     * Shutdown tracing provider
     */
    public void shutdown() {
        if (tracerProvider != null) {
            tracerProvider.close();
            log.info("AEP Tracing Provider shut down");
        }
    }
}
