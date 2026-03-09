package com.ghatana.datacloud.entity;

import java.util.List;
import java.util.Map;

/**
 * Represents an AI-generated entity suggestion.
 *
 * <p><b>Purpose</b><br>
 * Contains the suggested entity data, confidence score, and reasoning
 * for AI-assisted entity creation from natural language descriptions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntitySuggestion suggestion = new EntitySuggestion(
 *     Map.of(
 *         "name", "iPhone 15 Pro",
 *         "price", 999.99,
 *         "category", "electronics",
 *         "brand", "Apple"
 *     ),
 *     0.92,
 *     List.of(
 *         "Identified product name from description",
 *         "Inferred price from market data",
 *         "Classified as electronics based on context"
 *     )
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param suggestedData The suggested entity data conforming to collection schema
 * @param confidence AI confidence score (0.0 to 1.0)
 * @param reasoning List of reasons explaining the suggestions
 *
 * @see EntitySuggestionService
 * @doc.type record
 * @doc.purpose AI-generated entity suggestion result
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record EntitySuggestion(
    Map<String, Object> suggestedData,
    double confidence,
    List<String> reasoning
) {
    /**
     * Creates an entity suggestion with validation.
     *
     * @param suggestedData The suggested entity data
     * @param confidence AI confidence score (must be 0.0 to 1.0)
     * @param reasoning List of reasons
     * @throws IllegalArgumentException if confidence is out of range
     */
    public EntitySuggestion {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                "Confidence must be between 0.0 and 1.0, got: " + confidence
            );
        }
        if (suggestedData == null) {
            throw new IllegalArgumentException("Suggested data must not be null");
        }
        if (reasoning == null) {
            throw new IllegalArgumentException("Reasoning must not be null");
        }
        // Make defensive copies to ensure immutability
        suggestedData = Map.copyOf(suggestedData);
        reasoning = List.copyOf(reasoning);
    }

    /**
     * Checks if the suggestion has high confidence.
     *
     * @return true if confidence >= 0.8
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    /**
     * Checks if the suggestion has medium confidence.
     *
     * @return true if confidence is between 0.5 and 0.8
     */
    public boolean isMediumConfidence() {
        return confidence >= 0.5 && confidence < 0.8;
    }

    /**
     * Checks if the suggestion has low confidence.
     *
     * @return true if confidence < 0.5
     */
    public boolean isLowConfidence() {
        return confidence < 0.5;
    }
}
