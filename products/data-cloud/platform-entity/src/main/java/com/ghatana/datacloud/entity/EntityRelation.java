package com.ghatana.datacloud.entity;

import java.util.UUID;

/**
 * Represents a relationship between entities based on similarity or references.
 *
 * <p><b>Purpose</b><br>
 * Describes a related entity discovered through AI-powered similarity search
 * or explicit reference relationships.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityRelation relation = new EntityRelation(
 *     UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
 *     "products",
 *     RelationType.SIMILAR,
 *     0.87
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param relatedEntityId UUID of the related entity
 * @param collectionName Collection containing the related entity
 * @param relationType Type of relationship
 * @param similarity Similarity score (0.0 to 1.0)
 *
 * @see EntitySuggestionService
 * @see RelationType
 * @doc.type record
 * @doc.purpose Entity relationship descriptor
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record EntityRelation(
    UUID relatedEntityId,
    String collectionName,
    RelationType relationType,
    double similarity
) {
    /**
     * Creates an entity relation with validation.
     *
     * @param relatedEntityId UUID of related entity (required)
     * @param collectionName Collection name (required)
     * @param relationType Relation type (required)
     * @param similarity Similarity score (must be 0.0 to 1.0)
     * @throws IllegalArgumentException if validation fails
     */
    public EntityRelation {
        if (relatedEntityId == null) {
            throw new IllegalArgumentException("Related entity ID must not be null");
        }
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name must not be blank");
        }
        if (relationType == null) {
            throw new IllegalArgumentException("Relation type must not be null");
        }
        if (similarity < 0.0 || similarity > 1.0) {
            throw new IllegalArgumentException(
                "Similarity must be between 0.0 and 1.0, got: " + similarity
            );
        }
    }

    /**
     * Checks if the relation has high similarity.
     *
     * @return true if similarity >= 0.8
     */
    public boolean isHighSimilarity() {
        return similarity >= 0.8;
    }

    /**
     * Checks if this is a content-based similarity relation.
     *
     * @return true if relationType is SIMILAR
     */
    public boolean isSimilar() {
        return relationType == RelationType.SIMILAR;
    }

    /**
     * Checks if this is an explicit reference relation.
     *
     * @return true if relationType is REFERENCED
     */
    public boolean isReferenced() {
        return relationType == RelationType.REFERENCED;
    }
}
