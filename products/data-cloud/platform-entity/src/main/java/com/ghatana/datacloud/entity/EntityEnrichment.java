package com.ghatana.datacloud.entity;

/**
 * Represents an AI-suggested enrichment for an existing entity field.
 *
 * <p><b>Purpose</b><br>
 * Contains a suggested value for a missing or sparse entity field,
 * along with reasoning and confidence score.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityEnrichment enrichment = new EntityEnrichment(
 *     "description",
 *     "High-performance smartphone with advanced camera system",
 *     "Generated from product name and category",
 *     0.85
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param fieldName Name of the field to enrich
 * @param suggestedValue The suggested value for the field
 * @param reason Explanation for the suggestion
 * @param confidence AI confidence score (0.0 to 1.0)
 *
 * @see EntitySuggestionService
 * @doc.type record
 * @doc.purpose AI-suggested field enrichment
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record EntityEnrichment(
    String fieldName,
    Object suggestedValue,
    String reason,
    double confidence
) {
    /**
     * Creates an entity enrichment with validation.
     *
     * @param fieldName Name of the field (required)
     * @param suggestedValue The suggested value (required)
     * @param reason Explanation (required)
     * @param confidence AI confidence score (must be 0.0 to 1.0)
     * @throws IllegalArgumentException if validation fails
     */
    public EntityEnrichment {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("Field name must not be blank");
        }
        if (suggestedValue == null) {
            throw new IllegalArgumentException("Suggested value must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                "Confidence must be between 0.0 and 1.0, got: " + confidence
            );
        }
    }

    /**
     * Checks if the enrichment has high confidence.
     *
     * @return true if confidence >= 0.8
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    /**
     * Checks if the enrichment should be automatically applied.
     *
     * @return true if confidence >= 0.9 (very high confidence)
     */
    public boolean shouldAutoApply() {
        return confidence >= 0.9;
    }
}
