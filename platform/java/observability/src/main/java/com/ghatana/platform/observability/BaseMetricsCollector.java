package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base implementation of MetricsCollector with Micrometer integration.
 *
 * <p>BaseMetricsCollector provides common functionality for metrics collection including
 * tag conversion, null-safety, and MeterRegistry access. Subclasses implement specific
 * metrics collection strategies.</p>
 *
 * <p><b>Thread-Safety:</b> This class is thread-safe. The MeterRegistry is thread-safe,
 * and all methods are stateless.</p>
 *
 * <p><b>Null-Safety:</b> All methods perform null checks on the registry and gracefully
 * degrade to no-op behavior if the registry is null.</p>
 *
 * <p><b>Performance:</b> Tag conversion incurs O(n) overhead where n is the number of tags.
 * Counter operations are O(1) after initial metric creation. Metrics are cached internally
 * by MeterRegistry.</p>
 *
 * @see MetricsCollector for interface documentation
 * @see SimpleMetricsCollector for concrete implementation
 * @see NoopMetricsCollector for testing
 *
 * @doc.type class
 * @doc.purpose Base implementation for metrics collection with Micrometer tag conversion
 * @doc.layer core
 * @doc.pattern Template Method, Base Implementation
 *
 * @author Platform Team
 * @created 2024-09-15
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Abstract Implementation
 * @purpose Base implementation providing common metrics collection functionality
 * @pattern Template Method pattern (subclasses can override behavior)
 * @responsibility Tag conversion, null-safety, MeterRegistry delegation
 * @usage Extended by SimpleMetricsCollector and other concrete implementations
 * @examples See SimpleMetricsCollector for typical usage
 * @testing Test tag conversion, null-safety, counter operations; verify Micrometer interactions
 * @notes Thread-safe; null registry supported for graceful degradation
 */
public abstract class BaseMetricsCollector implements MetricsCollector {
    
    /**
     * The underlying Micrometer MeterRegistry for metrics storage.
     *
     * <p>This field is protected to allow subclasses to access the registry directly.
     * May be null in testing scenarios (NoopMetricsCollector).</p>
     */
    protected final MeterRegistry registry;

    /**
     * Constructs a BaseMetricsCollector with the specified MeterRegistry.
     *
     * <p>Subclasses MUST call this constructor to initialize the registry field.
     * Null registry is permitted for testing scenarios.</p>
     *
     * @param registry the MeterRegistry to use for metrics storage (may be null)
     */
    protected BaseMetricsCollector(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void increment(String metricName, double amount, Map<String, String> tags) {
        if (registry == null) {
            return; // Graceful degradation for null registry
        }
        // Convert Map<String, String> tags to Micrometer Tags
        Tags micrometerTags = tags != null ? 
            Tags.of(tags.entrySet().stream()
                .flatMap(e -> Tags.of(e.getKey(), String.valueOf(e.getValue())).stream())
                .toList()) : Tags.empty();
        registry.counter(metricName, micrometerTags).increment(amount);
    }

    @Override
    public void recordError(String metricName, Exception e, Map<String, String> tags) {
        // Default implementation: increment error counter by 1
        // Subclasses can override to extract exception type or stack traces
        increment(metricName, 1.0, tags);
    }

    @Override
    public void incrementCounter(String metricName, String... keyValues) {
        if (registry == null) {
            return; // Graceful degradation for null registry
        }
        if (keyValues == null || keyValues.length == 0) {
            registry.counter(metricName).increment();
            return;
        }
        // Micrometer expects alternating key-value strings
        registry.counter(metricName, keyValues).increment();
    }

    @Override
    public MeterRegistry getMeterRegistry() {
        return registry;
    }

    @Override
    public void recordTimer(String name, long durationMs) {
        if (registry == null) {
            return;
        }
        registry.timer(name).record(durationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordTimer(String name, long durationMs, String... keyValues) {
        if (registry == null) {
            return;
        }
        if (keyValues == null || keyValues.length == 0) {
            recordTimer(name, durationMs);
            return;
        }
        registry.timer(name, keyValues).record(durationMs, TimeUnit.MILLISECONDS);
    }
}
