package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

/**
 * No-op implementation of MetricsCollector for testing and fallback scenarios.
 *
 * <p>
 * NoopMetricsCollector provides a null-object pattern implementation that does
 * nothing.
 * Useful for testing, disabled metrics scenarios, or fallback when registry is
 * unavailable.
 * </p>
 *
 * <p>
 * <b>Behavior:</b>
 * </p>
 * <ul>
 * <li>All methods succeed silently (no exceptions thrown)</li>
 * <li>{@link #getMeterRegistry()} returns null</li>
 * <li>No metrics are recorded or exported</li>
 * <li>Zero performance overhead (all methods are no-ops)</li>
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * 
 * <pre>{@code
 * // Test scenario
 * MetricsCollector noop = new NoopMetricsCollector();
 * noop.incrementCounter("test.metric"); // No-op, no error
 * noop.recordError("test.error", exception, Map.of()); // No-op, no error
 *
 * // Factory method
 * MetricsCollector noop = MetricsCollectorFactory.createNoop();
 *
 * // Null registry fallback
 * MetricsCollector safe = MetricsCollectorFactory.create(null); // Returns NoopMetricsCollector
 * }</pre>
 *
 * <p>
 * <b>Thread-Safety:</b> Thread-safe (no state).
 * </p>
 *
 * @doc.type class
 * @doc.purpose No-op null-object pattern implementation for testing and
 *              fallback scenarios
 * @doc.layer core
 * @doc.pattern Null Object, Implementation
 *
 *              <p>
 *              <b>Performance:</b> Zero overhead; all methods are no-ops.
 *              </p>
 *
 * @see MetricsCollector for interface
 * @see MetricsCollectorFactory#createNoop() for creation
 *
 * @author Platform Team
 * @created 2024-09-15
 * @updated 2025-10-29
 * @version 1.0.0
 * @type No-op Implementation (Null Object)
 * @purpose No-op metrics collector for testing, fallback, and disabled metrics
 *          scenarios
 * @pattern Null Object pattern (succeed silently without throwing exceptions)
 * @responsibility Silent no-op for all metrics operations; safe placeholder
 * @usage Created via MetricsCollectorFactory.createNoop() or directly; used in
 *        tests and fallback scenarios
 * @examples See class-level JavaDoc usage example
 * @testing Test that all methods succeed silently; verify no exceptions thrown;
 *          confirm getMeterRegistry() returns null
 * @notes Stateless; zero performance overhead; safe placeholder for disabled
 *        metrics
 */
public class NoopMetricsCollector implements MetricsCollector {

    /**
     * Legacy-style static factory used in older tests.
     *
     * @return new NoopMetricsCollector instance
     */
    public static NoopMetricsCollector getInstance() {
        return new NoopMetricsCollector();
    }

    /**
     * No-op increment operation.
     *
     * <p>
     * This method does nothing and returns immediately.
     * </p>
     *
     * @param metricName ignored
     * @param amount     ignored
     * @param tags       ignored
     */
    @Override
    public void increment(String metricName, double amount, Map<String, String> tags) {
        // No-op
    }

    /**
     * No-op error recording operation.
     *
     * <p>
     * This method does nothing and returns immediately.
     * </p>
     *
     * @param metricName ignored
     * @param e          ignored
     * @param tags       ignored
     */
    @Override
    public void recordError(String metricName, Exception e, Map<String, String> tags) {
        // No-op
    }

    /**
     * No-op counter increment operation.
     *
     * <p>
     * This method does nothing and returns immediately.
     * </p>
     *
     * @param metricName ignored
     * @param keyValues  ignored
     */
    @Override
    public void incrementCounter(String metricName, String... keyValues) {
        // No-op
    }

    /**
     * Returns null (no MeterRegistry in no-op implementation).
     *
     * @return null
     */
    @Override
    public MeterRegistry getMeterRegistry() {
        return null;
    }
}
