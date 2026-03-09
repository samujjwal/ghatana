package com.ghatana.platform.observability.http.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP request model for ingesting a single span.
 * <p>
 * Represents the JSON payload for POST /api/v1/traces/spans.
 * Maps to {@link com.ghatana.platform.observability.trace.SpanData} for internal processing.
 * </p>
 * <p>
 * Example JSON:
 * <pre>
 * {
 *   "spanId": "abc123",
 *   "traceId": "trace-001",
 *   "parentSpanId": "parent-001",
 *   "operationName": "GET /api/users",
 *   "serviceName": "user-service",
 *   "startTime": "2025-10-23T10:00:00Z",
 *   "endTime": "2025-10-23T10:00:01Z",
 *   "duration": 1000,
 *   "status": "OK",
 *   "tags": {"http.method": "GET", "http.status_code": "200"},
 *   "logs": {"event": "Request started", "level": "INFO"}
 * }
 * </pre>
 * </p>
 *
 * @doc.type record
 * @doc.purpose HTTP request model for single span ingestion (POST /api/v1/traces/spans)
 * @doc.layer observability
 * @doc.pattern DTO (Data Transfer Object), HTTP Request Model
 *
 * @author Ghatana Platform Team
 * @since 1.0.0
 * @see com.ghatana.platform.observability.trace.SpanData
 */
public class SpanRequest {

    @NotBlank(message = "spanId is required")
    private final String spanId;

    @NotBlank(message = "traceId is required")
    private final String traceId;

    private final String parentSpanId;  // Optional (null for root spans)

    @NotBlank(message = "operationName is required")
    private final String operationName;

    @NotBlank(message = "serviceName is required")
    private final String serviceName;

    @NotNull(message = "startTime is required")
    private final Instant startTime;

    private final Instant endTime;  // Optional (null if span not finished)

    private final Long duration;  // Optional (auto-calculated if null)

    private final String status;  // Optional (default: "UNSET")

    private final Map<String, String> tags;  // Optional (empty map if null)

    private final Map<String, String> logs;  // Optional (empty map if null)

    /**
     * Constructs a SpanRequest with all fields.
     * <p>
     * Jackson will use this constructor for JSON deserialization.
     * </p>
     *
     * @param spanId         Unique span identifier (required)
     * @param traceId        Trace identifier (required)
     * @param parentSpanId   Parent span ID (optional, null for root spans)
     * @param operationName  Operation name (required)
     * @param serviceName    Service name (required)
     * @param startTime      Span start time (required)
     * @param endTime        Span end time (optional)
     * @param duration       Duration in milliseconds (optional, auto-calculated)
     * @param status         OTEL status: "OK", "ERROR", or "UNSET" (optional, default "UNSET")
     * @param tags           Key-value tags (optional, empty if null)
     * @param logs           Key-value log entries (optional, empty if null)
     */
    @JsonCreator
    public SpanRequest(
            @JsonProperty("spanId") String spanId,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("parentSpanId") String parentSpanId,
            @JsonProperty("operationName") String operationName,
            @JsonProperty("serviceName") String serviceName,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("endTime") Instant endTime,
            @JsonProperty("duration") Long duration,
            @JsonProperty("status") String status,
            @JsonProperty("tags") Map<String, String> tags,
            @JsonProperty("logs") Map<String, String> logs) {
        this.spanId = spanId;
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.operationName = operationName;
        this.serviceName = serviceName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.status = status;
        this.tags = tags != null ? Map.copyOf(tags) : Map.of();
        this.logs = logs != null ? Map.copyOf(logs) : Map.of();
    }

    // Getters

    public String getSpanId() {
        return spanId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Long getDuration() {
        return duration;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Map<String, String> getLogs() {
        return logs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpanRequest that = (SpanRequest) o;
        return Objects.equals(spanId, that.spanId) &&
                Objects.equals(traceId, that.traceId) &&
                Objects.equals(parentSpanId, that.parentSpanId) &&
                Objects.equals(operationName, that.operationName) &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(endTime, that.endTime) &&
                Objects.equals(duration, that.duration) &&
                Objects.equals(status, that.status) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(logs, that.logs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spanId, traceId, parentSpanId, operationName, serviceName,
                startTime, endTime, duration, status, tags, logs);
    }

    @Override
    public String toString() {
        return "SpanRequest{" +
                "spanId='" + spanId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", parentSpanId='" + parentSpanId + '\'' +
                ", operationName='" + operationName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", duration=" + duration +
                ", status='" + status + '\'' +
                ", tags=" + tags +
                ", logs=" + logs +
                '}';
    }
}
