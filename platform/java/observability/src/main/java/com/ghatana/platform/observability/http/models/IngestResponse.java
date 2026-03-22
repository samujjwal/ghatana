package com.ghatana.platform.observability.http.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * HTTP response model for successful span ingestion.
 * <p>
 * Returned by POST /api/v1/traces/spans and POST /api/v1/traces/spans/batch.
 * </p>
 * <p>
 * Example JSON:
 * <pre>
 * {
 *   "success": true,
 *   "message": "Span ingested successfully",
 *   "spanId": "abc123",
 *   "traceId": "trace-001"
 * }
 * </pre>
 * </p>
 *
 * @author Ghatana Platform Team
 * @doc.type record
 * @doc.purpose HTTP response model for single span ingestion success/failure
 * @doc.layer observability
 * @doc.pattern DTO (Data Transfer Object), HTTP Response Model
 * @since 1.0.0
 */
public class IngestResponse {

    private final boolean success;
    private final String message;
    private final String spanId;
    private final String traceId;

    /**
     * Constructs an IngestResponse.
     *
     * @param success  Whether ingestion was successful
     * @param message  Human-readable message
     * @param spanId   Ingested span ID
     * @param traceId  Trace ID
     */
    @JsonCreator
    public IngestResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message,
            @JsonProperty("spanId") String spanId,
            @JsonProperty("traceId") String traceId) {
        this.success = success;
        this.message = message;
        this.spanId = spanId;
        this.traceId = traceId;
    }

    /**
     * Creates a successful ingestion response.
     *
     * @param spanId   Ingested span ID
     * @param traceId  Trace ID
     * @return Success response
     */
    public static IngestResponse success(String spanId, String traceId) {
        return new IngestResponse(true, "Span ingested successfully", spanId, traceId);
    }

    /**
     * Creates a failure ingestion response.
     *
     * @param spanId   Failed span ID
     * @param traceId  Trace ID
     * @param reason   Failure reason
     * @return Failure response
     */
    public static IngestResponse failure(String spanId, String traceId, String reason) {
        return new IngestResponse(false, "Failed to ingest span: " + reason, spanId, traceId);
    }

    // Getters

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getTraceId() {
        return traceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IngestResponse that = (IngestResponse) o;
        return success == that.success &&
                Objects.equals(message, that.message) &&
                Objects.equals(spanId, that.spanId) &&
                Objects.equals(traceId, that.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, spanId, traceId);
    }

    @Override
    public String toString() {
        return "IngestResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", spanId='" + spanId + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
