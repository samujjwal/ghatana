package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Objects;

/**
 * Request DTO for batch content validation operations.
 *
 * <p>Contains multiple content items to validate against specified policies.
 * All items are processed independently with fail-soft semantics.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * BatchValidationRequest request = BatchValidationRequest.builder()
 *     .tenantId("tenant-123")
 *     .contentList(List.of("content1", "content2"))
 *     .policies(List.of("PROFANITY", "PII"))
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Request transfer object for batch policy validation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class BatchValidationRequest {
    private final String tenantId;
    private final List<String> contentList;
    private final List<String> policies;

    /**
     * Constructs a new batch validation request.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param contentList list of content items to validate (non-empty)
     * @param policies policy types to check (non-empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private BatchValidationRequest(String tenantId, List<String> contentList, List<String> policies) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.contentList = Objects.requireNonNull(contentList, "contentList cannot be null");
        this.policies = Objects.requireNonNull(policies, "policies cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (contentList.isEmpty()) {
            throw new IllegalArgumentException("contentList cannot be empty");
        }
        if (policies.isEmpty()) {
            throw new IllegalArgumentException("policies cannot be empty");
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
     * Gets the content items to validate.
     *
     * @return list of content strings (non-empty)
     */
    public List<String> getContentList() {
        return contentList;
    }

    /**
     * Gets the policy types to check.
     *
     * @return policy list (non-empty)
     */
    public List<String> getPolicies() {
        return policies;
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
     * Builder for BatchValidationRequest.
     *
     * @see BatchValidationRequest
     */
    public static class Builder {
        private String tenantId;
        private List<String> contentList;
        private List<String> policies;

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
         * Sets the content items.
         *
         * @param contentList list of content strings
         * @return this builder
         */
        public Builder contentList(List<String> contentList) {
            this.contentList = contentList;
            return this;
        }

        /**
         * Sets the policy types.
         *
         * @param policies list of policy types
         * @return this builder
         */
        public Builder policies(List<String> policies) {
            this.policies = policies;
            return this;
        }

        /**
         * Builds the BatchValidationRequest.
         *
         * @return new request instance
         * @throws NullPointerException if required fields are null
         * @throws IllegalArgumentException if validation fails
         */
        public BatchValidationRequest build() {
            return new BatchValidationRequest(tenantId, contentList, policies);
        }
    }

    @Override
    public String toString() {
        return "BatchValidationRequest{" +
                "tenantId='" + tenantId + '\'' +
                ", contentList.size=" + contentList.size() +
                ", policies=" + policies +
                '}';
    }
}
