package com.ghatana.virtualorg.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
// import io.opentelemetry.semconv.ResourceAttributes;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Observability configuration for Virtual Organization agents with OpenTelemetry and Micrometer.
 *
 * <p><b>Purpose</b><br>
 * Centralized configuration for distributed tracing and metrics collection
 * in the virtual-org service. Sets up OpenTelemetry tracer and Micrometer
 * registry with OTLP (OpenTelemetry Protocol) exporters for backend integration.
 *
 * <p><b>Architecture Role</b><br>
 * Configuration class providing:
 * - OpenTelemetry tracing setup with OTLP gRPC span exporter
 * - Micrometer metrics registry with OTLP exporter
 * - Service resource attributes (service name, version, deployment)
 * - W3C trace context propagation for distributed tracing
 * - Batch span processing for performance
 *
 * <p><b>Observability Stack</b><br>
 * Integrates with:
 * - <b>Backend</b>: OTLP-compatible collectors (Jaeger, Tempo, Grafana Cloud)
 * - <b>Tracing</b>: OpenTelemetry SDK with W3C trace context
 * - <b>Metrics</b>: Micrometer with OTLP registry
 * - <b>Propagation</b>: W3C Trace Context for distributed correlation
 *
 * <p><b>Configuration</b><br>
 * Requires:
 * - <b>Service Name</b>: Identifies this service (e.g., "virtual-org-service")
 * - <b>Service Version</b>: Semantic version (e.g., "1.0.0")
 * - <b>OTLP Endpoint</b>: Collector URL (e.g., "http://localhost:4317")
 * - <b>Resource Attributes</b>: Custom service metadata (environment, region, etc.)
 *
 * <p><b>Span Processing</b><br>
 * Uses BatchSpanProcessor for efficiency:
 * - Max queue size: 2048 spans
 * - Schedule delay: 5 seconds
 * - Max export batch: 512 spans
 * - Export timeout: 30 seconds
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VirtualOrgObservabilityConfig config = new VirtualOrgObservabilityConfig(
 *     "virtual-org-service",
 *     "1.0.0",
 *     "http://localhost:4317",  // OTLP collector
 *     Map.of(
 *         "environment", "production",
 *         "region", "us-east-1",
 *         "deployment.id", "deploy-456"
 *     )
 * );
 * 
 * // Initialize observability stack
 * config.initialize();
 * 
 * // Get tracer for instrumentation
 * Tracer tracer = config.getTracer();
 * Span span = tracer.spanBuilder("processTask").startSpan();
 * try (Scope scope = span.makeCurrent()) {
 *     // Traced operation
 * } finally {
 *     span.end();
 * }
 * 
 * // Get meter registry for metrics
 * MeterRegistry registry = config.getMeterRegistry();
 * registry.counter("tasks.processed", "status", "success").increment();
 * 
 * // Shutdown on service stop
 * config.shutdown();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe after initialization. Call {@link #initialize()} once before use.
 *
 * @see OpenTelemetry
 * @see Tracer
 * @see MeterRegistry
 * @see VirtualOrgAgentMetrics
 * @doc.type class
 * @doc.purpose Observability configuration with OpenTelemetry and Micrometer
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class VirtualOrgObservabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(VirtualOrgObservabilityConfig.class);

    private final String serviceName;
    private final String serviceVersion;
    private final String otlpEndpoint;
    private final Map<String, String> resourceAttributes;

    private OpenTelemetry openTelemetry;
    private MeterRegistry meterRegistry;
    private Tracer tracer;

    public VirtualOrgObservabilityConfig(
            @NotNull String serviceName,
            @NotNull String serviceVersion,
            @NotNull String otlpEndpoint,
            @NotNull Map<String, String> resourceAttributes) {

        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.otlpEndpoint = otlpEndpoint;
        this.resourceAttributes = resourceAttributes;

        log.info("Initializing observability: service={}, version={}, otlp={}",
                serviceName, serviceVersion, otlpEndpoint);
    }

    /**
     * Initializes OpenTelemetry and Micrometer.
     */
    public void initialize() {
        setupOpenTelemetry();
        setupMicrometer();

        log.info("Observability initialized successfully");
    }

    /**
     * Shuts down observability components.
     */
    public void shutdown() {
        if (openTelemetry instanceof OpenTelemetrySdk sdk) {
            sdk.getSdkTracerProvider().shutdown().join(10, TimeUnit.SECONDS);
            log.info("OpenTelemetry shutdown complete");
        }

        if (meterRegistry != null) {
            meterRegistry.close();
            log.info("MeterRegistry shutdown complete");
        }
    }

    @NotNull
    public OpenTelemetry getOpenTelemetry() {
        if (openTelemetry == null) {
            throw new IllegalStateException("OpenTelemetry not initialized. Call initialize() first.");
        }
        return openTelemetry;
    }

    @NotNull
    public Tracer getTracer() {
        if (tracer == null) {
            throw new IllegalStateException("Tracer not initialized. Call initialize() first.");
        }
        return tracer;
    }

    @NotNull
    public MeterRegistry getMeterRegistry() {
        if (meterRegistry == null) {
            throw new IllegalStateException("MeterRegistry not initialized. Call initialize() first.");
        }
        return meterRegistry;
    }

    // =============================
    // Private setup methods
    // =============================

    private void setupOpenTelemetry() {
        // Build resource with service attributes
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put("service.name", serviceName)
                        .put("service.version", serviceVersion)
                        .put("service.namespace", "virtual-org")
                        .build()));

        // Add custom resource attributes
        AttributesBuilder customAttrs = Attributes.builder();
        resourceAttributes.forEach(customAttrs::put);
        resource = resource.merge(Resource.create(customAttrs.build()));

        // Configure OTLP span exporter
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();

        // Build tracer provider
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(Duration.ofSeconds(1))
                        .setMaxQueueSize(2048)
                        .setMaxExportBatchSize(512)
                        .build())
                .build();

        // Build OpenTelemetry SDK
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        // Get tracer
        this.tracer = openTelemetry.getTracer("com.ghatana.virtualorg", serviceVersion);

        log.info("OpenTelemetry configured: endpoint={}", otlpEndpoint);
    }

    private void setupMicrometer() {
        if (otlpEndpoint != null && !otlpEndpoint.isEmpty()) {
            // OTLP meter registry
            OtlpConfig otlpConfig = new OtlpConfig() {
                @Override
                public String url() {
                    return otlpEndpoint + "/v1/metrics";
                }

                @Override
                public Duration step() {
                    return Duration.ofSeconds(10);
                }

                @Override
                public String get(String key) {
                    return null;
                }
            };

            this.meterRegistry = new OtlpMeterRegistry(otlpConfig, io.micrometer.core.instrument.Clock.SYSTEM);

            log.info("Micrometer OTLP registry configured: endpoint={}", otlpConfig.url());
        } else {
            // Simple in-memory registry for development
            this.meterRegistry = new SimpleMeterRegistry();

            log.warn("Using SimpleMeterRegistry (no OTLP endpoint configured)");
        }

        // Add common tags
        meterRegistry.config()
                .commonTags(
                        "service", serviceName,
                        "version", serviceVersion,
                        "namespace", "virtual-org"
                );

        resourceAttributes.forEach((key, value) ->
                meterRegistry.config().commonTags(key, value)
        );
    }

    // =============================
    // Builder
    // =============================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serviceName = "virtual-org-agent";
        private String serviceVersion = "1.0.0";
        private String otlpEndpoint = "http://localhost:4317";
        private Map<String, String> resourceAttributes = Map.of();

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        public Builder otlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint;
            return this;
        }

        public Builder resourceAttributes(Map<String, String> attributes) {
            this.resourceAttributes = attributes;
            return this;
        }

        public VirtualOrgObservabilityConfig build() {
            return new VirtualOrgObservabilityConfig(
                    serviceName,
                    serviceVersion,
                    otlpEndpoint,
                    resourceAttributes
            );
        }
    }
}
