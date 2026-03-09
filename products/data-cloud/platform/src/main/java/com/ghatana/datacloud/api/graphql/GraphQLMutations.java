/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.graphql;

import com.ghatana.datacloud.application.CollectionService;
import com.ghatana.datacloud.application.EntityService;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.MetaCollection;
import io.activej.promise.Promise;

import java.util.*;

/**
 * GraphQL mutation resolvers for entity and collection operations.
 *
 * <p>Provides CRUD operations for entities and collections, with input validation,
 * UUID parsing, and response mapping suitable for GraphQL responses.
 *
 * @doc.type class
 * @doc.purpose GraphQL mutation handler for DataCloud
 * @doc.layer api
 * @doc.pattern Resolver
 */
public class GraphQLMutations {

    private final EntityService entityService;
    private final CollectionService collectionService;
    private final CollectionRepository collectionRepository;

    public GraphQLMutations(EntityService entityService,
                            CollectionService collectionService,
                            CollectionRepository collectionRepository) {
        this.entityService = Objects.requireNonNull(entityService);
        this.collectionService = Objects.requireNonNull(collectionService);
        this.collectionRepository = Objects.requireNonNull(collectionRepository);
    }

    // ==================== Entity CRUD ====================

    /**
     * Creates a new entity in a collection.
     *
     * @param tenantId the tenant ID (required, non-blank)
     * @param collectionName the collection name (required)
     * @param data the entity data (required, non-empty)
     * @param userId the creating user (required)
     * @return Promise of entity map representation
     */
    public Promise<Map<String, Object>> createEntity(String tenantId, String collectionName,
                                                      Map<String, Object> data, String userId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be empty");
        }
        Objects.requireNonNull(collectionName, "collectionName is required");
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("data cannot be empty");
        }
        Objects.requireNonNull(userId, "userId is required");

        return entityService.createEntity(tenantId, collectionName, data, userId)
                .map(this::entityToMap);
    }

    /**
     * Updates an existing entity.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param entityId the entity ID as string (UUID format)
     * @param data the updated data
     * @param userId the updating user
     * @return Promise of updated entity map
     */
    public Promise<Map<String, Object>> updateEntity(String tenantId, String collectionName,
                                                      String entityId, Map<String, Object> data,
                                                      String userId) {
        UUID id = parseUUID(entityId);
        return entityService.updateEntity(tenantId, collectionName, id, data, userId)
                .map(this::entityToMap);
    }

    /**
     * Deletes an entity by ID.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param entityId the entity ID as string (UUID format)
     * @param userId the deleting user
     * @return Promise of true on success
     */
    public Promise<Boolean> deleteEntity(String tenantId, String collectionName,
                                          String entityId, String userId) {
        UUID id = parseUUID(entityId);
        return entityService.deleteEntity(tenantId, collectionName, id, userId)
                .map(v -> true);
    }

    // ==================== Collection CRUD ====================

    /**
     * Creates a new collection.
     *
     * @param tenantId the tenant ID
     * @param name the collection name (required, non-blank)
     * @param description the collection description
     * @param userId the creating user
     * @return Promise of collection map representation
     */
    public Promise<Map<String, Object>> createCollection(String tenantId, String name,
                                                          String description, String userId) {
        Objects.requireNonNull(name, "name is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be empty");
        }

        MetaCollection collection = MetaCollection.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name(name)
                .label(name)
                .description(description)
                .fields(Collections.emptyList())
                .permission(Collections.emptyMap())
                .build();

        return collectionService.createCollection(tenantId, collection, userId)
                .map(this::collectionToMap);
    }

    /**
     * Updates a collection's description by name.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name to find
     * @param description the new description
     * @param userId the updating user
     * @return Promise of updated collection map
     */
    public Promise<Map<String, Object>> updateCollection(String tenantId, String collectionName,
                                                          String description, String userId) {
        return collectionRepository.findByName(tenantId, collectionName)
                .map(opt -> opt.orElseThrow(() ->
                    new IllegalStateException("Collection not found: " + collectionName)))
                .then(existing -> {
                    existing.setDescription(description);
                    return collectionService.updateCollection(tenantId, existing, userId);
                })
                .map(this::collectionToMap);
    }

    /**
     * Deletes a collection by name.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name to find
     * @param userId the deleting user
     * @return Promise of true on success
     */
    public Promise<Boolean> deleteCollection(String tenantId, String collectionName, String userId) {
        return collectionRepository.findByName(tenantId, collectionName)
                .map(opt -> opt.orElseThrow(() ->
                    new IllegalStateException("Collection not found: " + collectionName)))
                .then(existing ->
                    collectionService.deleteCollection(tenantId, existing.getId(), userId)
                        .map(v -> true));
    }

    // ==================== Helper Methods ====================

    private UUID parseUUID(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid entity ID format: " + id, e);
        }
    }

    private Map<String, Object> entityToMap(Entity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (entity != null) {
            map.put("id", entity.getId() != null ? entity.getId().toString() : null);
            map.put("tenantId", entity.getTenantId());
            map.put("collectionName", entity.getCollectionName());
            map.put("data", entity.getData());
            if (entity.getMetadata() != null) {
                map.put("metadata", entity.getMetadata());
            }
            if (entity.getCreatedAt() != null) {
                map.put("createdAt", entity.getCreatedAt().toString());
            }
            if (entity.getUpdatedAt() != null) {
                map.put("updatedAt", entity.getUpdatedAt().toString());
            }
            map.put("version", entity.getVersion());
        }
        return map;
    }

    private Map<String, Object> collectionToMap(MetaCollection collection) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (collection != null) {
            map.put("id", collection.getId() != null ? collection.getId().toString() : null);
            map.put("tenantId", collection.getTenantId());
            map.put("name", collection.getName());
            map.put("description", collection.getDescription());
            if (collection.getCreatedAt() != null) {
                map.put("createdAt", collection.getCreatedAt().toString());
            }
        }
        return map;
    }
}
