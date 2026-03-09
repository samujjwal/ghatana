package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Core abstraction for collecting metrics across the platform.
 *
 * <p>
 * MetricsCollector provides a simplified metrics API that abstracts Micrometer,
 * allowing all product modules to collect metrics without direct Micrometer
 * dependency. This enables consistent metrics collection and easier testing via
 * no-op implementations.</p>
 *
 * <p>
 * <b>Architecture Role:</b></p>
 * <ul>
 * <li><b>Who creates:</b> Observability framework during initialization</li>
 * <li><b>Who uses:</b> All product modules and services</li>
 * <li><b>Storage:</b> Metrics stored in MeterRegistry</li>
 * <li><b>Lifecycle:</b> Singleton, created once and reused</li>
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b></p>
 * <pre>{@code
 * // Inject MetricsCollector
 * MetricsCollector metrics = ...;
 *
 * // Increment counter
 * metrics.incrementCounter("events.processed", "type", "order");
 *
 * // Record error
 * metrics.recordError("events.failed", exception, Map.of("type", "order"));
 *
 * // Access underlying registry for advanced usage
 * MeterRegistry registry = metrics.getMeterRegistry();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Core abstraction for collecting metrics with Micrometer backend
 * @doc.layer core
 * @doc.pattern Port, Facade
 *
 * @see BaseMetricsCollector for default implementation
 * @see NoopMetricsCollector for no-op implementation
 * @since 1.0.0
 *
 * @author Platform Team
 * @created 2024-09-15
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Service Interface
 * @purpose Core abstraction for collecting metrics across the platform using
 * Micrometer
 * @pattern Strategy pattern (interchangeable implementations), Facade pattern
 * (hides Micrometer complexity)
 * @responsibility Defines metrics collection API (counters, errors, custom
 * metrics)
 * @usage Implemented by BaseMetricsCollector, NoopMetricsCollector; used by all
 * product modules
 * @examples See class-level JavaDoc usage example and test classes
 * @testing Test counter increments, error recording, tag handling; use
 * NoopMetricsCollector for unit tests
 * @notes Platform abstraction to prevent product modules from directly
 * depending on Micrometer
 */
public interface MetricsCollector {

    /**
     * Increments a counter metric by a specified amount with tags.
     *
     * <p>
     * This method allows precise control over increment amount and metric tags.
     * Tags enable multi-dimensional metrics (e.g., by tenant, event type,
     * status).</p>
     *
     * @param metricName the name of the metric (e.g., "events.processed")
     * @param amount the amount to increment by (typically 1.0, but can be
     * fractional)
     * @param tags additional tags for the metric (key-value pairs for
     * dimensions)
     */
    void increment(String metricName, double amount, Map<String, String> tags);

    /**
     * Records an error occurrence in a metric.
     *
     * <p>
     * This method captures exception-based metrics, typically incrementing an
     * error counter. Implementations may extract exception type from {@code e}
     * for tagging.</p>
     *
     * @param metricName the name of the error metric (e.g., "events.failed")
     * @param e the exception that occurred (used for error classification)
     * @param tags additional tags for the metric (e.g., error type, tenant,
     * context)
     */
    void recordError(String metricName, Exception e, Map<String, String> tags);

    /**
     * Increments a counter metric by 1 with optional key-value tag pairs.
     *
     * <p>
     * This is the most common metrics method. Tags are specified as alternating
     * key-value strings (varargs). This method is optimized for the common case
     * of incrementing by 1.</p>
     *
     * <p>
     * <b>Example:</b></p>
     * <pre>{@code
     * metrics.incrementCounter("requests.total", "method", "POST", "status", "200");
     * }</pre>
     *
     * @param metricName the name of the metric (e.g., "requests.total")
     * @param keyValues optional key-value pairs for tags (key1, value1, key2,
     * value2, ...)
     */
    void incrementCounter(String metricName, String... keyValues);
    
    /**
     * Increments a counter metric by 1 with a Map of tags.
     *
     * @param metricName the name of the metric
     * @param tags tag map
     */
    default void incrementCounter(String metricName, Map<String, String> tags) {
        // Convert map to varargs
        if (tags.isEmpty()) {
            incrementCounter(metricName);
        } else {
            String[] keyValues = new String[tags.size() * 2];
            int i = 0;
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                keyValues[i++] = entry.getKey();
                keyValues[i++] = entry.getValue();
            }
            incrementCounter(metricName, keyValues);
        }
    }

    /**
     * Gets the underlying Micrometer MeterRegistry for advanced usage.
     *
     * <p>
     * <b>WARNING:</b> Prefer using the abstract methods when possible to
     * maintain abstraction. Direct registry access should be limited to core
     * modules that need advanced Micrometer features (timers, gauges,
     * distribution summaries).</p>
     *
     * <p>
     * Product modules should avoid this method and use {@link #incrementCounter},
     * {@link #increment}, or {@link #recordError} instead.</p>
     *
     * @return the meter registry (never null for production implementations)
     */
    MeterRegistry getMeterRegistry();

    /**
     * Convenience helper to record a duration in milliseconds as a Timer using
     * the underlying MeterRegistry. Implementations may provide more efficient
     * paths; default delegates to MeterRegistry.timer().
     *
     * @param name metric name
     * @param durationMs duration in milliseconds
     */
    default void recordTimer(String name, long durationMs) {
        try {
            getMeterRegistry().timer(name).record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Best-effort: avoid breaking production if registry not configured in tests
        }
    }

    /**
     * Compatibility overload that accepts alternating key/value tag pairs.
     *
     * <p>
     * Some call sites record timers with inline tags (e.g.
     * {@code "method", "POST", "status", "200"}). To preserve backward
     * compatibility during the Gradle 9.1 migration we provide a varargs
     * overload which currently delegates to the simpler
     * {@link #recordTimer(String, long)} implementation.
     *
     * <p>
     * Implementations are free to provide a richer behavior that records tags.
     * This default implementation intentionally keeps the change low-risk and
     * non-breaking for tests.
     *
     * @param name metric name
     * @param durationMs duration in milliseconds
     * @param keyValues optional alternating key/value tag pairs
     */
    default void recordTimer(String name, long durationMs, String... keyValues) {
        // Best-effort compatibility shim: ignore tags for now and delegate to the basic recorder.
        // This keeps existing call sites compiling while allowing a future improvement to honor tags.
        recordTimer(name, durationMs);
    }
    
    /**
     * Compatibility overload that accepts a Map of tags.
     *
     * @param name metric name
     * @param durationMs duration in milliseconds
     * @param tags tag map
     */
    default void recordTimer(String name, long durationMs, Map<String, String> tags) {
        recordTimer(name, durationMs);
    }

    /**
     * Convenience factory used by older tests to obtain a metrics collector.
     * Returns a no-op collector by default to avoid requiring Micrometer in tests.
     */
    static MetricsCollector create() {
        return com.ghatana.platform.observability.NoopMetricsCollector.getInstance();
    }

    /**
     * Convenience helper to record a confidence score or similar scalar value.
     * Delegates to DistributionSummary.
     *
     * @param name metric name
     * @param score value to record
     */
    default void recordConfidenceScore(String name, double score) {
        try {
            getMeterRegistry().summary(name).record(score);
        } catch (Exception ignored) {
            // noop in tests or when registry not available
        }
    }

    /**
     * Records a gauge value for metrics that represent current state.
     * Unlike counters, gauges can go up or down.
     *
     * @param name metric name
     * @param value the current value to record
     */
    default void recordGauge(String name, double value) {
        try {
            getMeterRegistry().gauge(name, value);
        } catch (Exception ignored) {
            // noop in tests or when registry not available
        }
    }

    /**
     * Records a gauge value with an AtomicLong source.
     *
     * @param name metric name
     * @param value the AtomicLong value
     */
    default void recordGauge(String name, long value) {
        recordGauge(name, (double) value);
    }
}
