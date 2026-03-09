package com.ghatana.platform.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.Map;

/**
 * Interface for creating and managing OpenTelemetry spans.
 *
 * <p>TracingProvider abstracts span creation, parent-child relationships, and context
 * management for distributed tracing across services.</p>
 *
 * <p><b>Architecture:</b></p>
 * <ul>
 *   <li><b>Scope:</b> Each provider represents a logical scope (e.g., "ingress", "validation")</li>
 *   <li><b>Span Hierarchy:</b> Supports parent-child span relationships for nested operations</li>
 *   <li><b>Context Propagation:</b> W3C Trace Context for cross-service correlation</li>
 *   <li><b>Attributes:</b> Key-value attributes for span enrichment</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * TracingProvider provider = tracingManager.getProvider("ingress");
 *
 * // Create root span
 * Span rootSpan = provider.createSpan("process-request");
 * try {
 * @doc.type interface
 * @doc.purpose SPI for creating and managing OpenTelemetry spans with context propagation
 * @doc.layer core
 * @doc.pattern Port, Service Provider Interface
 *     // Business logic
 *     
 *     // Create child span
 *     Span childSpan = provider.createChildSpan("validate-request", rootSpan);
 *     try {
 *         // Validation logic
 *     } finally {
 *         childSpan.end();
 *     }
 * } finally {
 *     rootSpan.end();
 * }
 *
 * // With attributes
 * Span span = provider.createSpan("process-event", Map.of(
 *     "event.type", "order",
 *     "tenant.id", "tenant-123"
 * ));
 * }</pre>
 *
 * <p><b>Thread-Safety:</b> Thread-safe. Tracer instances are thread-safe per OpenTelemetry spec.</p>
 *
 * <p><b>Performance:</b> Span creation is lightweight (< 1µs). Always call {@code span.end()}
 * in {@code finally} blocks to ensure spans are exported.</p>
 *
 * @see TracingManager for provider lifecycle
 * @see OpenTelemetryTracingProvider for implementation
 * @see io.opentelemetry.api.trace.Span for span API
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Service Interface
 * @purpose Abstraction for creating and managing OpenTelemetry spans with parent-child relationships
 * @pattern Strategy pattern (interchangeable providers), Provider pattern (span factory)
 * @responsibility Span creation, parent-child linking, context access, attribute management
 * @usage Implemented by OpenTelemetryTracingProvider; obtained via TracingManager.getProvider(name)
 * @examples See class-level JavaDoc usage example; always call span.end() in finally blocks
 * @testing Use TracingManager.createForTesting() with InMemorySpanExporter for span assertions
 * @notes Always end spans in finally blocks; use child spans for nested operations; enrich with attributes
 */
public interface TracingProvider {

    /**
     * Creates a span builder with the specified name.
     *
     * <p>Use this for advanced span configuration (kind, links, start timestamp).
     * Most use cases should use {@link #createSpan} instead.</p>
     *
     * @param spanName the span name (e.g., "process-request", "validate-event")
     * @return the span builder for configuration
     */
    SpanBuilder spanBuilder(String spanName);

    /**
     * Creates a span with the specified name.
     *
     * <p>The span is started immediately. Caller MUST call {@code span.end()} when done,
     * preferably in a {@code finally} block.</p>
     *
     * @param spanName the span name (e.g., "process-request")
     * @return the started span (never null)
     */
    Span createSpan(String spanName);

    /**
     * Creates a span with the specified name and attributes.
     *
     * <p>Attributes enrich spans with contextual information (tenant ID, event type, etc.).
     * The span is started immediately.</p>
     *
     * @param spanName the span name (e.g., "process-event")
     * @param attributes the span attributes (key-value pairs, e.g., {"tenant.id": "123"})
     * @return the started span with attributes (never null)
     */
    Span createSpan(String spanName, Map<String, Object> attributes);

    /**
     * Creates a child span with the specified name.
     *
     * <p>The child span's parent is set to {@code parentSpan}. This creates a span hierarchy
     * for nested operations (e.g., request → validation → database query).</p>
     *
     * @param spanName the span name (e.g., "validate-request")
     * @param parentSpan the parent span (must not be null)
     * @return the started child span (never null)
     */
    Span createChildSpan(String spanName, Span parentSpan);

    /**
     * Creates a child span with the specified name and attributes.
     *
     * <p>Combines parent-child relationship with attribute enrichment.</p>
     *
     * @param spanName the span name (e.g., "validate-event")
     * @param parentSpan the parent span (must not be null)
     * @param attributes the span attributes (key-value pairs)
     * @return the started child span with attributes (never null)
     */
    Span createChildSpan(String spanName, Span parentSpan, Map<String, Object> attributes);

    /**
     * Gets the current span from the current context.
     *
     * <p>Returns the span in the active context (if any). This is useful for implicit
     * parent-child relationships when the parent span is in scope.</p>
     *
     * @return the current span, or {@code Span.getInvalid()} if no active span
     */
    Span getCurrentSpan();

    /**
     * Gets the current OpenTelemetry context.
     *
     * <p>The context holds span baggage, trace state, and other propagated values.
     * Use this for manual context propagation across threads.</p>
     *
     * @return the current context (never null)
     */
    Context getCurrentContext();

    /**
     * Gets the underlying OpenTelemetry tracer.
     *
     * <p>Exposed for advanced use cases. Most callers should use {@link #createSpan}
     * instead of direct tracer access.</p>
     *
     * @return the tracer (never null)
     */
    Tracer getTracer();

    /**
     * Gets the name of the tracing provider.
     *
     * <p>This is the logical scope name passed to {@link TracingManager#getProvider(String)}.</p>
     *
     * @return the provider name (e.g., "ingress", "validation", "routing")
     */
    String getName();
}
