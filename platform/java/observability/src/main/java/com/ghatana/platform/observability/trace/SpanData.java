package com.ghatana.platform.observability.trace;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing a single OpenTelemetry span in a distributed trace.
 * <p>
 * A span represents a unit of work or operation within a distributed system. It contains
 * timing information (start/end times), metadata (tags/logs), relationships to other spans
 * (parent/trace IDs), and status information. This is the fundamental building block of
 * distributed tracing.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Immutable</b>: All fields final, defensive copies for collections</li>
 *   <li><b>OpenTelemetry Semantics</b>: Follows OTEL span model (status, tags, logs)</li>
 *   <li><b>Vendor-Neutral</b>: No dependency on specific tracing backend (Jaeger, Zipkin)</li>
 *   <li><b>Validated</b>: Canonical constructor validates required fields and constraints</li>
 *   <li><b>Builder Pattern</b>: Fluent API via {@link SpanDataBuilder}</li>
 * </ul>
 * 
 * <h2>Span Hierarchy</h2>
 * Spans form a tree structure within a trace:
 * <ul>
 *   <li><b>Root Span</b>: No parent (parentSpanId is null) - entry point of trace</li>
 *   <li><b>Child Span</b>: Has parent (parentSpanId set) - nested operation</li>
 *   <li><b>Trace</b>: Collection of spans sharing same traceId</li>
 * </ul>
 * 
 * <h2>Status Values (OpenTelemetry)</h2>
 * <ul>
 *   <li><b>OK</b>: Operation completed successfully</li>
 *   <li><b>ERROR</b>: Operation failed (exception, timeout, etc.)</li>
 *   <li><b>UNSET</b>: Status not explicitly set (default)</li>
 * </ul>
 * 
 * <h2>Tags vs Logs</h2>
 * <ul>
 *   <li><b>Tags</b>: Key-value metadata about the span (e.g., http.method=GET, db.statement=SELECT)</li>
 *   <li><b>Logs</b>: Timestamped events within the span (e.g., cache.hit, exception.message)</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // HTTP request span
 * SpanData httpSpan = SpanData.builder()
 *     .withSpanId("span-123")
 *     .withTraceId("trace-456")
 *     .withName("http.request")
 *     .withServiceName("api-gateway")
 *     .withOperationName("GET /api/users")
 *     .withStartTime(Instant.now())
 *     .withEndTime(Instant.now().plusMillis(100))
 *     .withStatus("OK")
 *     .withTag("http.method", "GET")
 *     .withTag("http.status_code", "200")
 *     .withTag("http.url", "/api/users")
 *     .build();
 *
 * // Database query span (child)
 * SpanData dbSpan = SpanData.builder()
 *     .withSpanId("span-124")
 *     .withTraceId("trace-456")
 *     .withParentSpanId("span-123")  // Child of HTTP span
 *     .withName("database.query")
 *     .withServiceName("user-service")
 *     .withOperationName("SELECT users")
 *     .withStartTime(Instant.now())
 *     .withEndTime(Instant.now().plusMillis(50))
 *     .withStatus("OK")
 *     .withTag("db.system", "postgresql")
 *     .withTag("db.statement", "SELECT * FROM users WHERE id = ?")
 *     .withLog("cache.miss", "true")
 *     .build();
 *
 * // Check span properties
 * System.out.println("Is root: " + httpSpan.isRootSpan());  // true
 * System.out.println("Is error: " + httpSpan.isError());    // false
 * System.out.println("Duration: " + httpSpan.durationMs() + "ms");  // 100
 * }</pre>
 * 
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>spanId must not be null or blank</li>
 *   <li>traceId must not be null or blank</li>
 *   <li>serviceName must not be null or blank</li>
 *   <li>startTime must not be null</li>
 *   <li>endTime must not be null and >= startTime</li>
 *   <li>durationMs must be non-negative</li>
 *   <li>status must be one of: OK, ERROR, UNSET</li>
 * </ul>
 * 
 * <h2>Common Tags (OTEL Conventions)</h2>
 * <ul>
 *   <li><b>HTTP</b>: http.method, http.status_code, http.url, http.target</li>
 *   <li><b>Database</b>: db.system, db.statement, db.user, db.name</li>
 *   <li><b>RPC</b>: rpc.system, rpc.service, rpc.method</li>
 *   <li><b>Messaging</b>: messaging.system, messaging.destination, messaging.operation</li>
 * </ul>
 *
 * @param spanId         Unique identifier for this span (required)
 * @param traceId        Identifier of the trace this span belongs to (required)
 * @param parentSpanId   Identifier of the parent span, or null if root span
 * @param name           Logical name of the span (e.g., "database.query")
 * @param serviceName    Name of the service that created this span (required)
 * @param operationName  Specific operation being performed (e.g., "SELECT users")
 * @param startTime      When the span started (required)
 * @param endTime        When the span ended (required)
 * @param durationMs     Duration in milliseconds (derived from end - start)
 * @param status         Span status: "OK", "ERROR", "UNSET" (OTEL semantics)
 * @param statusMessage  Optional human-readable status message (e.g., error details)
 * @param tags           Key-value metadata (e.g., http.method, db.statement)
 * @param logs           Timestamped log entries within the span
 * 
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety Immutable (thread-safe)
 * @performance O(1) construction
 * @since 1.0.0
 * @see SpanDataBuilder
 * @see TraceInfo
 * @doc.type record
 * @doc.purpose Immutable OpenTelemetry span data with hierarchical tracing semantics
 * @doc.layer observability
 * @doc.pattern Value Object
 */
