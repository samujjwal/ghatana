package com.ghatana.platform.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manager for creating and managing tracing providers.
 *
 * <p>TracingManager provides lifecycle management for tracing providers, handling
 * creation, caching, and access to named tracing providers. Each provider represents
 * a logical scope for trace instrumentation (e.g., "ingress", "validation", "routing").</p>
 *
 * <p><b>Architecture:</b></p>
 * @doc.type class
 * @doc.purpose Factory and lifecycle manager for OpenTelemetry tracing providers
 * @doc.layer core
 * @doc.pattern Factory, Manager
 * <ul>
 *   <li><b>Singleton Pattern:</b> Typically one TracingManager per application</li>
 *   <li><b>Provider Registry:</b> Caches TracingProvider instances by name</li>
 *   <li><b>Factory Methods:</b> Static factories for common configurations</li>
 *   <li><b>OpenTelemetry Integration:</b> Uses OpenTelemetry SDK for span export</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create manager with default OTLP endpoint
 * TracingManager manager = TracingManager.createDefault("my-service", "1.0.0");
 *
 * // Get provider for a logical scope
 * TracingProvider ingress = manager.getProvider("ingress");
 * Span span = ingress.createSpan("process-request");
 *
 * // Access all providers
 * Map<String, TracingProvider> providers = manager.getProviders();
 * }</pre>
 *
 * <p><b>Concurrency:</b> Thread-safe. Provider map uses {@code computeIfAbsent} for
 * atomic provider creation.</p>
 *
 * <p><b>Performance:</b> Provider lookup is O(1). First access creates provider; subsequent
 * accesses use cached instance.</p>
 *
 * @see TracingProvider for span creation API
 * @see OpenTelemetryTracingProvider for implementation
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Manager
 * @purpose Lifecycle management for tracing providers with OpenTelemetry integration
 * @pattern Manager pattern (lifecycle coordination), Factory pattern (static creation methods)
 * @responsibility Provider creation, caching, and access; OpenTelemetry configuration
 * @usage Created once at application startup; providers accessed via getProvider(name)
 * @examples See class-level JavaDoc usage example; createForTesting() for unit tests
 * @testing Use createForTesting() or createNoOp() to avoid OTLP collector dependencies
 * @notes Thread-safe provider registry; supports no-op mode for testing
 */
public class TracingManager {

    private static final Logger log = LoggerFactory.getLogger(TracingManager.class);

    /**
     * Provider registry keyed by provider name.
     *
     * <p>Providers are created lazily on first access via {@link #getProvider(String)}.
     * Thread-safe via {@code computeIfAbsent}.</p>
     */
    private final Map<String, TracingProvider> providers = new HashMap<>();
    
    /**
     * The OpenTelemetry instance for span export and context propagation.
     */
    private final OpenTelemetry openTelemetry;

