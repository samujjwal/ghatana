package com.ghatana.datacloud.api.dto;

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
    String collectionName,
    String description
) {

    /**
     * Validates request parameters.
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
