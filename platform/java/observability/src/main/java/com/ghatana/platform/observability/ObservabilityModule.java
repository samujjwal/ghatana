/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.observability;

import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;

/**
 * ActiveJ DI module that provides observability services.
 *
 * <p>Wires {@link MetricsRegistry}, {@link MetricsCollector},
 * {@link TracingManager}, and {@link Metrics} into the ActiveJ
 * injection container, ensuring consistent metric registries
 * across all components.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(new ObservabilityModule());
 * MetricsCollector collector = injector.getInstance(MetricsCollector.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for observability components
 * @doc.layer platform
 * @doc.pattern Module
 */
public class ObservabilityModule extends AbstractModule {

    @Provides
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Provides
    OpenTelemetry openTelemetry() {
        try {
            return GlobalOpenTelemetry.get();
        } catch (Exception e) {
            return OpenTelemetry.noop();
        }
    }

    @Provides
    MetricsRegistry metricsRegistry(MeterRegistry meterRegistry, OpenTelemetry openTelemetry) {
        return MetricsRegistry.initialize(
                meterRegistry,
                openTelemetry,
                "ghatana",
                "default",
                "1.0.0");
    }

    @Provides
    MetricsCollector metricsCollector(MeterRegistry meterRegistry) {
        return new SimpleMetricsCollector(meterRegistry);
    }

    @Provides
    TracingManager tracingManager(OpenTelemetry openTelemetry) {
        return new TracingManager(openTelemetry);
    }

    @Provides
    Metrics metrics(MeterRegistry meterRegistry) {
        return new Metrics(meterRegistry);
    }
}
