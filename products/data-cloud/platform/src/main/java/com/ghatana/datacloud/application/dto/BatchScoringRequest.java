package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request DTO for batch scoring operations.
 *
 * <p>Contains multiple entities to score for quality in batch.
 * Each entity is evaluated independently with aggregated results returned.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * BatchScoringRequest request = BatchScoringRequest.builder()
 *     .tenantId("tenant-123")
 *     .entities(List.of(
 *         Map.of("title", "Product 1"),
 *         Map.of("title", "Product 2")
 *     ))
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Request transfer object for batch quality scoring
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class BatchScoringRequest {
    private final String tenantId;
    private final List<Map<String, Object>> entities;

    /**
     * Constructs a new batch scoring request.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param entities list of entities to score (non-empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private BatchScoringRequest(String tenantId, List<Map<String, Object>> entities) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (entities.isEmpty()) {
            throw new IllegalArgumentException("entities cannot be empty");
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
     * Gets the entities to score.
     *
     * @return entities list (non-empty)
     */
    public List<Map<String, Object>> getEntities() {
        return entities;
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
     * Builder for BatchScoringRequest.
     */
    public static class Builder {
        private String tenantId;
        private List<Map<String, Object>> entities;

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
         * Sets the entities.
         *
         * @param entities list of entity maps
         * @return this builder
         */
        public Builder entities(List<Map<String, Object>> entities) {
            this.entities = entities;
            return this;
        }

        /**
         * Builds the BatchScoringRequest.
         *
         * @return new request instance
         */
        public BatchScoringRequest build() {
            return new BatchScoringRequest(tenantId, entities);
        }
    }

    @Override
    public String toString() {
        return "BatchScoringRequest{" +
                "tenantId='" + tenantId + '\'' +
                ", entities=" + entities.size() +
                '}';
    }
}
