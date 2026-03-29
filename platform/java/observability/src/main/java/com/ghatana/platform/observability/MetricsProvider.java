/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Centralized metrics provider to eliminate duplicate registry instances.
 *
 * <p>Provides a singleton PrometheusMeterRegistry and MetricsCollector
 * to ensure consistent metrics collection across all services.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In service launcher @Provides methods:
 * @Provides
 * MeterRegistry meterRegistry() {
 *     return MetricsProvider.getRegistry();
 * }
 *
 * @Provides
 * MetricsCollector metricsCollector() {
 *     return MetricsProvider.getCollector();
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized metrics registry provider
 * @doc.layer platform
 * @doc.pattern Singleton
 */
public final class MetricsProvider {

    private static final PrometheusMeterRegistry REGISTRY = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private static final MetricsCollector COLLECTOR = MetricsCollectorFactory.create(REGISTRY);

    private MetricsProvider() {
        // Prevent instantiation
    }

    /**
     * Returns the singleton Prometheus meter registry.
     *
     * @return shared PrometheusMeterRegistry instance
     */
    public static PrometheusMeterRegistry getRegistry() {
        return REGISTRY;
    }

    /**
     * Returns the singleton metrics collector.
     *
     * @return shared MetricsCollector instance
     */
    public static MetricsCollector getCollector() {
        return COLLECTOR;
    }

    /**
     * Returns Prometheus scrape endpoint content.
     *
     * @return Prometheus-formatted metrics
     */
    public static String scrape() {
        return REGISTRY.scrape();
    }
}
