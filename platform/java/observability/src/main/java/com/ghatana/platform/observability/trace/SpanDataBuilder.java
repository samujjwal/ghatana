package com.ghatana.platform.observability.trace;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating {@link SpanData} instances using a fluent API.
 * <p>
 * This builder provides a convenient way to construct immutable SpanData objects
 * with validation. It automatically calculates duration from start and end times
 * if not explicitly set, and provides default values for optional fields.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Fluent API</b>: Chain method calls for readable span construction</li>
 *   <li><b>Auto-Duration</b>: Calculates durationMs from startTime and endTime</li>
 *   <li><b>Default Status</b>: Sets status to "UNSET" if not specified</li>
 *   <li><b>Incremental Tags/Logs</b>: Add tags and logs one at a time or in bulk</li>
 *   <li><b>Validation</b>: Validates required fields and constraints at build time</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Basic span (duration auto-calculated)
 * SpanData span = SpanData.builder()
 *     .withSpanId("span-123")
 *     .withTraceId("trace-456")
 *     .withServiceName("api-gateway")
 *     .withName("http.request")
 *     .withStartTime(Instant.now())
 *     .withEndTime(Instant.now().plusMillis(100))
 *     .withStatus("OK")
 *     .build();  // durationMs = 100 (auto-calculated)
 *
 * // With tags and logs
 * SpanData enrichedSpan = SpanData.builder()
 *     .withSpanId("span-124")
 *     .withTraceId("trace-456")
 *     .withParentSpanId("span-123")
 *     .withServiceName("user-service")
 *     .withName("database.query")
 *     .withStartTime(Instant.now())
 *     .withEndTime(Instant.now().plusMillis(50))
 *     .withStatus("OK")
 *     .withTag("db.system", "postgresql")
 *     .withTag("db.statement", "SELECT * FROM users")
 *     .withTags(Map.of("db.user", "app", "db.name", "userdb"))
 *     .withLog("cache.miss", "true")
 *     .build();
 *
 * // Error span with status message
 * SpanData errorSpan = SpanData.builder()
 *     .withSpanId("span-125")
 *     .withTraceId("trace-456")
 *     .withServiceName("payment-service")
 *     .withName("payment.process")
 *     .withStartTime(Instant.now())
 *     .withEndTime(Instant.now().plusMillis(200))
 *     .withStatus("ERROR")
 *     .withStatusMessage("Payment gateway timeout")
 *     .withTag("error.type", "TimeoutException")
 *     .build();
 * }</pre>
 * 
 * <h2>Required Fields</h2>
 * The following fields MUST be set before calling {@link #build()}:
 * <ul>
 *   <li>spanId</li>
 *   <li>traceId</li>
 *   <li>serviceName</li>
 *   <li>startTime</li>
 *   <li>endTime</li>
 * </ul>
 * 
 * <h2>Optional Fields (with defaults)</h2>
 * <ul>
 *   <li><b>parentSpanId</b>: null (root span)</li>
 *   <li><b>name</b>: null</li>
 *   <li><b>operationName</b>: null</li>
 *   <li><b>status</b>: "UNSET"</li>
 *   <li><b>statusMessage</b>: null</li>
 *   <li><b>durationMs</b>: Calculated from endTime - startTime</li>
 *   <li><b>tags</b>: Empty map</li>
 *   <li><b>logs</b>: Empty map</li>
 * </ul>
 * 
 * <h2>Duration Calculation</h2>
 * If {@link #withDurationMs(long)} is called, that value is used. Otherwise,
 * duration is calculated automatically:
 * <pre>{@code
 * durationMs = Duration.between(startTime, endTime).toMillis()
 * }</pre>
 * 
 * <h2>Validation</h2>
 * Build-time validation ensures:
 * <ul>
 *   <li>Required fields are present</li>
 *   <li>endTime >= startTime</li>
 *   <li>durationMs >= 0</li>
 *   <li>status is one of: OK, ERROR, UNSET</li>
 * </ul>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety Not thread-safe (single-threaded builder)
 * @performance O(n) for n tags/logs
 * @since 1.0.0
 * @see SpanData
 * @doc.type class
 * @doc.purpose Fluent builder for constructing immutable SpanData instances with validation
 * @doc.layer observability
 * @doc.pattern Builder
 */
public class SpanDataBuilder {

    private String spanId;
    private String traceId;
    private String parentSpanId;
    private String name;
    private String serviceName;
    private String operationName;
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private String status = "UNSET";
    private String statusMessage;
    private final Map<String, String> tags = new HashMap<>();
    private final Map<String, String> logs = new HashMap<>();

    /**
     * Sets the span ID.
     *
     * @param spanId unique identifier for this span (required)
     * @return this builder for chaining
     */
    public SpanDataBuilder withSpanId(String spanId) {
        this.spanId = spanId;
        return this;
    }

    /**
     * Sets the trace ID.
     *
     * @param traceId identifier of the trace this span belongs to (required)
     * @return this builder for chaining
     */
    public SpanDataBuilder withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * Sets the parent span ID.
     *
     * @param parentSpanId identifier of the parent span, or null if root span
     * @return this builder for chaining
     */
    public SpanDataBuilder withParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
        return this;
    }

    /**
     * Sets the span name.
     *
     * @param name logical name of the span (e.g., "database.query")
     * @return this builder for chaining
     */
    public SpanDataBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the service name.
     *
     * @param serviceName name of the service that created this span (required)
     * @return this builder for chaining
     */
    public SpanDataBuilder withServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * Sets the operation name.
     *
     * @param operationName specific operation being performed
     * @return this builder for chaining
     */
    public SpanDataBuilder withOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    /**
     * Sets the start time.
     *
     * @param startTime when the span started (required)
     * @return this builder for chaining
     */
    public SpanDataBuilder withStartTime(Instant startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * Sets the end time.
     *
     * @param endTime when the span ended (required)
     * @return this builder for chaining
     */
    public SpanDataBuilder withEndTime(Instant endTime) {
        this.endTime = endTime;
        return this;
    }

    /**
     * Sets the duration in milliseconds.
     * <p>
     * Note: If not set explicitly, duration will be calculated automatically
     * from startTime and endTime during build().
     * </p>
     *
     * @param durationMs duration in milliseconds
     * @return this builder for chaining
     */
    public SpanDataBuilder withDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    /**
     * Sets the span status.
     *
     * @param status span status: "OK", "ERROR", or "UNSET" (default: "UNSET")
     * @return this builder for chaining
     */
    public SpanDataBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    /**
     * Sets the status message.
     *
     * @param statusMessage human-readable status message (e.g., error details)
     * @return this builder for chaining
     */
    public SpanDataBuilder withStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
        return this;
    }

    /**
     * Adds a single tag (key-value metadata).
     *
     * @param key   tag key (e.g., "http.method")
     * @param value tag value (e.g., "GET")
     * @return this builder for chaining
     */
    public SpanDataBuilder withTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    /**
     * Adds multiple tags at once.
     *
     * @param tags map of tags to add
     * @return this builder for chaining
     */
    public SpanDataBuilder withTags(Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }

    /**
     * Adds a single log entry.
     *
     * @param key   log key
     * @param value log value
     * @return this builder for chaining
     */
    public SpanDataBuilder withLog(String key, String value) {
        this.logs.put(key, value);
        return this;
    }

    /**
     * Adds multiple log entries at once.
     *
     * @param logs map of logs to add
     * @return this builder for chaining
     */
    public SpanDataBuilder withLogs(Map<String, String> logs) {
        this.logs.putAll(logs);
        return this;
    }

    /**
     * Builds the SpanData instance with validation.
     * <p>
     * If durationMs was not set explicitly, it will be calculated automatically
     * from startTime and endTime.
     * </p>
     *
     * @return a new immutable SpanData instance
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public SpanData build() {
        // Calculate duration if not set explicitly
        long finalDurationMs = (durationMs != null) ? durationMs.longValue() : calculateDuration();

        return new SpanData(
            spanId,
            traceId,
            parentSpanId,
            name,
            serviceName,
            operationName,
            startTime,
            endTime,
            finalDurationMs,
            status,
            statusMessage,
            tags,
            logs
        );
    }

    /**
     * Calculates duration from startTime and endTime.
     *
     * @return duration in milliseconds
     * @throws IllegalStateException if startTime or endTime is null
     */
    private long calculateDuration() {
        if (startTime == null || endTime == null) {
            throw new IllegalStateException(
                "Cannot calculate duration: startTime and endTime must be set"
            );
        }
        return Duration.between(startTime, endTime).toMillis();
    }
}
