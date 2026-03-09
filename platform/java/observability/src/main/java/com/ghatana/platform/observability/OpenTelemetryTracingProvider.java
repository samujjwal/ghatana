package com.ghatana.platform.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * OpenTelemetry implementation of TracingProvider for span creation and management.
 *
 * <p>OpenTelemetryTracingProvider wraps OpenTelemetry Tracer to provide span creation,
 * parent-child relationships, and context management.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Span creation with automatic start</li>
 *   <li>Parent-child span linking</li>
 *   <li>Type-safe attribute conversion (String, Boolean, Long, Double, Integer, Float)</li>
 *   <li>SpanKind support (SERVER, CLIENT, PRODUCER, CONSUMER)</li>
 *   <li>Context propagation via W3C Trace Context</li>
 * </ul>
 * @doc.type class
 * @doc.purpose OpenTelemetry-based implementation of TracingProvider for span management
 * @doc.layer core
 * @doc.pattern Implementation, Adapter
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * OpenTelemetryTracingProvider provider = new OpenTelemetryTracingProvider(openTelemetry, "ingress");
 *
 * // Server span for HTTP request
 * Span serverSpan = provider.createServerSpan("POST /events");
 * try {
 *     // Client span for database call
 *     Span clientSpan = provider.createChildSpan("query-database", serverSpan);
 *     try {
 *         // Database logic
 *     } finally {
 *         clientSpan.end();
 *     }
 * } finally {
 *     serverSpan.end();
 * }
 *
 * // With attributes
 * Span span = provider.createSpan("process-event", Map.of(
 *     "event.type", "order",
 *     "tenant.id", 123,
 *     "is.valid", true
 * ));
 * }</pre>
 *
 * <p><b>Thread-Safety:</b> Thread-safe. OpenTelemetry Tracer is thread-safe.</p>
 *
 * <p><b>Performance:</b> Attribute conversion is O(n) where n = number of attributes.
 * Span creation is lightweight (< 1µs). Always end spans in finally blocks.</p>
 *
 * @see TracingProvider for interface
 * @see TracingManager for provider creation
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Implementation (Adapter)
 * @purpose OpenTelemetry-based implementation of TracingProvider with span creation and attribute management
 * @pattern Adapter pattern (adapts OpenTelemetry Tracer to TracingProvider interface)
 * @responsibility Span creation, parent-child linking, attribute type conversion, SpanKind management
 * @usage Created by TracingManager; accessed via TracingManager.getProvider(name)
 * @examples See class-level JavaDoc usage example; always end spans in finally blocks
 * @testing Test span creation, parent-child relationships, attribute handling for all types; verify SpanKind
 * @notes Supports 6 attribute types (String, Boolean, Long, Double, Integer, Float); unknown types converted to String
 */
