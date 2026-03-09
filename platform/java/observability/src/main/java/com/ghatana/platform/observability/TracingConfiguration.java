package com.ghatana.platform.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enhanced OpenTelemetry configuration with environment-specific settings.
 *
 * <p>TracingConfiguration provides centralized OpenTelemetry setup with support for
 * development and production environments, environment-aware sampling strategies, and
 * resource configuration with service metadata.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Environment-specific sampling (dev: 100%, staging: 10%, prod: 1%)</li>
 *   <li>OTLP gRPC span export with compression (gzip)</li>
 *   <li>Batch span processing (512 batch size, 500ms delay)</li>
 *   <li>W3C Trace Context propagation</li>
 *   <li>Resource attributes (service.name, service.version, deployment.environment)</li>
 * @doc.type class
 * @doc.purpose OpenTelemetry SDK configuration with environment-aware sampling and OTLP export
 * @doc.layer core
 * @doc.pattern Configuration, Builder
 *   <li>No-op mode for disabled tracing (zero overhead)</li>
 *   <li>Singleton pattern with atomic initialization</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * TracingConfig config = TracingConfig.builder()
 *     .enabled(true)
 *     .serviceName("event-processor")
 *     .serviceVersion("1.0.0")
 *     .environment("production")
 *     .otlpEndpoint("http://jaeger:4317")
 *     .build();
 *
 * OpenTelemetry otel = TracingConfiguration.initialize(config);
 * Tracer tracer = TracingConfiguration.getTracer();
 * }</pre>
 *
 * <p><b>Environment-Specific Sampling:</b></p>
 * <ul>
 *   <li><b>Development:</b> 100% sampling (all traces captured)</li>
 *   <li><b>Staging/Test:</b> 10% sampling (1 in 10 traces)</li>
 *   <li><b>Production:</b> 1% sampling (1 in 100 traces)</li>
 *   <li><b>Default:</b> 10% sampling</li>
 * </ul>
 *
 * <p><b>Performance:</b></p>
 * <ul>
 *   <li>Batch processing reduces export overhead</li>
 *   <li>gzip compression reduces network bandwidth</li>
 *   <li>Sampling reduces storage costs in production</li>
 *   <li>No-op mode has zero overhead when tracing disabled</li>
 * </ul>
 *
 * <p><b>Thread-Safety:</b> Thread-safe via synchronized initialization and AtomicReference.</p>
 *
 * @see TracingConfig for configuration options
 * @see Tracing for simpler global tracing setup
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Configuration (Singleton)
 * @purpose Environment-aware OpenTelemetry initialization with sampling and OTLP export
 * @pattern Singleton pattern, Builder pattern (TracingConfig), Strategy pattern (sampling)
 * @responsibility OpenTelemetry SDK initialization, environment-specific configuration, tracer lifecycle
 * @usage Call TracingConfiguration.initialize(config) once at startup; access via getInstance()/getTracer()
 * @examples See class-level JavaDoc for configuration and initialization examples
 * @testing Test initialization with different environments, sampling verification, no-op mode
 * @notes Singleton; call initialize() before getInstance(); supports no-op mode for disabled tracing
 */
public final class TracingConfiguration {
    
    /**
     * Singleton OpenTelemetry instance (AtomicReference for thread-safe lazy init).
     */
    private static final AtomicReference<OpenTelemetry> INSTANCE = new AtomicReference<>();
    
    /**
     * Singleton Tracer instance (AtomicReference for thread-safe lazy init).
     */
    private static final AtomicReference<Tracer> TRACER = new AtomicReference<>();
    
    /**
     * Private constructor to prevent instantiation (singleton pattern).
     */
    private TracingConfiguration() {}
    
    /**
     * Initialize OpenTelemetry with environment-specific configuration.
     *
     * <p>This method should be called once at application startup. Subsequent calls
     * are ignored if OpenTelemetry is already initialized. If tracing is disabled,
     * returns a no-op OpenTelemetry instance with zero overhead.</p>
     *
     * <p><b>Configuration Details:</b></p>
     * <ul>
     *   <li><b>Resource Attributes:</b> service.name, service.version, deployment.environment</li>
     *   <li><b>Exporter:</b> OTLP gRPC to specified endpoint with gzip compression</li>
     *   <li><b>Span Processor:</b> BatchSpanProcessor (batch size 512, delay 500ms)</li>
     *   <li><b>Sampler:</b> TraceIdRatioBased sampler (environment-specific ratio)</li>
     *   <li><b>Propagators:</b> W3C Trace Context (traceparent, tracestate headers)</li>
     * </ul>
     *
     * <p><b>Sampling Strategy:</b></p>
     * <ul>
     *   <li>Development: 100% sampling (all traces)</li>
     *   <li>Staging/Test: 10% sampling</li>
     *   <li>Production: 1% sampling</li>
     *   <li>Default: 10% sampling</li>
     * </ul>
     *
     * <p><b>Thread-Safety:</b> Synchronized to ensure single initialization.</p>
     *
     * @param config the tracing configuration with service metadata and settings
     * @return the initialized OpenTelemetry instance (or no-op if disabled)
     */
    public static synchronized OpenTelemetry initialize(TracingConfig config) {
        if (INSTANCE.get() != null) {
            return INSTANCE.get();
        }
        
        if (!config.isEnabled()) {
            OpenTelemetry noop = OpenTelemetry.noop();
            INSTANCE.set(noop);
            TRACER.set(noop.getTracer("noop"));
            return noop;
        }
        
        // Create resource with service information
        Resource resource = Resource.getDefault()
            .merge(Resource.builder()
                .put("service.name", config.getServiceName())
                .put("service.version", config.getServiceVersion())
                .put("deployment.environment", config.getEnvironment())
                .build());
        
        // Configure OTLP exporter
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(config.getOtlpEndpoint())
            .setCompression("gzip")
            .build();
        
        // Configure sampler based on environment
        Sampler sampler = createSampler(config);
        
        // Build tracer provider
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                .setMaxExportBatchSize(512)
                .setScheduleDelay(Duration.ofMillis(500))
                .build())
            .setSampler(sampler)
            .build();
        
