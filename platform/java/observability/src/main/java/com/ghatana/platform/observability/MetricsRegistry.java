package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Centralized metrics registry with Micrometer and OpenTelemetry integration.
 *
 * <p>MetricsRegistry is a singleton providing centralized metrics management following
 * EventCloud taxonomy. It integrates both Micrometer and OpenTelemetry for comprehensive
 * metrics collection and export.</p>
 *
 * <p><b>Architecture:</b></p>
 * <ul>
 *   <li><b>Singleton Pattern:</b> Single global instance via {@link #getInstance()}</li>
 *   <li><b>Dual Integration:</b> Micrometer for JVM-based metrics, OpenTelemetry for OTLP export</li>
 *   <li><b>Taxonomy:</b> EventCloud module-based metrics naming convention</li>
 *   <li><b>Common Tags:</b> All metrics tagged with service, environment, version</li>
 * </ul>
 *
 * <p><b>Metrics Taxonomy:</b></p>
 * <pre>
 * Module-Based Naming:
 *   ingress.requests.total              - Ingress module HTTP requests
 *   eventlog.append.latency             - EventLog module append latency
 *   validation.events.processed.total   - Validation module event processing
 *   pattern.evaluations.total           - Pattern module evaluations
 *   routing.deliveries.total            - Routing module deliveries
 *   obs.anomaly.signals.total           - Observability module anomaly detection
 *   admin.api.latency                   - Admin module API latency
 *   security.jwt.validations.total      - Security module JWT validations
 * </pre>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Initialize once at application startup
 * MetricsRegistry.initialize(meterRegistry, openTelemetry, "ingress", "prod", "1.0.0");
 *
 * // Access singleton
 * MetricsRegistry registry = MetricsRegistry.getInstance();
 *
 * // Use domain-specific methods
 * registry.ingressRequestsTotal("POST", "/events").increment();
 * registry.eventlogAppendLatency("partition-0").record(latency);
 *
 * // Custom metrics
 * registry.customCounter("custom.metric", "Custom metric", Tag.of("key", "value"));
 * }</pre>
 *
 * <p><b>Thread-Safety:</b> Thread-safe via synchronized initialization and thread-safe
 * Micrometer/OpenTelemetry registries.</p>
 *
 * <p><b>Performance:</b> Metrics are created lazily and cached by Micrometer. First
 * access creates metric; subsequent accesses use cached instance (O(1) lookup).</p>
 *
 * @see MetricsCollector for product module abstraction
 * @see io.micrometer.core.instrument.MeterRegistry for Micrometer documentation
 *
 * @author Platform Team
 * @created 2024-10-05
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Registry (Singleton)
 * @purpose Centralized metrics registry following EventCloud taxonomy with dual Micrometer/OpenTelemetry integration
 * @pattern Singleton pattern (global instance), Registry pattern (metric catalog)
 * @responsibility Metric creation by module and operation; common tag management; scrape endpoint
 * @usage Initialize once at startup; access via getInstance(); use module-specific methods for EventCloud metrics
 * @examples See class-level JavaDoc usage example; consult module-specific methods for taxonomy
 * @testing Initialize with SimpleMeterRegistry for tests; verify metric names and tags
 * @notes EventCloud taxonomy enforced via method naming; all metrics auto-tagged with service/environment/version
 * @doc.type class
 * @doc.purpose Centralized metrics registry with EventCloud taxonomy and dual Micrometer/OpenTelemetry integration
 * @doc.layer observability
 * @doc.pattern Singleton, Registry
 */
public final class MetricsRegistry {
    
    /**
     * Singleton instance holder using AtomicReference for thread-safe initialization.
     */
    private static final AtomicReference<MetricsRegistry> INSTANCE = new AtomicReference<>();
    
    /**
     * Micrometer MeterRegistry for JVM-based metrics collection.
     */
    private final MeterRegistry meterRegistry;
    
    /**
     * OpenTelemetry Meter for OTLP-based metrics export.
     */
    private final Meter otelMeter;
    
