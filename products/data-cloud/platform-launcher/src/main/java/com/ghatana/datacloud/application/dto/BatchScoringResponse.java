package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response DTO for batch scoring operations.
 *
 * <p>Contains aggregated quality scoring results for multiple entities
 * including success count, failure count, and detailed results.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * BatchScoringResponse response = BatchScoringResponse.builder()
 *     .tenantId("tenant-123")
 *     .total(10)
 *     .success(9)
 *     .failures(1)
 *     .results(resultsList)
 *     .durationMs(800)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Response transfer object for batch quality scoring
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class BatchScoringResponse {
    private final String tenantId;
    private final int total;
    private final int success;
    private final int failures;
    private final List<Map<String, Object>> results;
    private final long durationMs;

    /**
     * Constructs a new batch scoring response.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param total total items scored (non-negative)
     * @param success items scored successfully (non-negative)
     * @param failures items that failed (non-negative)
     * @param results detailed results list (non-null)
     * @param durationMs processing duration in milliseconds (non-negative)
     * @throws NullPointerException if any non-optional parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private BatchScoringResponse(String tenantId, int total, int success, 
            int failures, List<Map<String, Object>> results, long durationMs) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.results = Objects.requireNonNull(results, "results cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (total < 0 || success < 0 || failures < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
        if (success + failures != total) {
            throw new IllegalArgumentException("success + failures must equal total");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs cannot be negative");
        }

        this.total = total;
        this.success = success;
        this.failures = failures;
        this.durationMs = durationMs;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return tenant ID (non-blank)
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the total number of items scored.
     *
     * @return total count (non-negative)
     */
    public int getTotal() {
        return total;
    }

    /**
     * Gets the number of items scored successfully.
     *
     * @return success count (non-negative)
     */
    public int getSuccess() {
        return success;
    }

    /**
     * Gets the number of items that failed.
     *
     * @return failure count (non-negative)
     */
    public int getFailures() {
        return failures;
    }

    /**
     * Gets the detailed results.
     *
     * @return results list (non-null)
     */
    public List<Map<String, Object>> getResults() {
        return results;
    }

    /**
     * Gets the processing duration.
     *
     * @return duration in milliseconds (non-negative)
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Gets the success rate as percentage.
     *
     * @return success rate (0-100)
     */
    public double getSuccessRate() {
        if (total == 0) {
            return 0.0;
        }
        return (100.0 * success) / total;
    }

    /**
     * Creates a builder for constructing responses.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for BatchScoringResponse.
     */
    public static class Builder {
        private String tenantId;
        private int total;
        private int success;
        private int failures;
        private List<Map<String, Object>> results;
        private long durationMs;

        /**
         * Sets the tenant identifier.
         *
         * @param tenantId tenant ID
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the total count.
         *
         * @param total items scored
         * @return this builder
         */
        public Builder total(int total) {
            this.total = total;
            return this;
        }

        /**
         * Sets the success count.
         *
         * @param success successful items
         * @return this builder
         */
        public Builder success(int success) {
            this.success = success;
            return this;
        }

        /**
         * Sets the failure count.
         *
         * @param failures failed items
         * @return this builder
         */
        public Builder failures(int failures) {
            this.failures = failures;
            return this;
        }

        /**
         * Sets the detailed results.
         *
         * @param results results list
         * @return this builder
         */
        public Builder results(List<Map<String, Object>> results) {
            this.results = results;
            return this;
        }

        /**
         * Sets the processing duration.
         *
         * @param durationMs duration in milliseconds
         * @return this builder
         */
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        /**
         * Builds the BatchScoringResponse.
         *
         * @return new response instance
         */
        public BatchScoringResponse build() {
            return new BatchScoringResponse(tenantId, total, success, failures, results, durationMs);
        }
    }

    @Override
    public String toString() {
        return "BatchScoringResponse{" +
                "tenantId='" + tenantId + '\'' +
                ", total=" + total +
                ", success=" + success +
                ", failures=" + failures +
                ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                ", durationMs=" + durationMs +
                '}';
    }
}
