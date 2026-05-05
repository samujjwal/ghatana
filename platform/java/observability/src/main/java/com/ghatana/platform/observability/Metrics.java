package com.ghatana.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * Metrics facade providing access to timers and counters via Micrometer.
 *
 * <p>Metrics wraps a MeterRegistry and provides convenient builder methods for creating
 * timers and counters with standard configurations (e.g., percentiles for latency tracking).</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Timer creation with standard percentiles (p50, p95, p99)</li>
 *   <li>Counter creation</li>
 *   <li>Direct registry access for advanced use cases</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * MeterRegistry registry = new SimpleMeterRegistry();
 * Metrics metrics = new Metrics(registry);
 *
 * // Timer for latency tracking (with percentiles)
 * Timer timer = metrics.timer("request.latency");
 * timer.record(() -> {
 *     // Business logic
 * });
 *
 * // Counter for request counts
 * Counter counter = metrics.counter("requests.total");
 * counter.increment();
 * }</pre>
 *
 * <p><b>Standard Configuration:</b></p>
 * <ul>
 *   <li>Timers: p50, p95, p99 percentiles enabled</li>
 *   <li>Counters: Basic counter with no additional configuration</li>
 * </ul>
 *
 * <p><b>Thread-Safety:</b> Thread-safe. MeterRegistry is thread-safe.</p>
 *
 * <p><b>Performance:</b> Metrics are created lazily and cached by MeterRegistry.
 * First access creates metric; subsequent accesses use cached instance (O(1)).</p>
 *
 * @see MetricsCollector for higher-level abstraction
 * @see io.micrometer.core.instrument.Timer for timer API
 * @see io.micrometer.core.instrument.Counter for counter API
 *
 * @author Platform Team
 * @created 2024-09-20
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Metrics Facade
 * @purpose Facade for creating timers and counters with standard percentile configurations
 * @pattern Facade pattern (simplifies Micrometer API)
 * @responsibility Timer creation (with percentiles), counter creation, registry access
 * @usage Instantiate with MeterRegistry; use timer() and counter() for metric creation
 * @examples See class-level JavaDoc usage example
 * @testing Test timer creation with percentiles, counter creation, registry access
 * @notes Simpler alternative to MetricsCollector for timer/counter-only use cases

 *
 * @doc.type class
 * @doc.purpose Metrics
 * @doc.layer core
 * @doc.pattern Metrics
*/
public class Metrics {
    /**
     * The underlying MeterRegistry for metric storage and export.
     */
    private final MeterRegistry registry;

    /**
     * Creates a Metrics instance with the given registry.
     *
     * @param registry the MeterRegistry to use (must not be null)
     */
    public Metrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Gets the underlying MeterRegistry.
     *
     * <p>Counters are monotonically increasing values (never decrease). Use for
     * counting events, requests, errors, etc.</p>
     *
     * @param name the counter name (e.g., "requests.total", "events.processed")
     * @return a configured Counter
     */
    public Counter counter(String name) {
        return Counter.builder(name)
            .register(registry);
    }

    /**
     * Creates a counter with key-value tag pairs.
     *
     * @param name the counter name
     * @param tags alternating key-value tag pairs (must be even count)
     * @return a configured Counter with the specified tags
     */
    public Counter counter(String name, String... tags) {
        return Counter.builder(name)
            .tags(Tags.of(tags))
            .register(registry);
    }

    /**
     * Creates a timer with standard percentile configuration.
     *
     * <p>Percentiles: p50 (0.5), p95 (0.95), p99 (0.99). Useful for latency tracking
     * and SLA monitoring.</p>
     *
     * @param name the timer name (e.g., "request.latency", "database.query.duration")
     * @return a configured Timer with percentiles
     */
    public Timer timer(String name) {
        return Timer.builder(name)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    /**
     * Creates a timer with standard percentile configuration and key-value tag pairs.
     *
     * @param name the timer name
     * @param tags alternating key-value tag pairs (must be even count)
     * @return a configured Timer with percentiles and the specified tags
     */
    public Timer timer(String name, String... tags) {
        return Timer.builder(name)
            .publishPercentiles(0.5, 0.95, 0.99)
            .tags(Tags.of(tags))
            .register(registry);
    }

    /**
     * Registers a gauge that reports a fixed {@code double} value.
     *
     * <p>Suitable for one-time startup or status metrics where the value
     * does not change after registration (e.g., startup duration, readiness flag).</p>
     *
     * @param name  the gauge name
     * @param value the fixed value to report
     */
    public void gauge(String name, double value) {
        java.util.concurrent.atomic.AtomicReference<Double> ref =
            new java.util.concurrent.atomic.AtomicReference<>(value);
        Gauge.builder(name, ref, java.util.concurrent.atomic.AtomicReference::get)
            .register(registry);
    }

    /**
     * Registers a gauge that reports a fixed {@code long} value converted to {@code double}.
     *
     * @param name  the gauge name
     * @param value the fixed value to report
     */
    public void gauge(String name, long value) {
        gauge(name, (double) value);
    }

    /**
     * Gets the underlying MeterRegistry.
     *
     * <p>Exposed for advanced use cases requiring direct registry access (gauges,
     * distribution summaries, custom meters). Prefer using {@link #timer} and
     * {@link #counter} when possible.</p>
     *
     * @return the MeterRegistry (never null)
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
