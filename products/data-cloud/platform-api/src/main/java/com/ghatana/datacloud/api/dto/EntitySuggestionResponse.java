package com.ghatana.datacloud.api.dto;

import com.ghatana.datacloud.entity.EntitySuggestion;

/**
 * Response containing AI-generated entity suggestion.
 *
 * <p><b>Purpose</b><br>
 * Wraps EntitySuggestion domain object for REST API response with proper serialization.
 * Provides DTO layer between domain model and HTTP transport.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // API responds:
 * HTTP 200 OK
 * {
 *   "suggestedData": {
 *     "name": "Gaming Laptop",
 *     "gpu": "RTX 4090",
 *     "ram": "32GB",
 *     "storage": "1TB SSD",
 *     "category": "Computers"
 *   },
 *   "confidence": 0.92,
 *   "reasoning": [
 *     "Extracted product type from description",
 *     "Inferred GPU model from specification",
 *     "Matched all key attributes"
 *   ]
 * }
 * }</pre>
 *
 * @param suggestion Underlying EntitySuggestion domain object
 * @doc.type record
 * @doc.purpose Response DTO for entity suggestion endpoint
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 * @since 1.0.0
 */
public record EntitySuggestionResponse(
    EntitySuggestion suggestion
) {

    /**
     * Creates response from domain suggestion.
     *
     * @param suggestion Domain suggestion object (required)
     * @throws NullPointerException if suggestion is null
     */
    public EntitySuggestionResponse {
        if (suggestion == null) {
            throw new NullPointerException("suggestion cannot be null");
        }
    }

    /**
     * Gets suggested data from domain object.
     * 
     * @return Map of suggested field names to values
     */
    public java.util.Map<String, Object> suggestedData() {
        return suggestion.suggestedData();
    }

    /**
     * Gets confidence score (0.0-1.0).
     * 
     * @return Confidence between 0 and 1
     */
    public double confidence() {
        return suggestion.confidence();
    }

    /**
     * Gets reasoning explanation list.
     * 
     * @return List of reasoning strings
     */
    public java.util.List<String> reasoning() {
        return suggestion.reasoning();
    }
}
