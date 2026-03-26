package com.ghatana.datacloud.api.dto;

import com.ghatana.datacloud.entity.MetaCollection;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for collection data.
 *
 * <p><b>Purpose</b><br>
 * Strongly-typed response payload for collection retrieval. Provides a stable API contract
 * independent of domain model changes. Includes all collection metadata and audit fields.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetaCollection domain = repository.findById(id);
 * CollectionResponse response = CollectionResponse.from(domain);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param id Collection unique identifier
 * @param tenantId Tenant identifier
 * @param name Collection name (unique per tenant)
 * @param label Human-readable label
 * @param description Collection description
 * @param permission RBAC permission map
 * @param applications Application visibility configuration
 * @param validationSchema JSON Schema for entity validation
 * @param active Active status (soft delete flag)
 * @param createdAt Creation timestamp
 * @param updatedAt Last update timestamp
 * @param createdBy User who created the collection
 * @param updatedBy User who last updated the collection
 *
 * @see com.ghatana.datacloud.entity.MetaCollection
 * @see com.ghatana.datacloud.api.http.CollectionApiController
 * @doc.type record
 * @doc.purpose Response DTO for collection data
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 */
public record CollectionResponse(
    UUID id,
    String tenantId,
    String name,
    String label,
    String description,
    Map<String, List<String>> permission,
    List<Map<String, Object>> applications,
    Map<String, Object> validationSchema,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy
) {
    /**
     * Converts a domain MetaCollection to a response DTO.
     *
     * <p>Maps all fields from the domain model to the API response format.
     * Ensures consistent serialization and API stability.
     *
     * @param collection domain collection entity
     * @return response DTO
     * @throws NullPointerException if collection is null
     */
    public static CollectionResponse from(MetaCollection collection) {
        if (collection == null) {
            throw new NullPointerException("MetaCollection must not be null");
        }

        return new CollectionResponse(
            collection.getId(),
            collection.getTenantId(),
            collection.getName(),
            collection.getLabel(),
            collection.getDescription(),
            collection.getPermission(),
            collection.getApplications(),
            collection.getValidationSchema(),
            collection.getActive(),
            collection.getCreatedAt(),
            collection.getUpdatedAt(),
            collection.getCreatedBy(),
            collection.getUpdatedBy()
        );
    }

    /**
     * Converts a list of domain collections to response DTOs.
     *
     * @param collections domain collection entities
     * @return list of response DTOs
     */
    public static List<CollectionResponse> fromList(List<MetaCollection> collections) {
        if (collections == null) {
            return List.of();
        }
        return collections.stream()
            .map(CollectionResponse::from)
            .toList();
    }
}