    /**
     * Service name for common tags (e.g., "ingress", "validation").
     */
    private final String serviceName;
    
    /**
     * Environment name for common tags (e.g., "prod", "staging", "dev").
     */
    private final String environment;
    
    /**
     * Service version for common tags (e.g., "1.0.0").
     */
    private final String version;
    
    /**
     * Private constructor to enforce singleton pattern.
     *
     * <p>Use {@link #initialize} to create the singleton instance.</p>
     *
     * @param meterRegistry the Micrometer MeterRegistry
     * @param openTelemetry the OpenTelemetry instance
     * @param serviceName the service name (e.g., "ingress")
     * @param environment the environment (e.g., "prod")
     * @param version the service version (e.g., "1.0.0")
     */
    private MetricsRegistry(MeterRegistry meterRegistry, OpenTelemetry openTelemetry, 
                           String serviceName, String environment, String version) {
        this.meterRegistry = meterRegistry;
        this.otelMeter = openTelemetry.getMeterProvider()
            .meterBuilder("com.ghatana.eventcloud")
            .setInstrumentationVersion(version)
            .build();
        this.serviceName = serviceName;
        this.environment = environment;
        this.version = version;
    }
    
    /**
     * Initializes the global metrics registry.
     *
     * <p>This method MUST be called once at application startup before any call to
     * {@link #getInstance()}. Subsequent calls replace the existing instance.</p>
     *
     * <p><b>Thread-Safety:</b> Synchronized to prevent concurrent initialization.</p>
     *
     * @param meterRegistry the Micrometer MeterRegistry (e.g., PrometheusMeterRegistry)
     * @param openTelemetry the OpenTelemetry instance for OTLP export
     * @param serviceName the service name (e.g., "ingress", "validation")
     * @param environment the environment (e.g., "prod", "staging", "dev")
     * @param version the service version (e.g., "1.0.0")
     * @return the initialized MetricsRegistry instance
     */
    public static synchronized MetricsRegistry initialize(MeterRegistry meterRegistry, 
                                                         OpenTelemetry openTelemetry,
                                                         String serviceName, 
                                                         String environment, 
                                                         String version) {
        MetricsRegistry registry = new MetricsRegistry(meterRegistry, openTelemetry, 
                                                      serviceName, environment, version);
        INSTANCE.set(registry);
        return registry;
    }
    
    /**
     * Gets the global metrics registry instance.
     *
     * <p><b>Precondition:</b> {@link #initialize} must be called first.</p>
     *
     * @return the MetricsRegistry singleton instance
     * @throws IllegalStateException if registry not initialized
     */
    public static MetricsRegistry getInstance() {
        MetricsRegistry registry = INSTANCE.get();
        if (registry == null) {
            throw new IllegalStateException("MetricsRegistry not initialized. Call initialize() first.");
        }
        return registry;
    }
    
    /**
     * Creates common tags for all metrics.
     *
     * <p>Tags: service={serviceName}, environment={environment}, version={version}</p>
     *
     * @return the common tags applied to all metrics
     */
    private Tags commonTags() {
        return Tags.of(
            "service", serviceName,
            "environment", environment,
            "version", version
        );
    }
    
    // ============================================================================
    // Ingress Module Metrics
    // ============================================================================
    
    public Counter ingressRequestsTotal(String method, String endpoint) {
        return Counter.builder("ingress.requests.total")
            .description("Total HTTP requests received")
            .tags(commonTags().and("method", method, "endpoint", endpoint))
            .register(meterRegistry);
    }
    
    public Timer ingressLatency(String method, String endpoint) {
        return Timer.builder("ingress.latency")
            .description("Request processing latency")
            .tags(commonTags().and("method", method, "endpoint", endpoint))
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }
    
    public Counter ingressAuthFailures() {
        return Counter.builder("ingress.auth.failures.total")
            .description("Authentication failures")
            .tags(commonTags())
            .register(meterRegistry);
    }
    
