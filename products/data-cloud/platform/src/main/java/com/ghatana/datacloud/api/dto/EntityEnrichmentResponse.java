package com.ghatana.datacloud.api.dto;

import com.ghatana.datacloud.entity.EntityEnrichment;

/**
 * Response containing field enrichment suggestions for an entity.
 *
 * <p><b>Purpose</b><br>
 * Wraps EntityEnrichment domain object for REST API response.
 * Provides enrichment suggestions with confidence scores for client application.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // API responds:
 * HTTP 200 OK
 * {
 *   "fieldName": "description",
 *   "suggestedValue": "Professional gaming laptop with ray tracing...",
 *   "reason": "Inferred from product specs",
 *   "confidence": 0.88,
 *   "shouldAutoApply": true  // If confidence >= 0.9
 * }
 * }</pre>
 *
 * @param enrichment Underlying EntityEnrichment domain object
 * @doc.type record
 * @doc.purpose Response DTO for entity enrichment endpoint
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 * @since 1.0.0
 */
public record EntityEnrichmentResponse(
    EntityEnrichment enrichment
) {

    /**
     * Creates response from domain enrichment.
     *
     * @param enrichment Domain enrichment object (required)
     * @throws NullPointerException if enrichment is null
     */
    public EntityEnrichmentResponse {
        if (enrichment == null) {
            throw new NullPointerException("enrichment cannot be null");
        }
    }

    /**
     * Gets field name being enriched.
     * 
     * @return Field name
     */
    public String fieldName() {
        return enrichment.fieldName();
    }

    /**
     * Gets suggested value for field.
     * 
     * @return Suggested value
     */
    public Object suggestedValue() {
        return enrichment.suggestedValue();
    }

    /**
     * Gets reason for suggestion.
     * 
     * @return Explanation text
     */
    public String reason() {
        return enrichment.reason();
    }

    /**
     * Gets confidence score (0.0-1.0).
     * 
     * @return Confidence between 0 and 1
     */
    public double confidence() {
        return enrichment.confidence();
    }

    /**
     * Gets whether suggestion should be auto-applied (confidence >= 0.9).
     * 
     * @return true if high confidence, false otherwise
     */
    public boolean shouldAutoApply() {
        return enrichment.shouldAutoApply();
    }
}
