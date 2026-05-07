package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Objects;

/**
 * Request DTO for content validation operations.
 *
 * <p>Contains the content to validate and policies to check against.
 * All fields are required and validated on construction.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ContentValidationRequest request = ContentValidationRequest.builder()
 *     .tenantId("tenant-123")
 *     .content("This is content to check")
 *     .policies(List.of("PROFANITY", "PII"))
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Request transfer object for policy validation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ContentValidationRequest {
    private final String tenantId;
    private final String content;
    private final List<String> policies;

    /**
     * Constructs a new content validation request.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param content content to validate (non-blank)
     * @param policies policy types to check (non-empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if tenantId is blank or content is empty
     */
    private ContentValidationRequest(String tenantId, String content, List<String> policies) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.policies = Objects.requireNonNull(policies, "policies cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("content cannot be blank");
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
     * Gets the content to validate.
     *
     * @return content (non-blank)
     */
    public String getContent() {
        return content;
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
     * Builder for ContentValidationRequest.
     *
     * @see ContentValidationRequest
     */
    public static class Builder {
        private String tenantId;
        private String content;
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
         * Sets the content to validate.
         *
         * @param content content string
         * @return this builder
         */
        public Builder content(String content) {
            this.content = content;
            return this;
        }

        /**
         * Sets the policy types to check.
         *
         * @param policies list of policy types
         * @return this builder
         */
        public Builder policies(List<String> policies) {
            this.policies = policies;
            return this;
        }

        /**
         * Builds the ContentValidationRequest.
         *
         * @return new request instance
         * @throws NullPointerException if any field is null
         * @throws IllegalArgumentException if validation fails
         */
        public ContentValidationRequest build() {
            return new ContentValidationRequest(tenantId, content, policies);
        }
    }

    @Override
    public String toString() {
        return "ContentValidationRequest{" +
                "tenantId='" + tenantId + '\'' +
                ", content='" + content.substring(0, Math.min(50, content.length())) + "...'" +
                ", policies=" + policies +
                '}';
    }
}
