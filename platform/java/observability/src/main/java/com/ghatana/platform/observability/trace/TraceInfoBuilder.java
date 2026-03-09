package com.ghatana.platform.observability.trace;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link TraceInfo} instances using a fluent API.
 * <p>
 * This builder automatically aggregates span data to calculate trace-level metrics
 * like duration, span count, error count, and overall status. It simplifies trace
 * construction by handling all aggregation logic automatically.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Auto-Aggregation</b>: Calculates metrics from added spans</li>
 *   <li><b>Smart Defaults</b>: Derives startTime, endTime, duration, counts, status</li>
 *   <li><b>Incremental Building</b>: Add spans one at a time or in bulk</li>
 *   <li><b>Override Support</b>: Explicit values override auto-calculation</li>
 *   <li><b>Validation</b>: Ensures consistency at build time</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Simple auto-aggregation (most common)
 * TraceInfo trace = TraceInfo.builder()
 *     .withTraceId("trace-456")
 *     .addSpan(span1)
 *     .addSpan(span2)
 *     .addSpan(span3)
 *     .build();  
 * // Auto-calculates: startTime, endTime, duration, spanCount, errorCount, serviceName, status
 *
 * // With explicit overrides
 * TraceInfo traceWithOverrides = TraceInfo.builder()
 *     .withTraceId("trace-789")
 *     .addSpans(spanList)
 *     .withServiceName("custom-service")  // Override default
 *     .withStatus("ERROR")  // Override calculated status
 *     .build();
 *
 * // Build from scratch (no spans yet - incomplete trace)
 * TraceInfo incompleteTrace = TraceInfo.builder()
 *     .withTraceId("trace-000")
 *     .withStartTime(Instant.now())
 *     .withEndTime(Instant.now().plusMillis(100))
 *     .withDurationMs(100)
 *     .withSpanCount(0)
 *     .withErrorCount(0)
 *     .withServiceName("unknown")
 *     .withStatus("UNSET")
 *     .build();  // No spans, all metrics explicit
 * }</pre>
 * 
 * <h2>Auto-Calculation Logic</h2>
 * If not explicitly set, the following values are calculated from spans:
 * <ul>
 *   <li><b>startTime</b>: Earliest span.startTime() (or Instant.EPOCH if no spans)</li>
 *   <li><b>endTime</b>: Latest span.endTime() (or Instant.EPOCH if no spans)</li>
 *   <li><b>durationMs</b>: Duration.between(startTime, endTime).toMillis()</li>
 *   <li><b>spanCount</b>: spans.size()</li>
 *   <li><b>errorCount</b>: Count of spans with status "ERROR"</li>
 *   <li><b>serviceName</b>: Root span's service name (or null if no root)</li>
 *   <li><b>status</b>: "ERROR" if errorCount > 0, else "OK"</li>
 * </ul>
 * 
 * <h2>Root Span Detection</h2>
 * The builder finds the root span (span with no parent) to extract the primary
 * service name. This represents the entry point of the trace.
 * 
 * <h2>Status Calculation</h2>
 * <ul>
 *   <li>If any span has status "ERROR" → trace status = "ERROR"</li>
 *   <li>If all spans have status "OK" → trace status = "OK"</li>
 *   <li>If all spans have status "UNSET" → trace status = "OK" (default)</li>
 * </ul>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety Not thread-safe (single-threaded builder)
 * @performance O(n) for n spans (aggregation)
 * @since 1.0.0
 * @see TraceInfo
 * @see SpanData
 * @doc.type class
 * @doc.purpose Fluent builder for constructing TraceInfo with automatic span aggregation
 * @doc.layer observability
 * @doc.pattern Builder
 */
public class TraceInfoBuilder {

    private String traceId;
    private final List<SpanData> spans = new ArrayList<>();
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private Integer spanCount;
    private Integer errorCount;
    private String serviceName;
    private String status;

    /**
     * Sets the trace ID.
     *
     * @param traceId unique identifier for this trace (required)
     * @return this builder for chaining
     */
    public TraceInfoBuilder withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * Adds a single span to this trace.
     *
     * @param span the span to add
     * @return this builder for chaining
     */
    public TraceInfoBuilder addSpan(SpanData span) {
        if (span != null) {
            this.spans.add(span);
        }
        return this;
    }

    /**
     * Adds multiple spans to this trace.
     *
     * @param spans the spans to add
     * @return this builder for chaining
     */
    public TraceInfoBuilder addSpans(List<SpanData> spans) {
        if (spans != null) {
            this.spans.addAll(spans);
        }
        return this;
    }

