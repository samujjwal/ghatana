package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared CRUD contract for structured data storage backends.
 *
 * <p>This interface captures the entity CRUD operations that embedded and
 * launcher-owned runtime code need without pulling in the platform plugin
 * contract. Plugin-backed implementations should expose this contract via
 * {@link DataStoragePlugin}.
 *
 * @doc.type interface
 * @doc.purpose Shared CRUD contract for structured data storage
 * @doc.layer core
 * @doc.pattern Port, Strategy
 */
public interface DataStorageOperations {

    /**
     * Creates a new entity in the specified collection.
     *
     * @param tenantId Tenant identifier for multi-tenancy
     * @param collectionName Collection/table name
     * @param data Entity data (fields and values)
     * @return Promise with created entity
     */
    Promise<EntityInterface> create(String tenantId, String collectionName, Map<String, Object> data);

    /**
     * Reads an entity by its ID.
     *
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param entityId Entity unique identifier
     * @return Promise with Optional containing entity if found
     */
    Promise<Optional<EntityInterface>> read(String tenantId, String collectionName, UUID entityId);

    /**
     * Updates an existing entity.
     *
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param entityId Entity unique identifier
     * @param updates Fields to update
     * @return Promise with updated entity
     */
    Promise<EntityInterface> update(String tenantId, String collectionName, UUID entityId, Map<String, Object> updates);

    /**
     * Deletes an entity.
     *
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param entityId Entity unique identifier
     * @return Promise completing when deleted
     */
    Promise<Void> delete(String tenantId, String collectionName, UUID entityId);

    /**
     * Queries entities with filtering, sorting, and pagination.
     *
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param query Query specification
     * @return Promise with matching entities
     */
    Promise<List<EntityInterface>> query(String tenantId, String collectionName, QuerySpecInterface query);

    /**
     * Counts entities matching the query.
     *
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param query Query specification
     * @return Promise with count of matching entities
     */
    Promise<Long> count(String tenantId, String collectionName, QuerySpecInterface query);
}