package com.ghatana.platform.observability.http.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * HTTP response model for batch span ingestion.
 * <p>
 * Returned by POST /api/v1/traces/spans/batch.
 * Provides detailed success/failure information for each span in the batch.
 * </p>
 * <p>
 * Example JSON:
 * <pre>
 * {
 *   "totalSpans": 10,
 *   "successCount": 9,
 *   "failureCount": 1,
 *   "results": [
 *     {"success": true, "message": "Span ingested successfully", "spanId": "abc123", "traceId": "trace-001"},
 *     {"success": false, "message": "Failed to ingest span: Invalid timestamp", "spanId": "def456", "traceId": "trace-002"}
 *   ]
 * }
 * </pre>
 * </p>
 *
 * @doc.type record
 * @doc.purpose HTTP response model for batch span ingestion with per-span success/failure tracking
 * @doc.layer observability
 * @doc.pattern DTO (Data Transfer Object), HTTP Response Model, Batch Result
 * @since 1.0.0
 */
public class BatchIngestResponse {

    private final int totalSpans;
    private final int successCount;
    private final int failureCount;
    private final List<IngestResponse> results;

    /**
     * Constructs a BatchIngestResponse.
     *
     * @param totalSpans     Total number of spans in the batch
     * @param successCount   Number of successfully ingested spans
     * @param failureCount   Number of failed spans
     * @param results        Detailed results for each span
     */
    @JsonCreator
    public BatchIngestResponse(
            @JsonProperty("totalSpans") int totalSpans,
            @JsonProperty("successCount") int successCount,
            @JsonProperty("failureCount") int failureCount,
            @JsonProperty("results") List<IngestResponse> results) {
        this.totalSpans = totalSpans;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.results = results != null ? List.copyOf(results) : List.of();
    }

    /**
     * Creates a batch response from individual results.
     *
     * @param results  List of individual ingest responses
     * @return Batch response with aggregated counts
     */
    public static BatchIngestResponse fromResults(List<IngestResponse> results) {
        int total = results.size();
        long successCount = results.stream().filter(IngestResponse::isSuccess).count();
        int failures = (int) (total - successCount);
        return new BatchIngestResponse(total, (int) successCount, failures, results);
    }

    // Getters

    public int getTotalSpans() {
        return totalSpans;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public List<IngestResponse> getResults() {
        return results;
    }

    /**
     * Checks if all spans were successfully ingested.
     *
     * @return True if all spans succeeded, false otherwise
     */
    public boolean isFullSuccess() {
        return failureCount == 0;
    }

    /**
     * Checks if any spans were successfully ingested.
     *
     * @return True if at least one span succeeded
     */
    public boolean hasAnySuccess() {
        return successCount > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchIngestResponse that = (BatchIngestResponse) o;
        return totalSpans == that.totalSpans &&
                successCount == that.successCount &&
                failureCount == that.failureCount &&
                Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalSpans, successCount, failureCount, results);
    }

    @Override
    public String toString() {
        return "BatchIngestResponse{" +
                "totalSpans=" + totalSpans +
                ", successCount=" + successCount +
                ", failureCount=" + failureCount +
                ", results=" + results.size() + " results" +
                '}';
    }
}
