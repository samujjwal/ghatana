package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * Response DTO for batch content validation operations.
 *
 * <p>Contains aggregated validation results for multiple content items
 * including success count, failure count, and individual results.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * BatchValidationResponse response = BatchValidationResponse.builder()
 *     .tenantId("tenant-123")
 *     .total(10)
 *     .passed(8)
 *     .failed(2)
 *     .results(resultsList)
 *     .durationMs(500)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Response transfer object for batch policy validation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class BatchValidationResponse {
    private final String tenantId;
    private final int total;
    private final int passed;
    private final int failed;
    private final List<Map<String, Object>> results;
    private final long durationMs;

    /**
     * Constructs a new batch validation response.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param total total items processed (non-negative)
     * @param passed items that passed validation (non-negative)
     * @param failed items that failed validation (non-negative)
     * @param results detailed results for each item (non-null)
     * @param durationMs processing duration in milliseconds (non-negative)
     * @throws NullPointerException if any non-optional parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private BatchValidationResponse(String tenantId, int total, int passed, 
            int failed, List<Map<String, Object>> results, long durationMs) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.results = Objects.requireNonNull(results, "results cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (total < 0 || passed < 0 || failed < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
        if (passed + failed != total) {
            throw new IllegalArgumentException("passed + failed must equal total");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs cannot be negative");
        }

        this.total = total;
        this.passed = passed;
        this.failed = failed;
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
     * Gets the total number of items processed.
     *
     * @return total count (non-negative)
     */
    public int getTotal() {
        return total;
    }

    /**
     * Gets the number of items that passed.
     *
     * @return passed count (non-negative)
     */
    public int getPassed() {
        return passed;
    }

    /**
     * Gets the number of items that failed.
     *
     * @return failed count (non-negative)
     */
    public int getFailed() {
        return failed;
    }

    /**
     * Gets the detailed results.
     *
     * @return list of result maps
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
     * Gets the pass rate as percentage.
     *
     * @return pass rate (0-100)
     */
    public double getPassRate() {
        if (total == 0) {
            return 0.0;
        }
        return (100.0 * passed) / total;
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
     * Builder for BatchValidationResponse.
     *
     * @see BatchValidationResponse
     */
    public static class Builder {
        private String tenantId;
        private int total;
        private int passed;
        private int failed;
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
         * @param total total items
         * @return this builder
         */
        public Builder total(int total) {
            this.total = total;
            return this;
        }

        /**
         * Sets the passed count.
         *
         * @param passed items that passed
         * @return this builder
         */
        public Builder passed(int passed) {
            this.passed = passed;
            return this;
        }

        /**
         * Sets the failed count.
         *
         * @param failed items that failed
         * @return this builder
         */
        public Builder failed(int failed) {
            this.failed = failed;
            return this;
        }

        /**
         * Sets the detailed results.
         *
         * @param results list of result maps
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
         * Builds the BatchValidationResponse.
         *
         * @return new response instance
         * @throws NullPointerException if required fields are null
         * @throws IllegalArgumentException if validation fails
         */
        public BatchValidationResponse build() {
            return new BatchValidationResponse(tenantId, total, passed, failed, results, durationMs);
        }
    }

    @Override
    public String toString() {
        return "BatchValidationResponse{" +
                "tenantId='" + tenantId + '\'' +
                ", total=" + total +
                ", passed=" + passed +
                ", failed=" + failed +
                ", passRate=" + String.format("%.1f%%", getPassRate()) +
                ", durationMs=" + durationMs +
                '}';
    }
}
