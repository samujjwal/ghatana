package com.ghatana.platform.observability.http.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * HTTP request model for batch span ingestion.
 * <p>
 * Represents the JSON payload for POST /api/v1/traces/spans/batch.
 * Allows ingesting multiple spans in a single request for efficiency.
 * </p>
 * <p>
 * Example JSON:
 * <pre>
 * {
 *   "spans": [
 *     {
 *       "spanId": "abc123",
 *       "traceId": "trace-001",
 *       "operationName": "GET /api/users",
 *       "serviceName": "user-service",
 *       "startTime": "2025-10-23T10:00:00Z"
 *     },
 *     {
 *       "spanId": "def456",
 *       "traceId": "trace-002",
 *       "operationName": "POST /api/orders",
 *       "serviceName": "order-service",
 *       "startTime": "2025-10-23T10:00:05Z"
 *     }
 *   ]
 * }
 * </pre>
 * </p>
 *
 * @doc.type record
 * @doc.purpose HTTP request model for batch span ingestion (POST /api/v1/traces/spans/batch)
 * @doc.layer observability
 * @doc.pattern DTO (Data Transfer Object), HTTP Request Model, Batch Input
 *
 * @author Ghatana Platform Team
 * @since 1.0.0
 * @see SpanRequest
 */
public class BatchSpanRequest {

    @NotNull(message = "spans list is required")
    private final List<SpanRequest> spans;

    /**
     * Constructs a BatchSpanRequest.
     *
     * @param spans  List of span requests (required, must not be null)
     */
    @JsonCreator
    public BatchSpanRequest(@JsonProperty("spans") List<SpanRequest> spans) {
        this.spans = spans != null ? List.copyOf(spans) : List.of();
    }

    /**
     * Gets the list of span requests.
     *
     * @return Immutable list of spans
     */
    public List<SpanRequest> getSpans() {
        return spans;
    }

    /**
     * Gets the number of spans in this batch.
     *
     * @return Span count
     */
    public int size() {
        return spans.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchSpanRequest that = (BatchSpanRequest) o;
        return Objects.equals(spans, that.spans);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spans);
    }

    @Override
    public String toString() {
        return "BatchSpanRequest{spans=" + spans.size() + " spans}";
    }
}