        // Build OpenTelemetry SDK
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(
                W3CTraceContextPropagator.getInstance()))
            .build();
        
        INSTANCE.set(openTelemetry);
        TRACER.set(openTelemetry.getTracer(config.getServiceName(), config.getServiceVersion()));
        
        return openTelemetry;
    }
    
    /**
     * Creates environment-specific sampler for trace sampling.
     *
     * <p>Returns a TraceIdRatioBased sampler with sampling ratio based on environment:</p>
     * <ul>
     *   <li>Development: 1.0 (100% sampling)</li>
     *   <li>Staging/Test: 0.1 (10% sampling)</li>
     *   <li>Production: 0.01 (1% sampling)</li>
     *   <li>Default: 0.1 (10% sampling)</li>
     * </ul>
     *
     * @param config the tracing configuration with environment setting
     * @return TraceIdRatioBased sampler with environment-specific ratio
     */
    private static Sampler createSampler(TracingConfig config) {
        return switch (config.getEnvironment().toLowerCase()) {
            case "dev", "development" -> Sampler.traceIdRatioBased(1.0); // Sample everything in dev
            case "staging", "test" -> Sampler.traceIdRatioBased(0.1); // 10% sampling in staging
            case "prod", "production" -> Sampler.traceIdRatioBased(0.01); // 1% sampling in production
            default -> Sampler.traceIdRatioBased(0.1); // Default to 10%
        };
    }
    
    /**
     * Get the global OpenTelemetry instance.
     *
     * <p>Returns the initialized OpenTelemetry instance. Throws IllegalStateException
     * if called before {@link #initialize}.</p>
     *
     * @return the global OpenTelemetry instance
     * @throws IllegalStateException if TracingConfiguration not initialized
     */
    public static OpenTelemetry getInstance() {
        OpenTelemetry instance = INSTANCE.get();
        if (instance == null) {
            throw new IllegalStateException("TracingConfiguration not initialized. Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * Get the global tracer instance.
     *
     * <p>Returns the initialized Tracer instance with service name and version.
     * If not initialized, returns a no-op tracer (safe fallback).</p>
     *
     * @return the global tracer instance (never null; returns no-op if not initialized)
     */
    public static Tracer getTracer() {
        Tracer tracer = TRACER.get();
        if (tracer == null) {
            return OpenTelemetry.noop().getTracer("noop");
        }
        return tracer;
    }
    
    /**
     * Configuration class for tracing setup.
     */
    public static class TracingConfig {
        private final boolean enabled;
        private final String serviceName;
        private final String serviceVersion;
        private final String environment;
        private final String otlpEndpoint;
        
        public TracingConfig(boolean enabled, String serviceName, String serviceVersion, 
                           String environment, String otlpEndpoint) {
            this.enabled = enabled;
            this.serviceName = serviceName;
            this.serviceVersion = serviceVersion;
            this.environment = environment;
            this.otlpEndpoint = otlpEndpoint != null ? otlpEndpoint : "http://localhost:4317";
        }
        
        public boolean isEnabled() { return enabled; }
        public String getServiceName() { return serviceName; }
        public String getServiceVersion() { return serviceVersion; }
        public String getEnvironment() { return environment; }
        public String getOtlpEndpoint() { return otlpEndpoint; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean enabled = false;
            private String serviceName = "eventcloud-service";
            private String serviceVersion = "1.0.0";
            private String environment = "development";
            private String otlpEndpoint = "http://localhost:4317";
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder serviceName(String serviceName) {
                this.serviceName = serviceName;
                return this;
            }
            
            public Builder serviceVersion(String serviceVersion) {
                this.serviceVersion = serviceVersion;
                return this;
            }
            
            public Builder environment(String environment) {
                this.environment = environment;
                return this;
            }
            
            public Builder otlpEndpoint(String otlpEndpoint) {
                this.otlpEndpoint = otlpEndpoint;
                return this;
            }
            
            public TracingConfig build() {
                return new TracingConfig(enabled, serviceName, serviceVersion, environment, otlpEndpoint);
            }
        }
    }
}
