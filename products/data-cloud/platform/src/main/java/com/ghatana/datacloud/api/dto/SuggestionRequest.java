package com.ghatana.datacloud.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for workflow suggestions.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates suggestion request parameters from client.
 * Validates input before processing by suggestion service.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SuggestionRequest request = new SuggestionRequest(
 *   "User wants to filter by price > 100",
 *   "field"
 * );
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * - context: required, non-blank
 * - type: required, must be valid suggestion type
 *
 * @param context the suggestion context (required)
 * @param type the suggestion type: field, transformation, validation (required)
 *
 * @doc.type record
 * @doc.purpose Suggestion request DTO
 * @doc.layer api
 * @doc.pattern Data Transfer Object (API Layer)
 */
public record SuggestionRequest(
    @NotBlank(message = "Context must not be blank")
    String context,

    @NotNull(message = "Type must not be null")
    String type
) {
    /**
     * Validates suggestion type.
     *
     * @return true if type is valid
     */
    public boolean isValidType() {
        return "field".equals(type) || "transformation".equals(type) || "validation".equals(type);
    }

    /**
     * Gets suggestion type as enum.
     *
     * @return suggestion type
     */
    public SuggestionType getSuggestionType() {
        return SuggestionType.valueOf(type.toUpperCase());
    }

    /**
     * Suggestion type enum.
     */
    public enum SuggestionType {
        FIELD("field"),
        TRANSFORMATION("transformation"),
        VALIDATION("validation");

        private final String value;

        SuggestionType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
