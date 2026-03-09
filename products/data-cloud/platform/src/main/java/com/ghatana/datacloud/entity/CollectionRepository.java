package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for MetaCollection persistence operations.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for collection metadata persistence with ActiveJ Promise-based async operations.
 * All operations are tenant-scoped and return ActiveJ Promises for non-blocking execution on the eventloop.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CollectionRepository repo = new JpaCollectionRepositoryImpl(entityManager);
 * Promise<MetaCollection> promise = repo.findByName("tenant-123", "products");
 * promise.then(collection -> {
 *     // Process collection
 *     return collection;
 * });
 * }</pre>
 *
 * <p><b>Architecture</b><br>
 * - Interface in domain layer (clean architecture)
 * - Implementation in infrastructure layer
 * - Used by application layer services
 * - All methods return ActiveJ Promises for async execution
 *
 * <p><b>RBAC & Multi-tenancy</b><br>
 * - All operations require tenantId parameter
 * - Implementations should enforce tenant isolation
 * - Access control should be applied at implementation level
 *
 * @author Collection System Team
 * @version 1.0
 * @since 2024-01-01
 * @see MetaCollection
 * @doc.type interface
 * @doc.purpose Repository contract for collection persistence
 * @doc.layer domain
 * @doc.pattern Repository (Domain Layer)
 */
public interface CollectionRepository {

    /**
     * Finds a collection by tenant ID and name.
     *
     * @param tenantId the tenant ID (required)
     * @param name the collection name (required)
     * @return Promise containing the collection if found, empty otherwise
     * @throws IllegalArgumentException if tenantId or name is null/empty
     */
    Promise<Optional<MetaCollection>> findByName(String tenantId, String name);

    /**
     * Finds a collection by its unique ID.
     *
     * @param tenantId the tenant ID (required)
     * @param id the collection ID (required)
     * @return Promise containing the collection if found, empty otherwise
     * @throws IllegalArgumentException if tenantId or id is null
     */
    Promise<Optional<MetaCollection>> findById(String tenantId, UUID id);

    /**
     * Lists all collections for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing list of all collections for the tenant
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    Promise<List<MetaCollection>> findAll(String tenantId);

    /**
     * Lists all collections for a tenant (alias for findAll).
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing list of all collections for the tenant
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    default Promise<List<MetaCollection>> findAllByTenant(String tenantId) {
        return findAll(tenantId);
    }

    /**
     * Finds all unique tenant IDs that have collections.
     *
     * <p>Used by cache warm-up service to discover all tenants for pre-loading.
     *
     * @return Promise containing list of tenant IDs
     */
    Promise<List<String>> findAllTenantIds();

    /**
     * Saves a collection (create or update).
     *
     * @param tenantId the tenant ID (required)
     * @param collection the collection to save (required)
     * @return Promise containing the saved collection with generated ID if new
     * @throws IllegalArgumentException if tenantId or collection is null
     */
    Promise<MetaCollection> save(String tenantId, MetaCollection collection);

    /**
     * Deletes a collection by ID.
     *
     * @param tenantId the tenant ID (required)
     * @param id the collection ID to delete (required)
     * @return Promise containing true if deleted, false if not found
     * @throws IllegalArgumentException if tenantId or id is null
     */
    Promise<Boolean> delete(String tenantId, UUID id);

    /**
     * Checks if a collection exists by name.
     *
     * @param tenantId the tenant ID (required)
     * @param name the collection name (required)
     * @return Promise containing true if exists, false otherwise
     * @throws IllegalArgumentException if tenantId or name is null/empty
     */
    Promise<Boolean> existsByName(String tenantId, String name);

    /**
     * Counts collections for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing the count of collections
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    Promise<Long> count(String tenantId);
}
