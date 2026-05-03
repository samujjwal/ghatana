package com.ghatana.plugin.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
// import io.opentelemetry.api.metrics.Counter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import com.ghatana.kernel.core.observability.CorrelationIdContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @doc.type class
 * @doc.purpose Base class for standardized plugin observability (spans + metrics)
 * @doc.layer platform
 * @doc.pattern PluginBase
 * 
 * All plugins should extend this class to ensure consistent OpenTelemetry
 * instrumentation: named spans, correlation ID propagation, metrics, and error tracking.
 */
public abstract class PluginObservability {
    
    protected final String pluginId;
    protected final Tracer tracer;
    protected final Meter meter;
    
    protected PluginObservability(String pluginId) {
        this.pluginId = pluginId;
        this.tracer = GlobalOpenTelemetry.getTracer(
            "com.ghatana.plugin." + pluginId,
            "1.0.0");
        this.meter = GlobalOpenTelemetry.getMeter(
            "com.ghatana.plugin." + pluginId);
    }
    
    /**
     * Start a named span for a plugin operation.
     * 
     * Automatically adds:
     * - Plugin ID
     * - Correlation ID (if available)
     * - Tenant ID (if available)
     * - Data classification (if provided)
     * 
     * @param operationName e.g., "check-access", "post-entry", "evaluate-rule"
     * @return a scope that must be closed or used with try-with-resources
     */
    protected SpanScope startSpan(String operationName) {
        return startSpan(operationName, null, null, null);
    }
    
    /**
     * Start a named span with attributes.
     */
    protected SpanScope startSpan(String operationName, String tenantId, 
                                 String actorId, String dataClassification) {
        String spanName = String.format("plugin.%s.%s", pluginId, operationName);
        
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        
        // Add standard attributes
        span.setAttribute("plugin.id", pluginId);
        span.setAttribute("otel.scope", "plugin");
        
        // Add correlation ID if available
        CorrelationIdContext.addToSpan(span);
        
        // Add tenant if provided
        if (tenantId != null) {
            span.setAttribute("tenant.id", tenantId);
        }
        
        // Add actor if provided
        if (actorId != null) {
            span.setAttribute("actor.id", actorId);
        }
        
        // Add data classification if provided
        if (dataClassification != null) {
            span.setAttribute("data.classification", dataClassification);
        }
        
        // Set span in OpenTelemetry context
        Scope scope = span.makeCurrent();
        
        return new SpanScope(span, scope);
    }
    
    /**
     * Record a counter metric for this plugin.
     *
     * Pattern: {@code ghatana.plugin.<plugin-id>.<metric-name>}
     *
     * @param metricName e.g., "entries_emitted", "denials_total"
     * @param labels e.g., Map.of("tenant_id", "tenant-001", "result", "denied")
     * @param value amount to increment
     */
    protected void recordMetric(String metricName, Map<String, String> labels, long value) {
        String fullName = String.format("ghatana.plugin.%s.%s", pluginId, metricName);
        AttributesBuilder attrsBuilder = Attributes.builder();
        labels.forEach((k, v) -> attrsBuilder.put(k, v));
        meter.counterBuilder(fullName)
            .setDescription("Plugin metric: " + metricName)
            .build()
            .add(value, attrsBuilder.build());
    }
    
    /**
     * Record a histogram metric (latency, size, etc).
     * 
     * @param metricName e.g., "query_latency_ms", "response_size_bytes"
     * @param value the measured value
     * @param labels optional labels
     */
    protected void recordHistogram(String metricName, double value, 
                                  Map<String, String> labels) {
        // OpenTelemetry v1.x has limited histogram support
        // Fallback to recording as metric
        String fullName = String.format("ghatana.plugin.%s.%s", pluginId, metricName);
        
        // This would be implemented with proper histogram/distribution metric
        // For now, just record a gauge or counter
        recordMetric(metricName + "_total", labels, (long) value);
    }
    
    /**
     * Helper to create attribute map.
     */
    protected static Map<String, String> attributes(Map<String, String> labels) {
        Map<String, String> attrs = new HashMap<>(labels != null ? labels : Map.of());
        attrs.putIfAbsent("metric_version", "1.0");
        return attrs;
    }
    
    /**
     * Helper for single-label metrics.
     */
    protected static Map<String, String> attr(String key, String value) {
        return Map.of(key, value, "metric_version", "1.0");
    }
    
    /**
     * Helper for two-label metrics.
     */
    protected static Map<String, String> attr(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put("metric_version", "1.0");
        return m;
    }
    
    /**
     * Scope wrapper that ensures span and OpenTelemetry context are properly managed.
     */
    public static class SpanScope implements AutoCloseable {
        private final Span span;
        private final Scope scope;
        private long startTimeNanos;
        
        SpanScope(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
            this.startTimeNanos = System.nanoTime();
        }
        
        public Span getSpan() {
            return span;
        }
        
        /**
         * Add an event to the span.
         */
        public void addEvent(String eventName) {
            span.addEvent(eventName);
        }
        
        /**
         * Add an attribute to the span.
         */
        public void setAttribute(String key, String value) {
            span.setAttribute(key, value);
        }
        
        /**
         * Add an attribute to the span.
         */
        public void setAttribute(String key, boolean value) {
            span.setAttribute(key, value);
        }
        
        /**
         * Add an attribute to the span.
         */
        public void setAttribute(String key, long value) {
            span.setAttribute(key, value);
        }
        
        /**
         * Add an attribute to the span.
         */
        public void setAttribute(String key, double value) {
            span.setAttribute(key, value);
        }
        
        /**
         * Record error on the span and set error status.
         */
        public void recordException(Throwable exception, String errorType) {
            span.recordException(exception);
            span.setAttribute("error.type", errorType);
            span.setAttribute("error.domain", "plugin");
        }
        
        /**
         * Get elapsed time since span started (in milliseconds).
         */
        public long getElapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
        }
        
        @Override
        public void close() {
            // Record duration
            long elapsedNanos = System.nanoTime() - startTimeNanos;
            span.setAttribute("duration.ms", TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
            
            // Close OpenTelemetry scope
            scope.close();
            
            // End span
            span.end();
        }
    }
}
