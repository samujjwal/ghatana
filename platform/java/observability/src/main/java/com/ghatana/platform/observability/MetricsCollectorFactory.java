package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Factory for creating MetricsCollector instances with consistent configuration.
 *
 * <p>MetricsCollectorFactory provides centralized creation of metrics collectors,
 * handling null-safety and fallback to no-op implementations when needed.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create with MeterRegistry
 * MeterRegistry registry = new SimpleMeterRegistry();
 * MetricsCollector collector = MetricsCollectorFactory.create(registry);
 *
 * // Create no-op for testing
 * MetricsCollector noop = MetricsCollectorFactory.createNoop();
 *
 * // Null-safe creation (returns noop if null registry)
 * MetricsCollector safe = MetricsCollectorFactory.create(null); // Returns NoopMetricsCollector
 * }</pre>
 *
 * <p><b>Thread-Safety:</b> All methods are static and thread-safe. Created instances
 * (SimpleMetricsCollector, NoopMetricsCollector) are also thread-safe.</p>
 *
 * @see MetricsCollector for interface
 * @see SimpleMetricsCollector for default implementation
 * @see NoopMetricsCollector for no-op implementation
 *
 * @author Platform Team
 * @created 2024-09-15
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Factory (Utility Class)
 * @purpose Centralized factory for creating MetricsCollector instances with null-safety
 * @pattern Static Factory pattern, Null Object pattern (for null registry)
 * @responsibility MetricsCollector instantiation, null-safety, fallback to no-op
 * @usage Call MetricsCollectorFactory.create(registry) to obtain MetricsCollector instance
 * @examples See class-level JavaDoc usage example
 * @testing Test null registry handling, noop creation, collector creation with valid registry
 * @notes Utility class (private constructor); null registry returns NoopMetricsCollector
 *
 * @doc.type class
 * @doc.purpose Centralized factory for creating MetricsCollector instances with null-safety
 * @doc.layer platform
 * @doc.pattern Factory
 */
public final class MetricsCollectorFactory {
    
    /**
     * Private constructor to prevent instantiation.
     *
     * <p>This is a utility class with only static methods.</p>
     */
    private MetricsCollectorFactory() {
        // Utility class
    }
    
    /**
     * Creates a MetricsCollector with the provided MeterRegistry.
     *
     * <p>If {@code registry} is null, returns {@link NoopMetricsCollector} for graceful
     * degradation (null-object pattern).</p>
     *
     * @param registry the meter registry to use (may be null)
     * @return a new SimpleMetricsCollector if registry is non-null, or NoopMetricsCollector otherwise
     */
    public static MetricsCollector create(MeterRegistry registry) {
        if (registry == null) {
            return new NoopMetricsCollector();
        }
        return new SimpleMetricsCollector(registry);
    }
    
    /**
     * Creates a no-op MetricsCollector for testing or disabled metrics.
     *
     * <p>The no-op collector silently ignores all metric operations. Useful for:
     * <ul>
     *   <li>Unit tests that don't require metrics assertions</li>
     *   <li>Environments where metrics are disabled</li>
     *   <li>Fallback when MeterRegistry is unavailable</li>
     * </ul>
     *
     * @return a no-op MetricsCollector instance (never null)
     */
    public static MetricsCollector createNoop() {
        return new NoopMetricsCollector();
    }
}
