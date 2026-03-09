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
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.activej.promise.Promise;

/**
 * Unified telemetry provider interface that consolidates observability across YAPPC. Provides
 * access to OpenTelemetry tracing, metrics, and privacy-compliant usage collection.
 *
 * <p>This interface unifies multiple telemetry approaches: - OpenTelemetry distributed tracing and
 * metrics - Privacy-first usage analytics with explicit consent - Performance monitoring and
 * instrumentation - Build and CLI command telemetry
 *
 * @doc.type interface
 * @doc.purpose Unified telemetry provider interface that consolidates observability across YAPPC. Provides
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface TelemetryProvider {

    /**
     * Gets the OpenTelemetry instance for distributed tracing and metrics.
     *
     * @return OpenTelemetry instance, may be no-op if telemetry is disabled
     */
    OpenTelemetry getOpenTelemetry();

    /**
     * Gets a tracer for creating spans and distributed traces.
     *
     * @param instrumentationName the name of the instrumentation library
     * @return Tracer instance
     */
    Tracer getTracer(String instrumentationName);

    /**
     * Gets a meter for creating metrics (counters, histograms, gauges).
     *
     * @param instrumentationName the name of the instrumentation library
     * @return Meter instance
     */
    Meter getMeter(String instrumentationName);

    /**
     * Records a privacy-compliant usage event if user has opted in.
     *
     * @param event the telemetry event to record
     * @return Promise that completes when event is stored
     */
    Promise<Void> recordUsageEvent(TelemetryEvent event);

    /**
     * Enables or disables privacy-compliant usage collection.
     *
     * @param enabled true to enable usage collection, false to disable and clear data
     */
    void setUsageCollectionEnabled(boolean enabled);

    /**
     * Checks if privacy-compliant usage collection is enabled.
     *
     * @return true if user has opted into usage collection
     */
    boolean isUsageCollectionEnabled();

    /**
     * Gets the service name used for telemetry identification.
     *
     * @return service name
     */
    String getServiceName();

    /**
     * Checks if OpenTelemetry tracing and metrics are enabled.
     *
     * @return true if OpenTelemetry is enabled (not no-op)
     */
    boolean isObservabilityEnabled();

    /**
 * Shuts down the telemetry provider and flushes any pending data. */
    void shutdown();

    /**
 * Builder interface for creating configured TelemetryProvider instances. */
    interface Builder {
        Builder serviceName(String serviceName);

        Builder otlpEndpoint(String otlpEndpoint);

        Builder tracingEnabled(boolean enabled);

        Builder metricsEnabled(boolean enabled);

        Builder usageCollectionEnabled(boolean enabled);

        Builder samplingProbability(double probability);

        TelemetryProvider build();
    }
}
