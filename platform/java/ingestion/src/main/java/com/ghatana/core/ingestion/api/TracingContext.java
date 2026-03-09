package com.ghatana.core.ingestion.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable distributed tracing context following W3C Trace Context standard for request correlation.
 *
 * <p><b>Purpose</b><br>
 * Provides W3C-compliant distributed tracing propagation for correlating requests across
 * microservices, ingestion pipeline stages, and EventCloud operations. Enables end-to-end
 * tracing from API gateway through validation, authorization, enrichment, and persistence.
 *
 * <p><b>Architecture Role</b><br>
 * Core tracing value object used throughout ingestion pipeline for observability and debugging.
 * Part of {@code core/ingestion/api} for platform-wide tracing context. Integrates with
 * OpenTelemetry, Jaeger, Zipkin, and W3C Trace Context HTTP headers.
 *
 * <p><b>Tracing Context Features</b><br>
 * <ul>
 *   <li>W3C Trace Context: Follows W3C standard (traceparent header format)</li>
 *   <li>Trace ID: Unique identifier for entire distributed trace (128-bit hex or UUID)</li>
 *   <li>Span ID: Unique identifier for current operation span (64-bit hex or UUID)</li>
 *   <li>Parent Span ID: Optional parent span for hierarchical tracing</li>
 *   <li>Immutability: Java record (thread-safe value object)</li>
 * @doc.type record
 * @doc.purpose W3C-compliant distributed tracing context for request correlation across services
 * @doc.layer core
 * @doc.pattern Value Object, Tracing Context
 *
 *   <li>Child span creation: {@code childSpan} factory method for nested operations</li>
 * </ul>
 *
 * <p><b>Usage Examples</b><br>
 *
 * <p><b>Example 1: Root Trace Creation (New Request)</b>
 * <pre>{@code
 * // Create new root trace (no parent span)
 * String traceId = UUID.randomUUID().toString();
 * String spanId = UUID.randomUUID().toString();
 * TracingContext rootTrace = TracingContext.newTrace(traceId, spanId);
 *
 * // Use in CallContext
 * CallContext ctx = new CallContext(tenantId, principal, rootTrace);
 * }</pre>
 *
 * <p><b>Example 2: Child Span Creation (Nested Operation)</b>
 * <pre>{@code
 * // Parent span: HTTP request handling
 * TracingContext parentTrace = TracingContext.newTrace(
 *     "trace-abc-123",
 *     "span-parent-456"
 * );
 *
 * // Create child span for validation stage
 * TracingContext validationTrace = parentTrace.childSpan("span-validation-789");
 *
 * // Child span has same traceId, new spanId, parent reference
 * assert validationTrace.traceId().equals("trace-abc-123");
 * assert validationTrace.spanId().equals("span-validation-789");
 * assert validationTrace.parentSpanId().equals(Optional.of("span-parent-456"));
 * }</pre>
 *
 * <p><b>Example 3: W3C Trace Context HTTP Header Parsing</b>
 * <pre>{@code
 * // Extract from HTTP traceparent header:
 * // "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
 * // Format: version-traceId-parentId-flags
 *
 * String traceparent = request.getHeader("traceparent");
 * String[] parts = traceparent.split("-");
 *
 * String traceId = parts[1];        // "4bf92f3577b34da6a3ce929d0e0e4736"
 * String parentSpanId = parts[2];   // "00f067aa0ba902b7"
 *
 * // Generate new span ID for this service
 * String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
 *
 * // Create tracing context with parent reference
 * TracingContext tracing = new TracingContext(
 *     traceId,
 *     spanId,
 *     Optional.of(parentSpanId)
 * );
 * }</pre>
 *
 * <p><b>Example 4: Multi-Stage Pipeline Tracing</b>
 * <pre>{@code
 * // Stage 1: HTTP ingestion (root span)
 * TracingContext httpSpan = TracingContext.newTrace("trace-123", "span-http-001");
 *
 * // Stage 2: Schema validation (child span)
 * TracingContext validationSpan = httpSpan.childSpan("span-validation-002");
 * schemaValidator.validate(event, validationSpan);
 *
 * // Stage 3: Authorization (child span)
 * TracingContext authzSpan = httpSpan.childSpan("span-authz-003");
 * authzService.authorize(principal, event, authzSpan);
 *
 * // Stage 4: EventCloud append (child span)
 * TracingContext appendSpan = httpSpan.childSpan("span-append-004");
 * eventCloud.append(event, appendSpan);
 *
 * // All spans share same traceId for end-to-end correlation
 * }</pre>
 *
 * <p><b>Example 5: OpenTelemetry Integration</b>
 * <pre>{@code
 * import io.opentelemetry.api.trace.*;
 *
 * // Convert TracingContext to OpenTelemetry Span
 * Tracer tracer = openTelemetry.getTracer("ingestion-service");
 *
 * SpanContext parentContext = SpanContext.createFromRemoteParent(
 *     tracing.traceId(),
 *     tracing.spanId(),
 *     TraceFlags.getSampled(),
 *     TraceState.getDefault()
 * );
 *
 * Span span = tracer.spanBuilder("ingest-event")
 *     .setParent(Context.current().with(Span.wrap(parentContext)))
 *     .startSpan();
 *
 * // Use span for tracing
 * try (Scope scope = span.makeCurrent()) {
 *     ingestionService.ingestOne(event, ctx);
 * } finally {
 *     span.end();
 * }
 * }</pre>
 *
 * <p><b>Example 6: Logging with Trace Correlation</b>
 * <pre>{@code
 * // Add tracing to log MDC for correlation
 * MDC.put("traceId", tracing.traceId());
 * MDC.put("spanId", tracing.spanId());
 * tracing.parentSpanId().ifPresent(parent -> MDC.put("parentSpanId", parent));
 *
 * logger.info("Processing event");
 * // Log: [traceId=abc-123] [spanId=def-456] [parentSpanId=ghi-789] Processing event
 *
 * // Clear MDC after request
 * MDC.remove("traceId");
 * MDC.remove("spanId");
 * MDC.remove("parentSpanId");
 * }</pre>
 *
 * <p><b>W3C Trace Context Format</b><br>
 * HTTP Header: {@code traceparent: 00-<traceId>-<spanId>-<flags>}
 * <ul>
 *   <li>Version: {@code 00} (W3C version 1)</li>
 *   <li>Trace ID: 32-char hex (128-bit) or UUID</li>
 *   <li>Span ID: 16-char hex (64-bit) or UUID</li>
 *   <li>Flags: {@code 01} (sampled) or {@code 00} (not sampled)</li>
 * </ul>
 *
 * <p><b>Trace ID Requirements</b><br>
 * <ul>
 *   <li>Globally unique across all traces</li>
 *   <li>128-bit random value (UUIDv4 recommended)</li>
 *   <li>Consistent format (hex or UUID) throughout pipeline</li>
 *   <li>Never empty or all-zeros</li>
 * </ul>
 *
 * <p><b>Span ID Requirements</b><br>
 * <ul>
 *   <li>Unique within trace</li>
 *   <li>64-bit random value (UUID substring or random hex)</li>
 *   <li>Different for each operation/span</li>
 *   <li>Never empty or all-zeros</li>
 * </ul>
 *
 * <p><b>Best Practices</b><br>
 * <ul>
 *   <li>Root traces: Use {@code newTrace()} for new requests (no parent)</li>
 *   <li>Child spans: Use {@code childSpan()} for nested operations (preserves trace hierarchy)</li>
 *   <li>Propagation: Extract from HTTP traceparent header, propagate through pipeline</li>
 *   <li>Logging: Add traceId/spanId to MDC for log correlation</li>
 *   <li>Metrics: Tag metrics with traceId for request-level aggregation</li>
 *   <li>Sampling: Use flags field for sampling decisions (not in this implementation)</li>
 * </ul>
 *
 * <p><b>Anti-Patterns (Avoid)</b><br>
 * <ul>
 *   <li>❌ Reusing span IDs: Each span must have unique ID</li>
 *   <li>❌ Empty trace IDs: Must be non-null, non-empty</li>
 *   <li>❌ Missing parent: Use {@code Optional.empty()} for root spans, not null</li>
 *   <li>❌ Hardcoded IDs: Always generate random IDs (UUID or SecureRandom)</li>
 *   <li>❌ Ignoring parent: Nested operations MUST reference parent span</li>
 * </ul>
 *
 * <p><b>Integration Points</b><br>
 * <ul>
 *   <li>OpenTelemetry: Convert to/from SpanContext</li>
 *   <li>Jaeger: Trace ID = 128-bit, Span ID = 64-bit</li>
 *   <li>Zipkin: Compatible with W3C Trace Context</li>
 *   <li>HTTP Headers: Parse/serialize traceparent header</li>
 *   <li>Logging: MDC context for log correlation</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record. Safe for concurrent use and cross-thread propagation.
 *
 * @param traceId Unique trace identifier (128-bit hex or UUID, required, non-null)
 * @param spanId Unique span identifier for this operation (64-bit hex or UUID, required, non-null)
 * @param parentSpanId Optional parent span ID for hierarchical tracing (empty for root spans)
 * @see CallContext
 * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context Specification</a>
 * @doc.type record
 * @doc.purpose W3C-compliant distributed tracing context for request correlation
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record TracingContext(
    String traceId,
    String spanId,
    Optional<String> parentSpanId
) {
    public TracingContext {
        Objects.requireNonNull(traceId, "traceId required");
        Objects.requireNonNull(spanId, "spanId required");
        Objects.requireNonNull(parentSpanId, "parentSpanId required");
    }

    /**
     * Create a new root trace context.
     */
    public static TracingContext newTrace(String traceId, String spanId) {
        return new TracingContext(traceId, spanId, Optional.empty());
    }

    /**
     * Create a child span context.
     */
    public TracingContext childSpan(String newSpanId) {
        return new TracingContext(traceId, newSpanId, Optional.of(spanId));
    }
}
