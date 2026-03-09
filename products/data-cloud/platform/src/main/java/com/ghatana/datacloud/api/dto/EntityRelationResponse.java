package com.ghatana.datacloud.api.dto;

import com.ghatana.datacloud.entity.EntityRelation;
import com.ghatana.datacloud.entity.RelationType;

/**
 * Response containing a related entity suggestion.
 *
 * <p><b>Purpose</b><br>
 * Wraps EntityRelation domain object for REST API response.
 * Provides semantically similar or related entities with similarity scores.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // API responds:
 * HTTP 200 OK
 * [
 *   {
 *     "relatedEntityId": "550e8400-e29b-41d4-a716-446655440001",
 *     "collectionName": "products",
 *     "relationType": "SIMILAR",
 *     "similarity": 0.87,
 *     "label": "MacBook Pro 16-inch"  // Human-readable label (fetched separately)
 *   },
 *   {
 *     "relatedEntityId": "550e8400-e29b-41d4-a716-446655440002",
 *     "collectionName": "products",
 *     "relationType": "SIMILAR",
 *     "similarity": 0.82,
 *     "label": "Dell XPS 15"
 *   }
 * ]
 * }</pre>
 *
 * @param relation Underlying EntityRelation domain object
 * @param label Human-readable label for related entity (fetched from entity)
 * @doc.type record
 * @doc.purpose Response DTO for entity relations endpoint
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 * @since 1.0.0
 */
public record EntityRelationResponse(
    EntityRelation relation,
    String label
) {

    /**
     * Creates response from domain relation and label.
     *
     * @param relation Domain relation object (required)
     * @param label Human-readable label (optional, can be null)
     * @throws NullPointerException if relation is null
     */
    public EntityRelationResponse {
        if (relation == null) {
            throw new NullPointerException("relation cannot be null");
        }
    }

    /**
     * Gets ID of related entity.
     * 
     * @return UUID of related entity
     */
    public java.util.UUID relatedEntityId() {
        return relation.relatedEntityId();
    }

    /**
     * Gets collection name of related entity.
     * 
     * @return Collection name
     */
    public String collectionName() {
        return relation.collectionName();
    }

    /**
     * Gets relation type (SIMILAR, REFERENCED, RELATED, HIERARCHICAL).
     * 
     * @return Relation type enum
     */
    public RelationType relationType() {
        return relation.relationType();
    }

    /**
     * Gets similarity score (0.0-1.0) for SIMILAR relations.
     * 
     * @return Similarity between 0 and 1
     */
    public double similarity() {
        return relation.similarity();
    }
}
