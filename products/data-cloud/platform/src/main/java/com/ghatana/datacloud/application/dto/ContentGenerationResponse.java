package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Objects;

/**
 * Response DTO for content generation operations.
 *
 * <p>Contains generated content and metadata about guardrails applied during generation.
 * Success flag indicates whether generation completed successfully.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ContentGenerationResponse response = ContentGenerationResponse.builder()
 *     .tenantId("tenant-123")
 *     .templateId("email-welcome")
 *     .content("Hello John from Acme!")
 *     .success(true)
 *     .guardrailsApplied(List.of("length-limit", "tone-check"))
 *     .durationMs(200)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Response transfer object for content generation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ContentGenerationResponse {
    private final String tenantId;
    private final String templateId;
    private final String content;
    private final boolean success;
    private final List<String> guardrailsApplied;
    private final long durationMs;

    /**
     * Constructs a new content generation response.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param templateId template identifier (non-blank)
     * @param content generated content (non-blank if success)
     * @param success whether generation succeeded
     * @param guardrailsApplied list of applied guardrails (non-null)
     * @param durationMs processing duration in milliseconds (non-negative)
     * @throws NullPointerException if any non-optional parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private ContentGenerationResponse(String tenantId, String templateId, String content,
            boolean success, List<String> guardrailsApplied, long durationMs) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.templateId = Objects.requireNonNull(templateId, "templateId cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.guardrailsApplied = Objects.requireNonNull(guardrailsApplied, "guardrailsApplied cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId cannot be blank");
        }
        if (success && content.isBlank()) {
            throw new IllegalArgumentException("content cannot be blank when success=true");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs cannot be negative");
        }

        this.success = success;
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
     * Gets the template identifier.
     *
     * @return template ID (non-blank)
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * Gets the generated content.
     *
     * @return generated content (non-blank if success)
     */
    public String getContent() {
        return content;
    }

    /**
     * Checks if generation succeeded.
     *
     * @return true if content was generated successfully
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the guardrails applied.
     *
     * @return list of guardrail names (non-null)
     */
    public List<String> getGuardrailsApplied() {
        return guardrailsApplied;
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
     * Builder for ContentGenerationResponse.
     *
     * @see ContentGenerationResponse
     */
    public static class Builder {
        private String tenantId;
        private String templateId;
        private String content;
        private boolean success;
        private List<String> guardrailsApplied;
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
         * Sets the template identifier.
         *
         * @param templateId template ID
         * @return this builder
         */
        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        /**
         * Sets the generated content.
         *
         * @param content generated content string
         * @return this builder
         */
        public Builder content(String content) {
            this.content = content;
            return this;
        }

        /**
         * Sets the success status.
         *
         * @param success success flag
         * @return this builder
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        /**
         * Sets the guardrails applied.
         *
         * @param guardrailsApplied list of guardrail names
         * @return this builder
         */
        public Builder guardrailsApplied(List<String> guardrailsApplied) {
            this.guardrailsApplied = guardrailsApplied;
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
         * Builds the ContentGenerationResponse.
         *
         * @return new response instance
         * @throws NullPointerException if required fields are null
         * @throws IllegalArgumentException if validation fails
         */
        public ContentGenerationResponse build() {
            return new ContentGenerationResponse(tenantId, templateId, content, success, guardrailsApplied, durationMs);
        }
    }

    @Override
    public String toString() {
        return "ContentGenerationResponse{" +
                "tenantId='" + tenantId + '\'' +
                ", templateId='" + templateId + '\'' +
                ", success=" + success +
                ", guardrails=" + guardrailsApplied.size() +
                ", durationMs=" + durationMs +
                '}';
    }
}
