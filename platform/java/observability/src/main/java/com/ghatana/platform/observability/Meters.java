package com.ghatana.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Utility class for standardized meter creation with Micrometer.
 *
 * <p>Meters provides static factory methods for creating counters and timers with
 * consistent tag handling and naming conventions.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * MeterRegistry registry = new SimpleMeterRegistry();
 *
 * // Create counter with tags
 * Counter counter = Meters.counter(registry, "requests.total", "method", "POST", "status", "200");
 * counter.increment();
 *
 * // Create timer with tags
 * Timer timer = Meters.timer(registry, "request.duration", "endpoint", "/api/events");
 * timer.record(() -> {
 *     // Business logic
 * });
 * }</pre>
 *
 * <p><b>Thread-Safety:</b> Thread-safe (all methods are stateless static factories).</p>
 *
 * <p><b>Performance:</b> Meters are created lazily and cached by MeterRegistry.
 * @doc.type class
 * @doc.purpose Factory for standardized Micrometer counter and timer creation
 * @doc.layer core
 * @doc.pattern Factory, Utility
 * First access creates meter; subsequent accesses use cached instance (O(1)).</p>
 *
 * @see Metrics for facade with percentile configuration
 * @see MetricsCollector for higher-level abstraction
 *
 * @author Platform Team
 * @created 2024-09-20
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Utility Class (Static Factory)
 * @purpose Standardized meter creation with consistent tag handling
 * @pattern Static Factory pattern
 * @responsibility Counter and timer creation with tag support
 * @usage Call Meters.counter() or Meters.timer() with registry, name, and tags (varargs)
 * @examples See class-level JavaDoc usage example
 * @testing Test counter creation, timer creation, tag handling
 * @notes Utility class (private constructor); simpler alternative to Metrics facade
 */
public final class Meters {
    /**
     * Private constructor to prevent instantiation.
     */
    private Meters() { }

    /**
     * Creates a counter with the specified name and tags.
     *
     * <p>Tags are specified as alternating key-value strings (varargs).</p>
     *
     * @param registry the meter registry to use
     * @param name the counter name (e.g., "requests.total")
     * @param tags optional key-value tag pairs (key1, value1, key2, value2, ...)
     * @return the created counter (or cached instance if already exists)
     */
    public static Counter counter(MeterRegistry registry, String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    /**
     * Creates a timer with the specified name and tags.
     *
     * <p>Tags are specified as alternating key-value strings (varargs).
     * Timers are useful for measuring latency and operation duration.</p>
     *
     * @param registry the meter registry to use
     * @param name the timer name (e.g., "request.duration")
     * @param tags optional key-value tag pairs (key1, value1, key2, value2, ...)
     * @return the created timer (or cached instance if already exists)
     */
    public static Timer timer(MeterRegistry registry, String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }
}
