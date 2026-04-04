package com.ghatana.datacloud.api.dto;

import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.Entity;

/**
 * Utility for mapping between DTOs and domain models.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes conversion logic between API DTOs and domain entities. Ensures
 * consistent
 * mapping rules and reduces duplication across controllers.
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * // Request -> Domain
 * MetaCollection collection = DtoMapper.toDomain(request, tenantId, userId);
 *
 * // Domain -> Response
 * CollectionResponse response = DtoMapper.toResponse(collection);
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Stateless utility class - thread-safe.
 *
 * @see CreateCollectionRequest
 * @see UpdateCollectionRequest
 * @see CollectionResponse
 * @see CreateEntityRequest
 * @see UpdateEntityRequest
 * @see EntityResponse
 * @doc.type class
 * @doc.purpose DTO to domain mapping utility
 * @doc.layer product
 * @doc.pattern Mapper
 */
public final class DtoMapper {

    private DtoMapper() {
        // Utility class - no instantiation
    }

    /**
     * Converts a CreateCollectionRequest to a MetaCollection domain model.
     *
     * <p>
     * Sets required audit fields (tenantId, createdBy) and maps all request fields.
     * The returned entity is ready for persistence.
     *
     * @param request  create request DTO
     * @param tenantId tenant identifier (from auth context)
     * @param userId   user identifier (from auth context)
     * @return domain collection entity
     * @throws NullPointerException if any required parameter is null
     */
    public static MetaCollection toDomain(CreateCollectionRequest request, String tenantId, String userId) {
        if (request == null) {
            throw new NullPointerException("CreateCollectionRequest must not be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }

        MetaCollection collection = new MetaCollection();
        collection.setTenantId(tenantId);
        collection.setName(request.name());
        collection.setLabel(request.label());
        collection.setDescription(request.description());
        collection.setPermission(request.permission());
        collection.setApplications(request.applications());
        collection.setValidationSchema(request.validationSchema());
        collection.setCreatedBy(userId);
        collection.setUpdatedBy(userId);
        collection.setActive(true);

        return collection;
    }

    /**
     * Applies an UpdateCollectionRequest to an existing MetaCollection.
     *
     * <p>
     * Only non-null fields in the request are applied (partial update semantics).
     * Updates the updatedBy audit field.
     *
     * @param existing existing domain collection
     * @param request  update request DTO
     * @param userId   user identifier (from auth context)
     * @throws NullPointerException if any required parameter is null
     */
    public static void applyUpdate(MetaCollection existing, UpdateCollectionRequest request, String userId) {
        if (existing == null) {
            throw new NullPointerException("Existing MetaCollection must not be null");
        }
        if (request == null) {
            throw new NullPointerException("UpdateCollectionRequest must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }

        if (request.label() != null) {
            existing.setLabel(request.label());
        }
        if (request.description() != null) {
            existing.setDescription(request.description());
        }
        if (request.permission() != null) {
            existing.setPermission(request.permission());
        }
        if (request.applications() != null) {
            existing.setApplications(request.applications());
        }
        if (request.validationSchema() != null) {
            existing.setValidationSchema(request.validationSchema());
        }

        existing.setUpdatedBy(userId);
    }

    /**
     * Converts a CreateEntityRequest to an Entity domain model.
     *
     * <p>
     * Sets required audit fields and maps entity data. The returned entity is ready
     * for schema validation and persistence.
     *
     * @param request      create request DTO
     * @param collectionId parent collection identifier
     * @param tenantId     tenant identifier (from auth context)
     * @param userId       user identifier (from auth context)
     * @return domain entity
     * @throws NullPointerException if any required parameter is null
     */
    public static Entity toDomain(CreateEntityRequest request, java.util.UUID collectionId, String tenantId,
            String userId) {
        if (request == null) {
            throw new NullPointerException("CreateEntityRequest must not be null");
        }
        if (collectionId == null) {
            throw new NullPointerException("collectionId must not be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }

        Entity entity = new Entity();
        entity.setCollectionName(collectionId.toString());
        entity.setTenantId(tenantId);
        entity.setData(request.data());
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        entity.setActive(true);

        return entity;
    }

    /**
     * Applies an UpdateEntityRequest to an existing Entity.
     *
     * <p>
     * Replaces the entire data field (full update semantics). Updates the updatedBy
     * audit field.
     *
     * @param existing existing domain entity
     * @param request  update request DTO
     * @param userId   user identifier (from auth context)
     * @throws NullPointerException if any required parameter is null
     */
    public static void applyUpdate(Entity existing, UpdateEntityRequest request, String userId) {
        if (existing == null) {
            throw new NullPointerException("Existing Entity must not be null");
        }
        if (request == null) {
            throw new NullPointerException("UpdateEntityRequest must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }

        existing.setData(request.data());
        existing.setUpdatedBy(userId);
    }

    /**
     * Maps a MetaCollection domain object to a CollectionResponse DTO.
     *
     * <p>
     * This is a thin wrapper over CollectionResponse.from to allow instance-style
     * usage from controllers that receive a DtoMapper bean.
     * </p>
     */
    public CollectionResponse toCollectionResponse(MetaCollection collection) {
        return CollectionResponse.from(collection);
    }

    /**
     * Maps an Entity domain object to an EntityResponse DTO.
     *
     * <p>
     * This is a thin wrapper over EntityResponse.from to allow instance-style
     * usage from controllers that receive a DtoMapper bean.
     * </p>
     */
    public EntityResponse toEntityResponse(Entity entity) {
        return EntityResponse.from(entity);
    }
}