public record SpanData(
    String spanId,
    String traceId,
    String parentSpanId,
    String name,
    String serviceName,
    String operationName,
    Instant startTime,
    Instant endTime,
    long durationMs,
    String status,
    String statusMessage,
    Map<String, String> tags,
    Map<String, String> logs
) {

    /**
     * Validates span data consistency.
     * <p>
     * Validation rules:
     * <ul>
     *   <li>spanId must not be null or blank</li>
     *   <li>traceId must not be null or blank</li>
     *   <li>serviceName must not be null or blank</li>
     *   <li>startTime must not be null</li>
     *   <li>endTime must not be null</li>
     *   <li>endTime must be after or equal to startTime</li>
     *   <li>durationMs must be non-negative</li>
     *   <li>status must be one of: "OK", "ERROR", "UNSET"</li>
     * </ul>
     * </p>
     *
     * @throws IllegalArgumentException if validation fails
     */
    public SpanData {
        // Defensive copies for mutable fields
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        logs = logs == null ? Map.of() : Map.copyOf(logs);

        // Validation
        Objects.requireNonNull(spanId, "spanId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");

        if (spanId.isBlank()) {
            throw new IllegalArgumentException("spanId must not be blank");
        }
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException(
                "endTime must be after or equal to startTime: start=" + startTime + ", end=" + endTime
            );
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be non-negative: " + durationMs);
        }
        if (status != null && !status.equals("OK") && !status.equals("ERROR") && !status.equals("UNSET")) {
            throw new IllegalArgumentException(
                "status must be one of: OK, ERROR, UNSET. Got: " + status
            );
        }
    }

    /**
     * Returns a builder for creating SpanData instances.
     * <p>
     * The builder provides a fluent API for constructing spans with validation.
     * </p>
     *
     * @return a new SpanDataBuilder instance
     */
    public static SpanDataBuilder builder() {
        return new SpanDataBuilder();
    }

    /**
     * Checks if this span represents an error.
     *
     * @return true if status is "ERROR", false otherwise
     */
    public boolean isError() {
        return "ERROR".equals(status);
    }

    /**
     * Checks if this is a root span (no parent).
     *
     * @return true if parentSpanId is null or blank, false otherwise
     */
    public boolean isRootSpan() {
        return parentSpanId == null || parentSpanId.isBlank();
    }

    /**
     * Gets a tag value by key.
     *
     * @param key the tag key
     * @return the tag value, or null if not present
     */
    public String getTag(String key) {
        return tags.get(key);
    }

    /**
     * Gets a log entry by key.
     *
     * @param key the log key
     * @return the log value, or null if not present
     */
    public String getLog(String key) {
        return logs.get(key);
    }
}
