package com.ghatana.platform.observability.trace;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record representing complete metadata for a distributed trace.
 * <p>
 * A trace is a collection of spans that represent the end-to-end journey of a request
 * through a distributed system. TraceInfo aggregates all spans belonging to the same
 * traceId and provides summary information (duration, error count, status, etc.).
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Trace Aggregation</b>: Consolidates all spans with same traceId</li>
 *   <li><b>Summary Metrics</b>: Total duration, span count, error count</li>
 *   <li><b>Status Derivation</b>: Overall status based on span statuses</li>
 *   <li><b>Service Attribution</b>: Primary service (typically entry point)</li>
 *   <li><b>Span Navigation</b>: Find root span, error spans, spans by service</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Build trace from spans
 * TraceInfo trace = TraceInfo.builder()
 *     .withTraceId("trace-456")
 *     .addSpan(httpSpan)
 *     .addSpan(dbSpan)
 * @doc.type class
 * @doc.purpose Immutable trace metadata aggregating distributed spans for telemetry analysis
 * @doc.layer core
 * @doc.pattern Value Object, Aggregator
 *     .addSpan(cacheSpan)
 *     .build();  // Auto-calculates startTime, endTime, duration, counts, status
 *
 * // Query trace properties
 * System.out.println("Trace ID: " + trace.traceId());
 * System.out.println("Duration: " + trace.durationMs() + "ms");
 * System.out.println("Total spans: " + trace.spanCount());
 * System.out.println("Error count: " + trace.errorCount());
 * System.out.println("Service: " + trace.serviceName());
 * System.out.println("Status: " + trace.status());
 *
 * // Navigate spans
 * SpanData rootSpan = trace.getRootSpan();
 * List<SpanData> errorSpans = trace.getErrorSpans();
 * List<SpanData> dbSpans = trace.getSpansByService("database-service");
 *
 * // Check trace health
 * if (trace.hasErrors()) {
 *     System.out.println("Trace has errors!");
 * }
 * }</pre>
 * 
 * <h2>Trace Lifecycle</h2>
 * <ol>
 *   <li><b>Start</b>: First span starts (startTime = earliest span start)</li>
 *   <li><b>Processing</b>: Spans execute (may be sequential or concurrent)</li>
 *   <li><b>End</b>: Last span ends (endTime = latest span end)</li>
 *   <li><b>Duration</b>: Total time from first span start to last span end</li>
 * </ol>
 * 
 * <h2>Status Derivation</h2>
 * Overall trace status is derived from span statuses:
 * <ul>
 *   <li><b>ERROR</b>: If any span has status "ERROR"</li>
 *   <li><b>OK</b>: If all spans have status "OK"</li>
 *   <li><b>UNSET</b>: If all spans have status "UNSET"</li>
 * </ul>
 * 
 * <h2>Service Name</h2>
 * The primary service is typically the entry point (root span's service).
 * If no root span, serviceName may be null.
 * 
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>traceId must not be null or blank</li>
 *   <li>spans must not be null (can be empty for incomplete traces)</li>
 *   <li>startTime must not be null</li>
 *   <li>endTime must not be null and >= startTime</li>
 *   <li>durationMs must be non-negative</li>
 *   <li>spanCount must match spans.size()</li>
 *   <li>errorCount must be between 0 and spanCount</li>
 *   <li>status must be one of: OK, ERROR, UNSET</li>
 * </ul>
 *
 * @param traceId     Unique identifier for this trace (required)
 * @param spans       List of all spans in this trace (required, immutable copy)
 * @param startTime   When the first span started (required)
 * @param endTime     When the last span ended (required)
 * @param durationMs  Duration from first span start to last span end (milliseconds)
 * @param spanCount   Total number of spans in this trace
 * @param errorCount  Number of spans with ERROR status
 * @param serviceName Primary service name (typically the entry point service)
 * @param status      Overall trace status: "OK" if all spans OK, "ERROR" if any error
 * 
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety Immutable (thread-safe)
 * @performance O(1) access, O(n) for span navigation
 * @since 1.0.0
 * @see SpanData
 * @see TraceInfoBuilder
 */
public record TraceInfo(
    String traceId,
    List<SpanData> spans,
    Instant startTime,
    Instant endTime,
    long durationMs,
    int spanCount,
    int errorCount,
    String serviceName,
    String status
) {

    /**
     * Validates trace info consistency.
     * <p>
     * Validation rules:
     * <ul>
     *   <li>traceId must not be null or blank</li>
     *   <li>spans must not be null (can be empty for incomplete traces)</li>
     *   <li>startTime must not be null</li>
     *   <li>endTime must not be null</li>
     *   <li>endTime must be after or equal to startTime</li>
     *   <li>durationMs must be non-negative</li>
     *   <li>spanCount must match spans.size()</li>
     *   <li>errorCount must be non-negative and <= spanCount</li>
     *   <li>status must be one of: "OK", "ERROR", "UNSET"</li>
     * </ul>
     * </p>
     *
     * @throws IllegalArgumentException if validation fails
     */
    public TraceInfo {
        // Defensive copy for mutable field
        spans = spans == null ? List.of() : List.copyOf(spans);

        // Validation
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");

        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException(
                "endTime must be after or equal to startTime: start=" + startTime + ", end=" + endTime
            );
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be non-negative: " + durationMs);
        }
        if (spanCount != spans.size()) {
            throw new IllegalArgumentException(
                "spanCount must match spans.size(): spanCount=" + spanCount + ", spans.size()=" + spans.size()
            );
        }
        if (errorCount < 0 || errorCount > spanCount) {
            throw new IllegalArgumentException(
                "errorCount must be between 0 and spanCount: errorCount=" + errorCount + ", spanCount=" + spanCount
            );
        }
        if (status != null && !status.equals("OK") && !status.equals("ERROR") && !status.equals("UNSET")) {
            throw new IllegalArgumentException(
                "status must be one of: OK, ERROR, UNSET. Got: " + status
            );
        }
    }

    /**
     * Returns a builder for creating TraceInfo instances.
     * <p>
     * The builder provides a fluent API for constructing traces with validation
     * and automatic aggregation of span data.
     * </p>
     *
     * @return a new TraceInfoBuilder instance
     */
    public static TraceInfoBuilder builder() {
        return new TraceInfoBuilder();
    }

    /**
     * Checks if this trace contains any errors.
     *
     * @return true if errorCount > 0, false otherwise
     */
    public boolean hasErrors() {
        return errorCount > 0;
    }

    /**
     * Checks if this trace is complete (has spans).
     *
     * @return true if spanCount > 0, false otherwise
     */
    public boolean isComplete() {
        return spanCount > 0;
    }

    /**
     * Gets the root span (span with no parent).
     * <p>
     * A trace typically has one root span representing the entry point.
     * </p>
     *
     * @return the root span, or null if no root span found
     */
    public SpanData getRootSpan() {
        return spans.stream()
            .filter(SpanData::isRootSpan)
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets all error spans.
     *
     * @return list of spans with ERROR status
     */
    public List<SpanData> getErrorSpans() {
        return spans.stream()
            .filter(SpanData::isError)
            .toList();
    }

    /**
     * Gets spans for a specific service.
     *
     * @param serviceName the service name to filter by
     * @return list of spans from the specified service
     */
    public List<SpanData> getSpansByService(String serviceName) {
        if (serviceName == null) {
            return List.of();
        }
        return spans.stream()
            .filter(span -> serviceName.equals(span.serviceName()))
            .toList();
    }
}
