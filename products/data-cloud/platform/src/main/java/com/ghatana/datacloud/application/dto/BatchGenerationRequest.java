package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Objects;

/**
 * Request DTO for batch generation operations.
 *
 * <p>Contains multiple generation requests to be processed sequentially.
 * Each request specifies template ID and variables.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * BatchGenerationRequest request = BatchGenerationRequest.builder()
 *     .tenantId("tenant-123")
 *     .requests(List.of(
 *         GenerationRequestItem.builder().templateId("email-1").variables(...).build(),
 *         GenerationRequestItem.builder().templateId("email-2").variables(...).build()
 *     ))
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Request transfer object for batch content generation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class BatchGenerationRequest {
    private final String tenantId;
    private final List<GenerationRequestItem> requests;

    /**
     * Constructs a new batch generation request.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param requests list of generation requests (non-empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private BatchGenerationRequest(String tenantId, List<GenerationRequestItem> requests) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.requests = Objects.requireNonNull(requests, "requests cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("requests cannot be empty");
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
     * Gets the generation requests.
     *
     * @return request list (non-empty)
     */
    public List<GenerationRequestItem> getRequests() {
        return requests;
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
     * Builder for BatchGenerationRequest.
     */
    public static class Builder {
        private String tenantId;
        private List<GenerationRequestItem> requests;

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
         * Sets the generation requests.
         *
         * @param requests list of request items
         * @return this builder
         */
        public Builder requests(List<GenerationRequestItem> requests) {
            this.requests = requests;
            return this;
        }

        /**
         * Builds the BatchGenerationRequest.
         *
         * @return new request instance
         */
        public BatchGenerationRequest build() {
            return new BatchGenerationRequest(tenantId, requests);
        }
    }

    @Override
    public String toString() {
        return "BatchGenerationRequest{" +
                "tenantId='" + tenantId + '\'' +
                ", requests=" + requests.size() +
                '}';
    }
}
