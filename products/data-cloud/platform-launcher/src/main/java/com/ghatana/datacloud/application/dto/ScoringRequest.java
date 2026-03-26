package com.ghatana.datacloud.application.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Request DTO for quality scoring operations.
 *
 * <p>Contains entity to score for quality across dimensions.
 * Entity should contain fields relevant to scoring dimensions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ScoringRequest request = ScoringRequest.builder()
 *     .tenantId("tenant-123")
 *     .entity(Map.of("title", "Product Title", "description", "..."))
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Request transfer object for quality scoring
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ScoringRequest {
    private final String tenantId;
    private final Map<String, Object> entity;

    /**
     * Constructs a new scoring request.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param entity entity to score (non-empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private ScoringRequest(String tenantId, Map<String, Object> entity) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.entity = Objects.requireNonNull(entity, "entity cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("entity cannot be empty");
        }
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
     * Gets the entity to score.
     *
     * @return entity map (non-empty)
     */
    public Map<String, Object> getEntity() {
        return entity;
    }

    /**
     * Creates a builder for constructing requests.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ScoringRequest.
     *
     * @see ScoringRequest
     */
    public static class Builder {
        private String tenantId;
        private Map<String, Object> entity;

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
         * Sets the entity to score.
         *
         * @param entity entity map
         * @return this builder
         */
        public Builder entity(Map<String, Object> entity) {
            this.entity = entity;
            return this;
        }

        /**
         * Builds the ScoringRequest.
         *
         * @return new request instance
         * @throws NullPointerException if required fields are null
         * @throws IllegalArgumentException if validation fails
         */
        public ScoringRequest build() {
            return new ScoringRequest(tenantId, entity);
        }
    }

    @Override
    public String toString() {
        return "ScoringRequest{" +
                "tenantId='" + tenantId + '\'' +
                ", entity.keys=" + entity.keySet() +
                '}';
    }
}
