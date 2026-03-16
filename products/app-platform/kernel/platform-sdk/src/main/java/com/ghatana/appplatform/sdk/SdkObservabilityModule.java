package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @doc.type    Service
 * @doc.purpose SDK module for K-06 observability integration.
 *              Provides auto-instrumentation of HTTP and event handlers (OpenTelemetry spans),
 *              a custom metrics API (counters, histograms, gauges), a structured logging helper
 *              with correlation_id propagation, and plugin sandbox metrics auto-emission.
 *              Used by all services and plugins at instrumentation point startup.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class SdkObservabilityModule {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface TracingPort {
        /** Start a new trace span. Returns a span context handle. */
        SpanContext startSpan(String operationName, Map<String, String> attributes);

        /** Finish the span identified by context. */
        void finishSpan(SpanContext ctx, boolean success, String errorMessage);

        record SpanContext(String traceId, String spanId, long startNanos) {}
    }

    public interface StructuredLogPort {
        /** Emit a structured log entry with correlation_id propagation. */
        void log(String level, String message, String correlationId,
                 String service, Map<String, String> fields);
    }

    public interface K06MetricsPushPort {
        /** Push a custom metric snapshot to the K-06 Prometheus remote-write endpoint. */
        Promise<Void> push(String metricName, double value, Map<String, String> labels);
    }

    // -----------------------------------------------------------------------
    // Custom Metrics API (fluent builder)
    // -----------------------------------------------------------------------

    /**
     * Fluent API for creating custom metrics via the K-06 SDK.
     * Usage: {@code sdk.metrics().counter("orders.placed").tag("side", "BUY").increment()}
     */
    public class MetricsBuilder {
        private final String metricName;
        private final Map<String, String> tags = new ConcurrentHashMap<>();

        MetricsBuilder(String metricName) { this.metricName = metricName; }

        public MetricsBuilder tag(String key, String value) { tags.put(key, value); return this; }

        /** Increment a counter by 1. */
        public void increment() {
            Counter.builder(metricName).tags(flattenTags()).register(meterRegistry).increment();
            customCounterTotal.increment();
        }

        /** Record a value on a histogram/timer. */
        public void record(double value) {
            meterRegistry.summary(metricName, flattenTags()).record(value);
            customHistogramTotal.increment();
        }

        /** Set a gauge value (backed by an AtomicReference<Double>). */
        public void gauge(double value) {
            AtomicReference<Double> ref = gaugeRefs.computeIfAbsent(metricName, k -> {
                AtomicReference<Double> r = new AtomicReference<>(0.0);
                Gauge.builder(metricName, r, AtomicReference::get)
                     .tags(flattenTags())
                     .register(meterRegistry);
                return r;
            });
            ref.set(value);
        }

        private String[] flattenTags() {
            return tags.entrySet().stream()
                       .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                       .toArray(String[]::new);
        }
    }

    // -----------------------------------------------------------------------
    // Tracer facade
    // -----------------------------------------------------------------------

    /**
     * Lightweight tracer that wraps the underlying {@link TracingPort}.
     * Usage:
     * <pre>
     *   var span = sdk.tracer().start("order.validate");
     *   try { ... span.finish(true, null); }
     *   catch (Exception e) { span.finish(false, e.getMessage()); throw e; }
     * </pre>
     */
    public class SpanBuilder {
        private final String operationName;
        private final Map<String, String> attributes = new ConcurrentHashMap<>();
        private TracingPort.SpanContext ctx;

        SpanBuilder(String operationName) { this.operationName = operationName; }

        public SpanBuilder attr(String key, String value) { attributes.put(key, value); return this; }

        public SpanBuilder start() {
            ctx = tracingPort.startSpan(operationName, Map.copyOf(attributes));
            activeSpanCount.incrementAndGet();
            return this;
        }

        public void finish(boolean success, String errorMessage) {
            if (ctx != null) {
                long durationMs = (System.nanoTime() - ctx.startNanos()) / 1_000_000;
                tracingPort.finishSpan(ctx, success, errorMessage);
                activeSpanCount.decrementAndGet();
                spanDurationMs.record(durationMs);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Structured Logger facade
    // -----------------------------------------------------------------------

    public class Logger {
        private final String service;
        private String correlationId;

        Logger(String service) { this.service = service; }

        public Logger withCorrelation(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public void info(String message, Map<String, String> fields) {
            structuredLogPort.log("INFO", message, correlationId, service, fields);
        }

        public void warn(String message, Map<String, String> fields) {
            structuredLogPort.log("WARN", message, correlationId, service, fields);
        }

        public void error(String message, Map<String, String> fields) {
            structuredLogPort.log("ERROR", message, correlationId, service, fields);
        }
    }

    // -----------------------------------------------------------------------
    // Plugin Sandbox Metrics auto-emission
    // -----------------------------------------------------------------------

    /**
     * Auto-emit sandbox utilisation metrics for a plugin execution.
     * Called automatically by the plugin runtime after each plugin execution.
     */
    public Promise<Void> emitPluginSandboxMetrics(String pluginId, String tier,
                                                   long executionMs, long memoryUsedMb,
                                                   boolean syscallViolation) {
        pluginExecutionTimer.record(executionMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (syscallViolation) pluginSyscallViolationTotal.increment();
        Map<String, String> labels = Map.of("plugin_id", pluginId, "tier", tier);
        return k06PushPort.push("sdk.plugin.execution_ms", executionMs, labels)
            .then(ignored -> k06PushPort.push("sdk.plugin.memory_used_mb", memoryUsedMb, labels));
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final MeterRegistry meterRegistry;
    private final TracingPort tracingPort;
    private final StructuredLogPort structuredLogPort;
    private final K06MetricsPushPort k06PushPort;

    private final Counter customCounterTotal;
    private final Counter customHistogramTotal;
    private final Timer spanDurationMs;
    private final Counter pluginSyscallViolationTotal;
    private final Timer pluginExecutionTimer;
    private final AtomicLong activeSpanCount = new AtomicLong(0);

    private final Map<String, AtomicReference<Double>> gaugeRefs = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public SdkObservabilityModule(MeterRegistry meterRegistry,
                                   TracingPort tracingPort,
                                   StructuredLogPort structuredLogPort,
                                   K06MetricsPushPort k06PushPort) {
        this.meterRegistry      = meterRegistry;
        this.tracingPort        = tracingPort;
        this.structuredLogPort  = structuredLogPort;
        this.k06PushPort        = k06PushPort;

        this.customCounterTotal    = Counter.builder("sdk.observability.custom_counter_ops_total")
                .description("Total custom counter increment operations via SDK metrics API")
                .register(meterRegistry);
        this.customHistogramTotal  = Counter.builder("sdk.observability.custom_histogram_ops_total")
                .description("Total custom histogram record operations via SDK metrics API")
                .register(meterRegistry);
        this.spanDurationMs        = Timer.builder("sdk.observability.span_duration_ms")
                .description("Duration of traced spans in milliseconds")
                .register(meterRegistry);
        this.pluginSyscallViolationTotal = Counter.builder("sdk.plugin.syscall_violation_total")
                .description("Total sandbox syscall violations from plugin execution")
                .register(meterRegistry);
        this.pluginExecutionTimer  = Timer.builder("sdk.plugin.execution_timer")
                .description("Plugin execution duration timer")
                .register(meterRegistry);

        Gauge.builder("sdk.observability.active_spans", activeSpanCount, AtomicLong::get)
             .description("Currently active traced spans")
             .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public factory methods for fluent API
    // -----------------------------------------------------------------------

    /** Entry point for custom metrics API: {@code sdk.metrics("orders.placed").tag(...).increment()} */
    public MetricsBuilder metrics(String metricName) { return new MetricsBuilder(metricName); }

    /** Entry point for distributed tracing: {@code sdk.tracer("operation").attr(...).start()} */
    public SpanBuilder tracer(String operationName) { return new SpanBuilder(operationName); }

    /** Entry point for structured logging: {@code sdk.logger("my-service").withCorrelation(id).info(...)} */
    public Logger logger(String service) { return new Logger(service); }

    /**
     * Auto-instrument an HTTP handler with a trace span and structured log.
     * The handler is called with the span already started.
     */
    public <T> Promise<T> instrumentHttp(String operationName, String correlationId,
                                          java.util.function.Supplier<Promise<T>> handler) {
        SpanBuilder span = tracer(operationName)
            .attr("http.route", operationName)
            .attr("correlation_id", correlationId)
            .start();
        return handler.get()
            .whenComplete((result, err) -> span.finish(err == null, err != null ? err.getMessage() : null));
    }

    /**
     * Auto-instrument an event handler with a trace span.
     */
    public <T> Promise<T> instrumentEvent(String eventType, String correlationId,
                                           java.util.function.Supplier<Promise<T>> handler) {
        SpanBuilder span = tracer("event." + eventType)
            .attr("event.type", eventType)
            .attr("correlation_id", correlationId)
            .start();
        return handler.get()
            .whenComplete((result, err) -> span.finish(err == null, err != null ? err.getMessage() : null));
    }
}