public class OpenTelemetryTracingProvider implements TracingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryTracingProvider.class);

    /**
     * The OpenTelemetry instance for context propagation and configuration.
     */
    private final OpenTelemetry openTelemetry;
    
    /**
     * The tracer used for creating spans.
     */
    private final Tracer tracer;
    
    /**
     * The instrumentation name (logical scope, e.g., "ingress", "validation").
     */
    private final String name;

    /**
     * Creates a new OpenTelemetryTracingProvider with the specified OpenTelemetry instance.
     *
     * <p>The instrumentation name identifies the logical scope (e.g., "ingress", "validation").
     * All spans created by this provider will be associated with this name.</p>
     *
     * @param openTelemetry the OpenTelemetry instance (must not be null)
     * @param instrumentationName the instrumentation name (e.g., "ingress", "validation")
     */
    public OpenTelemetryTracingProvider(OpenTelemetry openTelemetry, String instrumentationName) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer(instrumentationName);
        this.name = instrumentationName;
    }

    @Override
    public SpanBuilder spanBuilder(String spanName) {
        return tracer.spanBuilder(spanName);
    }

    @Override
    public Span createSpan(String spanName) {
        return spanBuilder(spanName).startSpan();
    }

    @Override
    public Span createSpan(String spanName, Map<String, Object> attributes) {
        SpanBuilder builder = spanBuilder(spanName);
        addAttributes(builder, attributes);
        return builder.startSpan();
    }

    @Override
    public Span createChildSpan(String spanName, Span parentSpan) {
        return spanBuilder(spanName)
                .setParent(Context.current().with(parentSpan))
                .startSpan();
    }

    @Override
    public Span createChildSpan(String spanName, Span parentSpan, Map<String, Object> attributes) {
        SpanBuilder builder = spanBuilder(spanName)
                .setParent(Context.current().with(parentSpan));
        addAttributes(builder, attributes);
        return builder.startSpan();
    }

    @Override
    public Span getCurrentSpan() {
        return Span.current();
    }

    @Override
    public Context getCurrentContext() {
        return Context.current();
    }

    @Override
    public Tracer getTracer() {
        return tracer;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Adds attributes to a span builder with type-safe conversion.
     *
     * <p>Supported types: String, Boolean, Long, Double, Integer, Float. Unknown types
     * are converted to String via {@code toString()}.</p>
     *
     * <p><b>Performance:</b> O(n) where n = number of attributes.</p>
     *
     * @param builder the span builder to add attributes to
     * @param attributes the attributes map (key-value pairs)
     */
    private void addAttributes(SpanBuilder builder, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                builder.setAttribute(AttributeKey.stringKey(key), (String) value);
            } else if (value instanceof Boolean) {
                builder.setAttribute(AttributeKey.booleanKey(key), (Boolean) value);
            } else if (value instanceof Long) {
                builder.setAttribute(AttributeKey.longKey(key), (Long) value);
            } else if (value instanceof Double) {
                builder.setAttribute(AttributeKey.doubleKey(key), (Double) value);
            } else if (value instanceof Integer) {
                builder.setAttribute(AttributeKey.longKey(key), ((Integer) value).longValue());
            } else if (value instanceof Float) {
                builder.setAttribute(AttributeKey.doubleKey(key), ((Float) value).doubleValue());
            } else if (value != null) {
                // Fallback: convert unknown types to String
                builder.setAttribute(AttributeKey.stringKey(key), value.toString());
            }
        }
    }

    /**
     * Creates a server span with the specified name.
     *
     * <p>Server spans represent incoming requests (HTTP, gRPC, message consumption).
     * Use this for ingress/API endpoints.</p>
     *
     * @param spanName the span name (e.g., "POST /events")
     * @return the started server span (SpanKind.SERVER)
     */
    public Span createServerSpan(String spanName) {
        return spanBuilder(spanName)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
    }

    /**
     * Creates a client span with the specified name.
     *
     * <p>Client spans represent outgoing requests (HTTP, gRPC, database queries).
     * Use this for external service calls.</p>
     *
     * @param spanName the span name (e.g., "GET /users", "SELECT users")
     * @return the started client span (SpanKind.CLIENT)
     */
    public Span createClientSpan(String spanName) {
        return spanBuilder(spanName)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
    }

    /**
     * Creates a producer span with the specified name.
     *
     * <p>Producer spans represent message publication (Kafka, RabbitMQ, SQS).
     * Use this for event/message producers.</p>
     *
     * @param spanName the span name (e.g., "publish event")
     * @return the started producer span (SpanKind.PRODUCER)
     */
    public Span createProducerSpan(String spanName) {
        return spanBuilder(spanName)
                .setSpanKind(SpanKind.PRODUCER)
                .startSpan();
    }

    /**
     * Creates a consumer span with the specified name.
     *
     * <p>Consumer spans represent message consumption (Kafka, RabbitMQ, SQS).
     * Use this for event/message consumers.</p>
     *
     * @param spanName the span name (e.g., "consume event")
     * @return the started consumer span (SpanKind.CONSUMER)
     */
    public Span createConsumerSpan(String spanName) {
        return spanBuilder(spanName)
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan();
    }
}
