package com.ghatana.datacloud.api.dto;

import com.ghatana.datacloud.entity.Entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for entity data.
 *
 * <p><b>Purpose</b><br>
 * Strongly-typed response payload for entity retrieval. Provides a stable API contract
 * independent of domain model changes. Includes entity data and audit metadata.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Entity domain = repository.findById(id);
 * EntityResponse response = EntityResponse.from(domain);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param id Entity unique identifier
 * @param collectionId Parent collection identifier
 * @param tenantId Tenant identifier
 * @param data Entity data (JSON object)
 * @param active Active status (soft delete flag)
 * @param createdAt Creation timestamp
 * @param updatedAt Last update timestamp
 * @param createdBy User who created the entity
 * @param updatedBy User who last updated the entity
 *
 * @see com.ghatana.datacloud.entity.Entity
 * @see com.ghatana.datacloud.api.http.EntityApiController
 * @doc.type record
 * @doc.purpose Response DTO for entity data
 * @doc.layer product
 * @doc.pattern DTO (Data Transfer Object)
 */
public record EntityResponse(
    UUID id,
    UUID collectionId,
    String tenantId,
    Map<String, Object> data,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy
) {
    /**
     * Converts a domain Entity to a response DTO.
     *
     * <p>Maps all fields from the domain model to the API response format.
     * Ensures consistent serialization and API stability.
     *
     * @param entity domain entity
     * @return response DTO
     * @throws NullPointerException if entity is null
     */
    public static EntityResponse from(Entity entity) {
        if (entity == null) {
            throw new NullPointerException("Entity must not be null");
        }

        return new EntityResponse(
            entity.getId(),
            entity.getId(),  // collectionId - using id as placeholder
            entity.getTenantId(),
            entity.getData(),
            entity.getActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedBy(),
            entity.getUpdatedBy()
        );
    }

    /**
     * Converts a list of domain entities to response DTOs.
     *
     * @param entities domain entities
     * @return list of response DTOs
     */
    public static List<EntityResponse> fromList(List<Entity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
            .map(EntityResponse::from)
            .toList();
    }
}
