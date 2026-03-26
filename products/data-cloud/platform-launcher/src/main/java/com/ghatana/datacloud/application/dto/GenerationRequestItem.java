package com.ghatana.datacloud.application.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Single generation request item for batch operations.
 *
 * <p>Contains template ID and variable substitutions for a single generation.
 * Used as part of batch generation requests.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GenerationRequestItem item = GenerationRequestItem.builder()
 *     .templateId("email-welcome")
 *     .variables(Map.of("firstName", "John"))
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Single item in batch generation request
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class GenerationRequestItem {
    private final String templateId;
    private final Map<String, String> variables;

    /**
     * Constructs a new generation request item.
     *
     * @param templateId template identifier (non-blank)
     * @param variables template variables (non-empty)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private GenerationRequestItem(String templateId, Map<String, String> variables) {
        this.templateId = Objects.requireNonNull(templateId, "templateId cannot be null");
        this.variables = Objects.requireNonNull(variables, "variables cannot be null");

        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId cannot be blank");
        }
        if (variables.isEmpty()) {
            throw new IllegalArgumentException("variables cannot be empty");
        }
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
     * Creates a builder for constructing items.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for GenerationRequestItem.
     */
    public static class Builder {
        private String templateId;
        private Map<String, String> variables;

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
         * @param variables variable map
         * @return this builder
         */
        public Builder variables(Map<String, String> variables) {
            this.variables = variables;
            return this;
        }

        /**
         * Builds the GenerationRequestItem.
         *
         * @return new item instance
         */
        public GenerationRequestItem build() {
            return new GenerationRequestItem(templateId, variables);
        }
    }

    @Override
    public String toString() {
        return "GenerationRequestItem{" +
                "templateId='" + templateId + '\'' +
                ", variables.size=" + variables.size() +
                '}';
    }
}