    /**
     * Sets the start time explicitly.
     * <p>
     * If not set, start time will be calculated from the earliest span start time.
     * </p>
     *
     * @param startTime when the first span started
     * @return this builder for chaining
     */
    public TraceInfoBuilder withStartTime(Instant startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * Sets the end time explicitly.
     * <p>
     * If not set, end time will be calculated from the latest span end time.
     * </p>
     *
     * @param endTime when the last span ended
     * @return this builder for chaining
     */
    public TraceInfoBuilder withEndTime(Instant endTime) {
        this.endTime = endTime;
        return this;
    }

    /**
     * Sets the duration explicitly.
     * <p>
     * If not set, duration will be calculated from startTime and endTime.
     * </p>
     *
     * @param durationMs duration in milliseconds
     * @return this builder for chaining
     */
    public TraceInfoBuilder withDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    /**
     * Sets the span count explicitly.
     * <p>
     * If not set, span count will be calculated from spans.size().
     * </p>
     *
     * @param spanCount total number of spans
     * @return this builder for chaining
     */
    public TraceInfoBuilder withSpanCount(int spanCount) {
        this.spanCount = spanCount;
        return this;
    }

    /**
     * Sets the error count explicitly.
     * <p>
     * If not set, error count will be calculated by counting error spans.
     * </p>
     *
     * @param errorCount number of error spans
     * @return this builder for chaining
     */
    public TraceInfoBuilder withErrorCount(int errorCount) {
        this.errorCount = errorCount;
        return this;
    }

    /**
     * Sets the primary service name.
     *
     * @param serviceName the service name (typically entry point service)
     * @return this builder for chaining
     */
    public TraceInfoBuilder withServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * Sets the overall trace status.
     * <p>
     * If not set, status will be calculated: "ERROR" if any error spans, else "OK".
     * </p>
     *
     * @param status trace status: "OK", "ERROR", or "UNSET"
     * @return this builder for chaining
     */
    public TraceInfoBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    /**
     * Builds the TraceInfo instance with automatic aggregation.
     * <p>
     * This method automatically calculates missing values:
     * <ul>
     *   <li>startTime: earliest span start time</li>
     *   <li>endTime: latest span end time</li>
     *   <li>durationMs: endTime - startTime</li>
     *   <li>spanCount: spans.size()</li>
     *   <li>errorCount: number of error spans</li>
     *   <li>serviceName: root span's service name</li>
     *   <li>status: "ERROR" if errors, else "OK"</li>
     * </ul>
     * </p>
     *
     * @return a new immutable TraceInfo instance
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public TraceInfo build() {
        // Calculate aggregated values if not set explicitly
        Instant finalStartTime = startTime != null ? startTime : calculateStartTime();
        Instant finalEndTime = endTime != null ? endTime : calculateEndTime();
        long finalDurationMs = durationMs != null ? durationMs : calculateDuration(finalStartTime, finalEndTime);
        int finalSpanCount = spanCount != null ? spanCount : spans.size();
        int finalErrorCount = errorCount != null ? errorCount : calculateErrorCount();
        String finalServiceName = serviceName != null ? serviceName : calculateServiceName();
        String finalStatus = status != null ? status : calculateStatus(finalErrorCount);

        return new TraceInfo(
            traceId,
            spans,
            finalStartTime,
            finalEndTime,
            finalDurationMs,
            finalSpanCount,
            finalErrorCount,
            finalServiceName,
            finalStatus
        );
    }

    /**
     * Calculates start time from the earliest span.
     *
     * @return earliest span start time, or Instant.EPOCH if no spans
     */
    private Instant calculateStartTime() {
        return spans.stream()
            .map(SpanData::startTime)
            .min(Instant::compareTo)
            .orElse(Instant.EPOCH);
    }

    /**
     * Calculates end time from the latest span.
     *
     * @return latest span end time, or Instant.EPOCH if no spans
     */
    private Instant calculateEndTime() {
        return spans.stream()
            .map(SpanData::endTime)
            .max(Instant::compareTo)
            .orElse(Instant.EPOCH);
    }

    /**
     * Calculates duration from start and end times.
     *
     * @param start the start time
     * @param end   the end time
     * @return duration in milliseconds
     */
    private long calculateDuration(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Duration.between(start, end).toMillis();
    }

    /**
     * Counts spans with ERROR status.
     *
     * @return number of error spans
     */
    private int calculateErrorCount() {
        return (int) spans.stream()
            .filter(span -> "ERROR".equals(span.status()))
            .count();
    }

    /**
     * Determines primary service name from root span.
     *
     * @return root span's service name, or null if no root span
     */
    private String calculateServiceName() {
        return spans.stream()
            .filter(SpanData::isRootSpan)
            .findFirst()
            .map(SpanData::serviceName)
            .orElse(null);
    }

    /**
     * Calculates overall status based on error count.
     *
     * @param errors number of error spans
     * @return "ERROR" if errors > 0, else "OK"
     */
    private String calculateStatus(int errors) {
        return errors > 0 ? "ERROR" : "OK";
    }
}
