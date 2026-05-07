/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.Entity;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.UUID;

/**
 * Service for CRUD operations on entities within collections.
 *
 * @doc.type interface
 * @doc.purpose Entity service abstraction for DataCloud
 * @doc.layer application
 * @doc.pattern Service
 */
public interface EntityService {

    /**
     * Creates a new entity in the given collection.
     *
     * @param tenantId the tenant ID (required)
     * @param collectionName the collection name (required)
     * @param data the entity data (required)
     * @param userId the user creating the entity (required)
     * @return Promise of the created Entity
     */
    Promise<Entity> createEntity(String tenantId, String collectionName, Map<String, Object> data, String userId);

    /**
     * Updates an existing entity.
     *
     * @param tenantId the tenant ID (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @param data the updated data (required)
     * @param userId the user performing the update (required)
     * @return Promise of the updated Entity
     */
    Promise<Entity> updateEntity(String tenantId, String collectionName, UUID entityId, Map<String, Object> data, String userId);

    /**
     * Retrieves an entity by ID.
     *
     * @param tenantId the tenant ID (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @return Promise of the Entity
     */
    Promise<Entity> getEntity(String tenantId, String collectionName, UUID entityId);

    /**
     * Deletes an entity by ID.
     *
     * @param tenantId the tenant ID (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @param userId the user performing the deletion (required)
     * @return Promise of void
     */
    Promise<Void> deleteEntity(String tenantId, String collectionName, UUID entityId, String userId);
}