    public Counter ingressRateLimitHits() {
        return Counter.builder("ingress.rate_limit.hits.total")
            .description("Rate limit violations")
            .tags(commonTags())
            .register(meterRegistry);
    }
    
    // EventLog Module Metrics
    
    public Timer eventlogAppendLatency(String partitionId) {
        return Timer.builder("eventlog.append.latency")
            .description("Event append latency")
            .tags(commonTags().and("partition_id", partitionId))
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
    }
    
    public Counter eventlogAppendTotal(String partitionId) {
        return Counter.builder("eventlog.append.total")
            .description("Total events appended")
            .tags(commonTags().and("partition_id", partitionId))
            .register(meterRegistry);
    }
    
    public Counter eventlogAppendFailures(String partitionId) {
        return Counter.builder("eventlog.append.failures.total")
            .description("Failed append operations")
            .tags(commonTags().and("partition_id", partitionId))
            .register(meterRegistry);
    }
    
    public Gauge eventlogBacklogSize(String partitionId, java.util.function.Supplier<Number> valueSupplier) {
        return Gauge.builder("eventlog.backlog.size", valueSupplier)
            .description("Current backlog size per partition")
            .tags(commonTags().and("partition_id", partitionId))
            .register(meterRegistry);
    }
    
    // Validation Module Metrics
    
    public Counter validationEventsProcessed(String eventType) {
        return Counter.builder("validation.events.processed.total")
            .description("Events processed")
            .tags(commonTags().and("event_type", eventType))
            .register(meterRegistry);
    }
    
    public Counter validationEventsPassed(String eventType) {
        return Counter.builder("validation.events.passed.total")
            .description("Events that passed validation")
            .tags(commonTags().and("event_type", eventType))
            .register(meterRegistry);
    }
    
    public Counter validationEventsFailed(String eventType, String reason) {
        return Counter.builder("validation.events.failed.total")
            .description("Events that failed validation")
            .tags(commonTags().and("event_type", eventType, "reason", reason))
            .register(meterRegistry);
    }
    
    public Timer validationLatency(String eventType) {
        return Timer.builder("validation.latency")
            .description("Validation processing latency")
            .tags(commonTags().and("event_type", eventType))
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
    }
    
    // AI-specific metrics (Phase 1)
    
    public Timer validationVectorizationLatency(String modelId) {
        return Timer.builder("validation.vectorization.latency")
            .description("Vectorization processing time")
            .tags(commonTags().and("model_id", modelId))
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
    }
    
    public Counter validationVectorizationErrors(String modelId, String errorType) {
        return Counter.builder("validation.vectorization.errors.total")
            .description("Vectorization failures")
            .tags(commonTags().and("model_id", modelId, "error_type", errorType))
            .register(meterRegistry);
    }
    
    public Timer validationSimilaritySearchLatency() {
        return Timer.builder("validation.similarity.search.latency")
            .description("Similarity search latency")
            .tags(commonTags())
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
    }
    
    // Pattern Module Metrics
    
    public Counter patternEvaluationsTotal(String patternId) {
        return Counter.builder("pattern.evaluations.total")
            .description("Pattern evaluations performed")
            .tags(commonTags().and("pattern_id", patternId))
            .register(meterRegistry);
    }
    
    public Counter patternMatchesTotal(String patternId) {
        return Counter.builder("pattern.matches.total")
            .description("Pattern matches found")
            .tags(commonTags().and("pattern_id", patternId))
            .register(meterRegistry);
    }
    
    public Timer patternEngineLatency(String patternType) {
        return Timer.builder("pattern.engine.latency")
            .description("Pattern engine processing latency")
            .tags(commonTags().and("pattern_type", patternType))
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
    }
    
    // Routing Module Metrics
    
    public Counter routingDeliveriesTotal(String endpointType) {
        return Counter.builder("routing.deliveries.total")
            .description("Delivery attempts")
            .tags(commonTags().and("endpoint_type", endpointType))
            .register(meterRegistry);
    }
    
