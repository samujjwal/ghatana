/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.yappc.core.telemetry;

import com.ghatana.yappc.core.telemetry.model.TelemetryEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.io.IOException;
import io.activej.promise.Promise;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified telemetry provider implementation that consolidates all observability
 * approaches. Integrates OpenTelemetry (tracing/metrics) with privacy-first
 * usage collection.
 *
 * @doc.type class
 * @doc.purpose Unified telemetry provider implementation that consolidates all
 * observability approaches.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class UnifiedTelemetryProvider implements TelemetryProvider {

    private static final Logger log = LoggerFactory.getLogger(UnifiedTelemetryProvider.class);

    private final String serviceName;
    private final OpenTelemetry openTelemetry;
    private final LocalTelemetryCollector usageCollector;  // Changed to concrete type
    private final boolean observabilityEnabled;
    private final SdkTracerProvider tracerProvider;

    private UnifiedTelemetryProvider(Builder builder) {
        this.serviceName = builder.serviceName;
        this.usageCollector = new LocalTelemetryCollector();

        // Initialize usage collection state
        try {
            usageCollector.getConfiguration(); // Initialize collector
            if (builder.usageCollectionEnabled && !usageCollector.isOptedIn()) {
                usageCollector.optIn();
            } else if (!builder.usageCollectionEnabled && usageCollector.isOptedIn()) {
                usageCollector.optOut();
            }
        } catch (IOException e) {
            log.error("Warning: Could not configure usage collection: {}", e.getMessage());
        }

        // Initialize OpenTelemetry
        if (builder.tracingEnabled || builder.metricsEnabled) {
            OpenTelemetryResult result = initializeOpenTelemetry(builder);
            this.openTelemetry = result.openTelemetry;
            this.tracerProvider = result.tracerProvider;
            this.observabilityEnabled = true;
        } else {
            this.openTelemetry = OpenTelemetry.noop();
            this.tracerProvider = null;
            this.observabilityEnabled = false;
        }
    }

    @Override
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    @Override
    public Tracer getTracer(String instrumentationName) {
        return openTelemetry.getTracer(instrumentationName, "1.0.0");
    }

    @Override
    public Meter getMeter(String instrumentationName) {
        return openTelemetry
                .getMeterProvider()
                .meterBuilder(instrumentationName)
                .setInstrumentationVersion("1.0.0")
                .build();
    }

    @Override
    public Promise<Void> recordUsageEvent(TelemetryEvent event) {
        return usageCollector.recordEvent(event);
    }

    @Override
    public void setUsageCollectionEnabled(boolean enabled) {
        try {
            if (enabled) {
                usageCollector.optIn();
            } else {
                usageCollector.optOut();
            }
        } catch (IOException e) {
            log.error("Error updating usage collection settings: {}", e.getMessage());
        }
    }

    @Override
    public boolean isUsageCollectionEnabled() {
        return usageCollector.isOptedIn();
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public boolean isObservabilityEnabled() {
        return observabilityEnabled;
    }

    @Override
    public void shutdown() {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        usageCollector.shutdown();
    }

    /**
     * Creates a builder for configuring telemetry provider.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a simple telemetry provider with usage collection only.
     */
    public static TelemetryProvider createSimple(String serviceName) {
        return builder()
                .serviceName(serviceName)
                .tracingEnabled(false)
                .metricsEnabled(false)
                .usageCollectionEnabled(true)
                .build();
    }

    /**
     * Creates a full telemetry provider with observability and usage
     * collection.
     */
    public static TelemetryProvider createDefault(String serviceName, String otlpEndpoint) {
        return builder()
                .serviceName(serviceName)
                .otlpEndpoint(otlpEndpoint)
                .tracingEnabled(true)
                .metricsEnabled(true)
                .usageCollectionEnabled(true)
                .samplingProbability(1.0)
                .build();
    }

    /**
     * Creates a no-op telemetry provider that disables all telemetry.
     */
    public static TelemetryProvider createNoop(String serviceName) {
        return builder()
                .serviceName(serviceName)
                .tracingEnabled(false)
                .metricsEnabled(false)
                .usageCollectionEnabled(false)
                .build();
    }

    private static OpenTelemetryResult initializeOpenTelemetry(Builder builder) {
        try {
            // Create resource with service information
            Resource resource
                    = Resource.getDefault()
                            .merge(
                                    Resource.create(
                                            Attributes.of(
                                                    AttributeKey.stringKey("service.name"),
                                                    builder.serviceName,
                                                    AttributeKey.stringKey("service.version"),
                                                    "1.0.0",
                                                    AttributeKey.stringKey("telemetry.sdk.name"),
                                                    "opentelemetry",
                                                    AttributeKey.stringKey(
                                                            "telemetry.sdk.language"),
                                                    "java")));

            final SdkTracerProvider tracerProvider;

            if (builder.tracingEnabled && builder.otlpEndpoint != null) {
                // Configure OTLP span exporter
                OtlpGrpcSpanExporter spanExporter
                        = OtlpGrpcSpanExporter.builder()
                                .setEndpoint(builder.otlpEndpoint)
                                .setTimeout(30, TimeUnit.SECONDS)
                                .build();

                // Create tracer provider
                tracerProvider
                        = SdkTracerProvider.builder()
                                .addSpanProcessor(
                                        BatchSpanProcessor.builder(spanExporter)
                                                .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                                                .setMaxQueueSize(2048)
                                                .setMaxExportBatchSize(512)
                                                .setExporterTimeout(30, TimeUnit.SECONDS)
                                                .build())
                                .setSampler(Sampler.traceIdRatioBased(builder.samplingProbability))
                                .setResource(resource)
                                .build();
            } else {
                // Create minimal tracer provider
                tracerProvider = SdkTracerProvider.builder().setResource(resource).build();
            }

            // Build OpenTelemetry SDK (avoid global registration in tests)
            OpenTelemetry openTelemetry
                    = OpenTelemetrySdk.builder()
                            .setTracerProvider(tracerProvider)
                            .setPropagators(
                                    ContextPropagators.create(
                                            W3CTraceContextPropagator.getInstance()))
                            .build();

            // Add shutdown hook
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        tracerProvider.close();
                                    }));

            return new OpenTelemetryResult(openTelemetry, tracerProvider);

        } catch (Exception e) {
            log.error("Failed to initialize OpenTelemetry: {}", e.getMessage());
            return new OpenTelemetryResult(OpenTelemetry.noop(), null);
        }
    }

    private record OpenTelemetryResult(
            OpenTelemetry openTelemetry, SdkTracerProvider tracerProvider) {
    }

    /**
     * Builder for configuring UnifiedTelemetryProvider instances.
     */
    public static class Builder implements TelemetryProvider.Builder {

        private String serviceName = "yappc";
        private String otlpEndpoint;
        private boolean tracingEnabled = false;
        private boolean metricsEnabled = false;
        private boolean usageCollectionEnabled = true;
        private double samplingProbability = 1.0;

        @Override
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        @Override
        public Builder otlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint;
            return this;
        }

        @Override
        public Builder tracingEnabled(boolean enabled) {
            this.tracingEnabled = enabled;
            return this;
        }

        @Override
        public Builder metricsEnabled(boolean enabled) {
            this.metricsEnabled = enabled;
            return this;
        }

        @Override
        public Builder usageCollectionEnabled(boolean enabled) {
            this.usageCollectionEnabled = enabled;
            return this;
        }

        @Override
        public Builder samplingProbability(double probability) {
            this.samplingProbability = Math.max(0.0, Math.min(1.0, probability));
            return this;
        }

        @Override
        public TelemetryProvider build() {
            return new UnifiedTelemetryProvider(this);
        }
    }
}