    /**
     * Creates a new TracingManager with the specified OpenTelemetry instance.
     *
     * <p>Use this constructor when you have custom OpenTelemetry configuration.
     * For standard configurations, prefer static factory methods.</p>
     *
     * @param openTelemetry the OpenTelemetry instance (must not be null)
     */
    public TracingManager(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * Gets a tracing provider with the specified name.
     *
     * <p>If the provider does not exist, it is created and cached. Subsequent calls
     * with the same name return the cached instance.</p>
     *
     * <p><b>Thread-Safety:</b> Atomic provider creation via {@code computeIfAbsent}.</p>
     *
     * @param name the provider name (e.g., "ingress", "validation", "routing")
     * @return the tracing provider (never null)
     */
    public TracingProvider getProvider(String name) {
        return providers.computeIfAbsent(name, k -> createProvider(k));
    }

    /**
     * Creates a tracing provider with the specified name.
     *
     * <p>Called internally by {@link #getProvider(String)} when a provider is first accessed.</p>
     *
     * @param name the provider name
     * @return the newly created tracing provider
     */
    private TracingProvider createProvider(String name) {
        log.info("Creating tracing provider: {}", name);
        return new OpenTelemetryTracingProvider(openTelemetry, name);
    }

    /**
     * Gets all tracing providers.
     *
     * <p>Returns a defensive copy of the provider map. Modifications to the returned
     * map do not affect the internal registry.</p>
     *
     * @return the tracing providers (never null)
     */
    public Map<String, TracingProvider> getProviders() {
        return new HashMap<>(providers);
    }

    /**
     * Creates a default TracingManager with the specified service name and version.
     *
     * <p>Uses default OTLP gRPC endpoint: {@code http://localhost:4317}.</p>
     *
     * <p><b>Configuration:</b></p>
     * <ul>
     *   <li>Exporter: OTLP gRPC</li>
     *   <li>Span Processor: Batch (default batch size and schedule)</li>
     *   <li>Sampler: Always on (100% sampling)</li>
     *   <li>Propagation: W3C Trace Context</li>
     * </ul>
     *
     * @param serviceName the service name (e.g., "ingress-service")
     * @param serviceVersion the service version (e.g., "1.0.0")
     * @return the tracing manager configured with default settings
     */
    public static TracingManager createDefault(String serviceName, String serviceVersion) {
        return createDefault(serviceName, serviceVersion, "http://localhost:4317");
    }

    /**
     * Creates a default TracingManager with the specified service name, version, and collector endpoint.
     *
     * <p><b>Configuration:</b></p>
     * <ul>
     *   <li>Resource: service.name={serviceName}, service.version={serviceVersion}</li>
     *   <li>Exporter: OTLP gRPC to specified endpoint</li>
     *   <li>Span Processor: Batch (5s max delay, 512 max queue size)</li>
     *   <li>Sampler: Always on (100% sampling)</li>
     *   <li>Propagation: W3C Trace Context</li>
     *   <li>Timeout: 5 seconds for export</li>
     * </ul>
     *
     * @param serviceName the service name (e.g., "ingress-service")
     * @param serviceVersion the service version (e.g., "1.0.0")
     * @param collectorEndpoint the OTLP gRPC collector endpoint (e.g., "http://jaeger:4317")
     * @return the tracing manager configured for production use
     */
    public static TracingManager createDefault(String serviceName, String serviceVersion, String collectorEndpoint) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put("service.name", serviceName)
                        .put("service.version", serviceVersion)
                        .build()));

        SpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(collectorEndpoint)
                .setTimeout(5, TimeUnit.SECONDS)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setSampler(Sampler.alwaysOn())
                .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        return new TracingManager(openTelemetry);
    }

    /**
     * Creates a no-op TracingManager.
     *
     * <p>No spans are exported. Useful for disabling tracing in environments where
     * the collector is not available (e.g., local development without Docker).</p>
     *
     * @return the no-op tracing manager
     */
    public static TracingManager createNoOp() {
        return new TracingManager(OpenTelemetry.noop());
    }

    /**
     * Creates a TracingManager for testing.
     *
     * <p>Uses {@link InMemorySpanExporter} to capture spans in memory for assertions.
     * Spans are not exported to external collector.</p>
     *
     * <p><b>Configuration:</b></p>
     * <ul>
     *   <li>Exporter: InMemory (spans accessible via InMemorySpanExporter.getFinishedSpanItems())</li>
     *   <li>Span Processor: Simple (synchronous export)</li>
     *   <li>Sampler: Always on (100% sampling)</li>
     * </ul>
     *
     * @param serviceName the service name (e.g., "test-service")
     * @param serviceVersion the service version (e.g., "1.0.0-test")
     * @return the tracing manager configured for unit tests
     */
    public static TracingManager createForTesting(String serviceName, String serviceVersion) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put("service.name", serviceName)
                        .put("service.version", serviceVersion)
                        .build()));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(SimpleSpanProcessor.create(new InMemorySpanExporter()))
                .setSampler(Sampler.alwaysOn())
                .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        return new TracingManager(openTelemetry);
    }
}