    public Counter routingDeliveriesSuccess(String endpointType) {
        return Counter.builder("routing.deliveries.success.total")
            .description("Successful deliveries")
            .tags(commonTags().and("endpoint_type", endpointType))
            .register(meterRegistry);
    }
    
    public Counter routingDeliveriesFailed(String endpointType, String reason) {
        return Counter.builder("routing.deliveries.failed.total")
            .description("Failed deliveries")
            .tags(commonTags().and("endpoint_type", endpointType, "reason", reason))
            .register(meterRegistry);
    }
    
    public Gauge routingDlqSize(String endpointType, java.util.function.Supplier<Number> valueSupplier) {
        return Gauge.builder("routing.dlq.size", valueSupplier)
            .description("Dead letter queue size")
            .tags(commonTags().and("endpoint_type", endpointType))
            .register(meterRegistry);
    }
    
    // Observability Module Metrics
    
    public Counter obsAnomalySignals(String detectorType) {
        return Counter.builder("obs.anomaly.signals.total")
            .description("Anomaly signals detected")
            .tags(commonTags().and("detector_type", detectorType))
            .register(meterRegistry);
    }
    
    public Counter obsAlertsTotal(String alertType, String severity) {
        return Counter.builder("obs.alerts.fired.total")
            .description("Alerts fired")
            .tags(commonTags().and("alert_type", alertType, "severity", severity))
            .register(meterRegistry);
    }
    
    // Admin Module Metrics
    
    public Timer adminApiLatency(String operation) {
        return Timer.builder("admin.api.latency")
            .description("Admin API latency")
            .tags(commonTags().and("operation", operation))
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
    }
    
    public Counter adminRbacDenials(String resource, String action) {
        return Counter.builder("admin.rbac.denials.total")
            .description("RBAC access denials")
            .tags(commonTags().and("resource", resource, "action", action))
            .register(meterRegistry);
    }
    
    // Security Module Metrics
    
    public Counter securityJwtValidations() {
        return Counter.builder("security.jwt.validations.total")
            .description("JWT validations")
            .tags(commonTags())
            .register(meterRegistry);
    }
    
    public Counter securityJwtFailures(String reason) {
        return Counter.builder("security.jwt.failures.total")
            .description("JWT validation failures")
            .tags(commonTags().and("reason", reason))
            .register(meterRegistry);
    }
    
    // Utility methods
    
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
    
    public Meter getOtelMeter() {
        return otelMeter;
    }
    
    /**
     * Create a custom timer with the service's common tags.
     */
    public Timer customTimer(String name, String description, Tag... additionalTags) {
        return Timer.builder(name)
            .description(description)
            .tags(commonTags().and(additionalTags))
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
    }
    
    /**
     * Create a custom counter with the service's common tags.
     */
    public Counter customCounter(String name, String description, Tag... additionalTags) {
        return Counter.builder(name)
            .description(description)
            .tags(commonTags().and(additionalTags))
            .register(meterRegistry);
    }

    /** Convenience counter with single tag key/value for existing call sites. */
    public Counter counter(String name, String tagKey, String tagValue) {
        return Counter.builder(name)
            .description(name)
            .tags(commonTags().and(tagKey, tagValue))
            .register(meterRegistry);
    }

    /** Convenience counter without tags for legacy call sites. */
    public Counter counter(String name) {
        return Counter.builder(name)
            .description(name)
            .tags(commonTags())
            .register(meterRegistry);
    }

    /** Expose underlying meters for lightweight introspection used by tests/collectors. */
    public java.util.Collection<io.micrometer.core.instrument.Meter> getMeters() {
        return meterRegistry.getMeters();
    }

    /**
     * If the underlying registry is a PrometheusMeterRegistry, return its scrape output.
     * Otherwise return an empty string for compatibility.
     */
    public String scrape() {
        if (meterRegistry instanceof io.micrometer.prometheus.PrometheusMeterRegistry prometheus) {
            return prometheus.scrape();
        }
        return "";
    }
}
