package com.ghatana.datacloud.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to generate entity suggestion from natural language description.
 *
 * <p><b>Purpose</b><br>
 * Captures input parameters for AI-assisted entity creation workflow.
 * The description is sent to LLM to generate entity fields and structure.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Client sends:
 * POST /api/{tenantId}/entities/suggest
 * {
 *   "collectionName": "products",
 *   "description": "Gaming laptop with RTX 4090, 32GB RAM, 1TB SSD"
 * }
 * }</pre>
 *
 * @param collectionName Name of target collection (required)
 * @param description Natural language description for entity generation (required, non-empty)
 * @doc.type record
 * @doc.purpose Request DTO for entity suggestion endpoint
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 * @since 1.0.0
 */
public record EntitySuggestionRequest(
    @NotBlank(message = "collectionName is required")
    @Size(min = 1, max = 255, message = "collectionName must be between 1 and 255 characters")
    String collectionName,

    @NotBlank(message = "description is required")
    @Size(min = 1, max = 4096, message = "description must be between 1 and 4096 characters")
    String description
) {

    /**
     * Validates request parameters at construction time (fail-fast, non-servlet context).
     *
     * @throws IllegalArgumentException if collectionName is blank or description is blank
     */
    public EntitySuggestionRequest {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("collectionName is required and cannot be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required and cannot be blank");
        }
    }
}
