/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Launcher for auto-bootstrapping observability components without ActiveJ DI.
 *
 * <p><b>Purpose</b><br>
 * Provides a standalone launcher for initializing metrics, tracing, and observability
 * infrastructure without depending on ActiveJ DI. This enables observability setup
 * in any application context (Spring, manual, other frameworks) without the
 * ActiveJ DI dependency that was previously blocking the launcher.</p>
 *
 * <p><b>Architecture</b><br>
 * Uses manual constructor injection pattern instead of ActiveJ DI launcher integration.
 * This provides a stable, long-term solution that doesn't depend on the unstable
 * ActiveJ DI API (activej-inject) that was the original blocker.</p>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Basic usage with defaults
 * ObservabilityLauncher launcher = ObservabilityLauncher.builder()
 *     .serviceName("my-service")
 *     .build();
 *
 * MetricsCollector metrics = launcher.getMetricsCollector();
 * TracingManager tracing = launcher.getTracingManager();
 *
 * // With custom configuration
 * ObservabilityLauncher launcher = ObservabilityLauncher.builder()
 *     .serviceName("my-service")
 *     .serviceVersion("1.0.0")
 *     .enablePrometheusMetrics(true)
 *     .enableTracing(true)
 *     .build();
 *
 * // Shutdown when done
 * launcher.shutdown();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Standalone observability launcher without ActiveJ DI dependency
 * @doc.layer platform
 * @doc.pattern Builder + Factory
 */
public final class ObservabilityLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityLauncher.class);

    private final String serviceName;
    private final String serviceVersion;
    private final MeterRegistry meterRegistry;
    private final MetricsCollector metricsCollector;
    private final TracingManager tracingManager;
    private final MetricsRegistry metricsRegistry;
    private final OpenTelemetry openTelemetry;

    private ObservabilityLauncher(Builder builder) {
        this.serviceName = Objects.requireNonNull(builder.serviceName, "serviceName is required");
        this.serviceVersion = builder.serviceVersion != null ? builder.serviceVersion : "1.0.0";

        // Initialize MeterRegistry (always use SimpleMeterRegistry for now)
        this.meterRegistry = new SimpleMeterRegistry();
        logger.info("Initialized SimpleMeterRegistry for service: {}", serviceName);

        // Initialize OpenTelemetry
        OpenTelemetry telemetry;
        if (builder.enableTracing) {
            SpanExporter spanExporter = builder.spanExporter != null 
                ? builder.spanExporter 
                : new InMemorySpanExporter();
            
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
            
            telemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
            
            logger.info("Initialized OpenTelemetry with tracing for service: {}", serviceName);
        } else {
            try {
                telemetry = GlobalOpenTelemetry.get();
            } catch (Exception e) {
                telemetry = OpenTelemetry.noop();
                logger.debug("Using no-op OpenTelemetry for service: {}", serviceName);
            }
        }
        this.openTelemetry = telemetry;

        // Initialize MetricsRegistry
        this.metricsRegistry = MetricsRegistry.initialize(
            meterRegistry,
            openTelemetry,
            serviceName,
            builder.environment != null ? builder.environment : "default",
            serviceVersion
        );

        // Initialize MetricsCollector
        this.metricsCollector = MetricsCollectorFactory.create(meterRegistry);

        // Initialize TracingManager
        this.tracingManager = new TracingManager(openTelemetry);

        logger.info("ObservabilityLauncher initialized for service: {} version: {}", 
            serviceName, serviceVersion);
    }

    /**
     * Creates a new Builder for configuring the ObservabilityLauncher.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the MetricsCollector for recording metrics.
     *
     * @return the MetricsCollector instance
     */
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    /**
     * Get the TracingManager for tracing operations.
     *
     * @return the TracingManager instance
     */
    public TracingManager getTracingManager() {
        return tracingManager;
    }

    /**
     * Get the MeterRegistry for direct Micrometer access.
     *
     * @return the MeterRegistry instance
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * Get the MetricsRegistry for advanced metrics operations.
     *
     * @return the MetricsRegistry instance
     */
    public MetricsRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    /**
     * Get the OpenTelemetry instance for tracing operations.
     *
     * @return the OpenTelemetry instance
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    /**
     * Get the service name.
     *
     * @return the service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Get the service version.
     *
     * @return the service version
     */
    public String getServiceVersion() {
        return serviceVersion;
    }

    /**
     * Shutdown the observability components and release resources.
     *
     * <p>This should be called when the application is shutting down to properly
     * flush metrics and traces.</p>
     */
    public void shutdown() {
        logger.info("Shutting down ObservabilityLauncher for service: {}", serviceName);
        
        try {
            if (openTelemetry instanceof OpenTelemetrySdk) {
                ((OpenTelemetrySdk) openTelemetry).close();
                logger.debug("Closed OpenTelemetrySdk");
            }
        } catch (Exception e) {
            logger.warn("Error closing OpenTelemetrySdk: {}", e.getMessage());
        }

        logger.info("ObservabilityLauncher shutdown complete for service: {}", serviceName);
    }

    /**
     * Builder for configuring ObservabilityLauncher.
     */
    public static final class Builder {
        private String serviceName;
        private String serviceVersion;
        private String environment;
        private boolean enableTracing = true;
        private SpanExporter spanExporter;

        private Builder() {
        }

        /**
         * Set the service name (required).
         *
         * @param serviceName the service name
         * @return this builder
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Set the service version (optional, defaults to "1.0.0").
         *
         * @param serviceVersion the service version
         * @return this builder
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Set the environment name (optional, defaults to "default").
         *
         * @param environment the environment name
         * @return this builder
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Enable tracing (optional, defaults to true).
         *
         * @param enableTracing true to enable tracing
         * @return this builder
         */
        public Builder enableTracing(boolean enableTracing) {
            this.enableTracing = enableTracing;
            return this;
        }

        /**
         * Set a custom SpanExporter for tracing (optional).
         *
         * @param spanExporter the custom span exporter
         * @return this builder
         */
        public Builder spanExporter(SpanExporter spanExporter) {
            this.spanExporter = spanExporter;
            return this;
        }

        /**
         * Build the ObservabilityLauncher instance.
         *
         * @return a new ObservabilityLauncher instance
         * @throws IllegalStateException if serviceName is null
         */
        public ObservabilityLauncher build() {
            if (serviceName == null || serviceName.isBlank()) {
                throw new IllegalStateException("serviceName is required");
            }
            return new ObservabilityLauncher(this);
        }
    }
}
