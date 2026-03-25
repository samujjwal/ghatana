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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Instrumentation utilities for common telemetry patterns. Provides convenient methods for
 * instrumenting CLI commands, build operations, and other activities.
 *
 * @doc.type class
 * @doc.purpose Instrumentation utilities for common telemetry patterns. Provides convenient methods for
 * @doc.layer platform
 * @doc.pattern Component
 */
public class TelemetryInstrumentation {

    private final TelemetryProvider telemetryProvider;
    private final Tracer tracer;
    private final Meter meter;
    private final LongCounter commandCounter;
    private final LongHistogram commandDuration;
    private final LongCounter errorCounter;

    public TelemetryInstrumentation(
            TelemetryProvider telemetryProvider, String instrumentationName) {
        this.telemetryProvider = telemetryProvider;
        this.tracer = telemetryProvider.getTracer(instrumentationName);
        this.meter = telemetryProvider.getMeter(instrumentationName);

        // Pre-create common metrics
        this.commandCounter =
                meter.counterBuilder("yappc.command.count")
                        .setDescription("Number of commands executed")
                        .setUnit("1")
                        .build();

        this.commandDuration =
                meter.histogramBuilder("yappc.command.duration")
                        .setDescription("Command execution duration")
                        .setUnit("ms")
                        .ofLongs()
                        .build();

        this.errorCounter =
                meter.counterBuilder("yappc.error.count")
                        .setDescription("Number of errors encountered")
                        .setUnit("1")
                        .build();
    }

    /**
 * Instruments a CLI command execution with tracing, metrics, and usage analytics. */
    public <T> T instrumentCommand(String commandName, Callable<T> operation) throws Exception {
        return instrumentCommand(commandName, null, operation);
    }

    /**
 * Instruments a CLI command execution with additional context. */
    public <T> T instrumentCommand(
            String commandName, Attributes additionalAttributes, Callable<T> operation)
            throws Exception {

        Instant startTime = Instant.now();
        Attributes baseAttributes =
                Attributes.of(
                        AttributeKey.stringKey("command.name"),
                        commandName,
                        AttributeKey.stringKey("service.name"),
                        telemetryProvider.getServiceName());

        Attributes attributes =
                additionalAttributes != null
                        ? baseAttributes.toBuilder().putAll(additionalAttributes).build()
                        : baseAttributes;

        Span span = tracer.spanBuilder("command.execute").setAllAttributes(attributes).startSpan();

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("command.started");

            T result = operation.call();

            // Record success metrics
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            commandCounter.add(1, attributes);
            commandDuration.record(durationMs, attributes);

            // Record usage event
            recordUsageEvent(commandName, true, durationMs, attributes);

            span.setStatus(StatusCode.OK);
            span.addEvent("command.completed");

            return result;

        } catch (Exception e) {
            // Record error metrics
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            Attributes errorAttributes =
                    attributes.toBuilder()
                            .put(AttributeKey.stringKey("error.type"), e.getClass().getSimpleName())
                            .put(AttributeKey.stringKey("error.message"), e.getMessage())
                            .build();

            errorCounter.add(1, errorAttributes);
            commandDuration.record(durationMs, errorAttributes);

            // Record usage event
            recordUsageEvent(commandName, false, durationMs, errorAttributes);

            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);

            throw e;

        } finally {
            span.end();
        }
    }

    /**
 * Instruments a build operation (generation, compilation, etc.). */
    public <T> T instrumentBuildOperation(
            String operationType, String language, String framework, Supplier<T> operation) {
        return instrumentBuildOperation(operationType, language, framework, null, operation);
    }

    /**
 * Instruments a build operation with additional context. */
    public <T> T instrumentBuildOperation(
            String operationType,
            String language,
            String framework,
            Attributes additionalAttributes,
            Supplier<T> operation) {

        Instant startTime = Instant.now();
        Attributes baseAttributes =
                Attributes.of(
                        AttributeKey.stringKey("build.operation"), operationType,
                        AttributeKey.stringKey("build.language"), language,
                        AttributeKey.stringKey("build.framework"),
                                framework != null ? framework : "none");

        Attributes attributes =
                additionalAttributes != null
                        ? baseAttributes.toBuilder().putAll(additionalAttributes).build()
                        : baseAttributes;

        Span span = tracer.spanBuilder("build.operation").setAllAttributes(attributes).startSpan();

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("build.started");

            T result = operation.get();

            // Record success metrics
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            LongCounter buildCounter =
                    meter.counterBuilder("yappc.build.count")
                            .setDescription("Number of build operations")
                            .setUnit("1")
                            .build();

            LongHistogram buildDuration =
                    meter.histogramBuilder("yappc.build.duration")
                            .setDescription("Build operation duration")
                            .setUnit("ms")
                            .ofLongs()
                            .build();

            buildCounter.add(1, attributes);
            buildDuration.record(durationMs, attributes);

            // Record usage event for build operations
            TelemetryEvent event =
                    TelemetryEvent.builder()
                            .eventType("build.operation")
                            .command(operationType)
                            .success(true)
                            .durationMs(durationMs)
                            .timestamp(startTime)
                            .language(language)
                            .framework(framework)
                            .build();

            telemetryProvider.recordUsageEvent(event);

            span.setStatus(StatusCode.OK);
            span.addEvent("build.completed");

            return result;

        } catch (Exception e) {
            // Record error metrics
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            Attributes errorAttributes =
                    attributes.toBuilder()
                            .put(AttributeKey.stringKey("error.type"), e.getClass().getSimpleName())
                            .build();

            errorCounter.add(1, errorAttributes);

            // Record usage event for build failure
            TelemetryEvent event =
                    TelemetryEvent.builder()
                            .eventType("build.operation")
                            .command(operationType)
                            .success(false)
                            .durationMs(durationMs)
                            .timestamp(startTime)
                            .language(language)
                            .framework(framework)
                            .errorType(e.getClass().getSimpleName())
                            .build();

            telemetryProvider.recordUsageEvent(event);

            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);

            throw new RuntimeException(e);

        } finally {
            span.end();
        }
    }

    /**
 * Instruments a simple operation with span tracking. */
    public <T> T instrumentOperation(String operationName, Function<Span, T> operation) {
        Span span = tracer.spanBuilder(operationName).startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = operation.apply(span);
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
 * Records a custom metric value. */
    public void recordMetric(String metricName, long value, Attributes attributes) {
        LongCounter counter =
                meter.counterBuilder(metricName)
                        .setDescription("Custom metric: " + metricName)
                        .setUnit("1")
                        .build();

        counter.add(value, attributes);
    }

    /**
 * Records a duration metric. */
    public void recordDuration(String metricName, long durationMs, Attributes attributes) {
        LongHistogram histogram =
                meter.histogramBuilder(metricName)
                        .setDescription("Duration metric: " + metricName)
                        .setUnit("ms")
                        .ofLongs()
                        .build();

        histogram.record(durationMs, attributes);
    }

    /**
 * Records a usage event with basic command information. */
    private void recordUsageEvent(
            String commandName, boolean success, long durationMs, Attributes attributes) {

        String errorType = null;
        if (!success) {
            errorType = attributes.get(AttributeKey.stringKey("error.type"));
        }

        TelemetryEvent event =
                TelemetryEvent.builder()
                        .eventType("command.execution")
                        .command(commandName)
                        .success(success)
                        .durationMs(durationMs)
                        .timestamp(Instant.now())
                        .errorType(errorType)
                        .build();

        telemetryProvider.recordUsageEvent(event);
    }
}
