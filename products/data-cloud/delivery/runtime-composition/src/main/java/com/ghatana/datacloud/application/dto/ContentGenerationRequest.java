package com.ghatana.datacloud.application.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Request DTO for content generation operations.
 *
 * <p>Contains template ID and variable substitutions for content generation.
 * Template and variables must exist and be valid before processing.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ContentGenerationRequest request = ContentGenerationRequest.builder()
 *     .tenantId("tenant-123")
 *     .templateId("email-welcome")
 *     .variables(Map.of("firstName", "John", "companyName", "Acme"))
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Request transfer object for content generation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ContentGenerationRequest {
    private final String tenantId;
    private final String templateId;
    private final Map<String, String> variables;

    /**
     * Constructs a new content generation request.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param templateId template identifier (non-blank)
     * @param variables template variables map (non-empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private ContentGenerationRequest(String tenantId, String templateId, Map<String, String> variables) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.templateId = Objects.requireNonNull(templateId, "templateId cannot be null");
        this.variables = Objects.requireNonNull(variables, "variables cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId cannot be blank");
        }
        if (variables.isEmpty()) {
            throw new IllegalArgumentException("variables cannot be empty");
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
     * Gets the template identifier.
     *
     * @return template ID (non-blank)
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * Gets the template variables.
     *
     * @return variables map (non-empty)
     */
    public Map<String, String> getVariables() {
        return variables;
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
     * Builder for ContentGenerationRequest.
     *
     * @see ContentGenerationRequest
     */
    public static class Builder {
        private String tenantId;
        private String templateId;
        private Map<String, String> variables;

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
         * Sets the template variables.
         *
         * @param variables variable substitution map
         * @return this builder
         */
        public Builder variables(Map<String, String> variables) {
            this.variables = variables;
            return this;
        }

        /**
         * Builds the ContentGenerationRequest.
         *
         * @return new request instance
         * @throws NullPointerException if required fields are null
         * @throws IllegalArgumentException if validation fails
         */
        public ContentGenerationRequest build() {
            return new ContentGenerationRequest(tenantId, templateId, variables);
        }
    }

    @Override
    public String toString() {
        return "ContentGenerationRequest{" +
                "tenantId='" + tenantId + '\'' +
                ", templateId='" + templateId + '\'' +
                ", variables.size=" + variables.size() +
                '}';
    }
}
