package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * Response DTO for content validation operations.
 *
 * <p>Contains validation results including pass/fail status and any violations found.
 * Immutable value object for HTTP response serialization.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ContentValidationResponse response = ContentValidationResponse.builder()
 *     .tenantId("tenant-123")
 *     .passed(false)
 *     .violations(List.of(violation1, violation2))
 *     .durationMs(150)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Response transfer object for policy validation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ContentValidationResponse {
    private final String tenantId;
    private final boolean passed;
    private final List<Map<String, Object>> violations;
    private final long durationMs;

    /**
     * Constructs a new content validation response.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param passed whether validation passed
     * @param violations list of violation details
     * @param durationMs processing duration in milliseconds (non-negative)
     * @throws NullPointerException if any non-optional parameter is null
     */
    private ContentValidationResponse(String tenantId, boolean passed, 
            List<Map<String, Object>> violations, long durationMs) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.violations = Objects.requireNonNull(violations, "violations cannot be null");
        
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs cannot be negative");
        }
        
        this.passed = passed;
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
     * Checks if validation passed.
     *
     * @return true if no violations found, false otherwise
     */
    public boolean isPassed() {
        return passed;
    }

    /**
     * Gets the violations found.
     *
     * @return list of violation maps with type, severity, message, location
     */
    public List<Map<String, Object>> getViolations() {
        return violations;
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
     * Creates a builder for constructing responses.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ContentValidationResponse.
     *
     * @see ContentValidationResponse
     */
    public static class Builder {
        private String tenantId;
        private boolean passed;
        private List<Map<String, Object>> violations;
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
         * Sets whether validation passed.
         *
         * @param passed pass/fail status
         * @return this builder
         */
        public Builder passed(boolean passed) {
            this.passed = passed;
            return this;
        }

        /**
         * Sets the violations list.
         *
         * @param violations list of violation details
         * @return this builder
         */
        public Builder violations(List<Map<String, Object>> violations) {
            this.violations = violations;
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
         * Builds the ContentValidationResponse.
         *
         * @return new response instance
         * @throws NullPointerException if required fields are null
         * @throws IllegalArgumentException if validation fails
         */
        public ContentValidationResponse build() {
            return new ContentValidationResponse(tenantId, passed, violations, durationMs);
        }
    }

    @Override
    public String toString() {
        return "ContentValidationResponse{" +
                "tenantId='" + tenantId + '\'' +
                ", passed=" + passed +
                ", violations=" + violations.size() +
                ", durationMs=" + durationMs +
                '}';
    }
}
