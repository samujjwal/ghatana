package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for MetaDataSource persistence operations.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for data source metadata persistence with ActiveJ Promise-based async operations.
 * All operations are tenant-scoped and return ActiveJ Promises for non-blocking execution on the eventloop.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DataSourceRepository repo = new JpaDataSourceRepositoryImpl(entityManager);
 * Promise<MetaDataSource> promise = repo.findByName("tenant-123", "postgres-orders");
 * promise.then(dataSource -> {
 *     // Process data source
 *     return dataSource;
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
 * @see MetaDataSource
 * @doc.type interface
 * @doc.purpose Repository contract for data source persistence
 * @doc.layer domain
 * @doc.pattern Repository (Domain Layer)
 */
public interface DataSourceRepository {

    /**
     * Finds a data source by tenant ID and name.
     *
     * @param tenantId the tenant ID (required)
     * @param name the data source name (required)
     * @return Promise containing the data source if found, empty otherwise
     * @throws IllegalArgumentException if tenantId or name is null/empty
     */
    Promise<Optional<MetaDataSource>> findByName(String tenantId, String name);

    /**
     * Finds a data source by its unique ID.
     *
     * @param tenantId the tenant ID (required)
     * @param id the data source ID (required)
     * @return Promise containing the data source if found, empty otherwise
     * @throws IllegalArgumentException if tenantId or id is null
     */
    Promise<Optional<MetaDataSource>> findById(String tenantId, UUID id);

    /**
     * Lists all data sources for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing list of all data sources for the tenant
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    Promise<List<MetaDataSource>> findAll(String tenantId);

    /**
     * Lists all data sources for a tenant (alias for findAll).
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing list of all data sources for the tenant
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    default Promise<List<MetaDataSource>> findAllByTenant(String tenantId) {
        return findAll(tenantId);
    }

    /**
     * Finds all unique tenant IDs that have data sources.
     *
     * <p>Used by cache warm-up service to discover all tenants for pre-loading.
     *
     * @return Promise containing list of tenant IDs
     */
    Promise<List<String>> findAllTenantIds();

    /**
     * Finds data sources by type.
     *
     * @param tenantId the tenant ID (required)
     * @param type the data source type to search for (required)
     * @return Promise containing list of data sources of the specified type
     * @throws IllegalArgumentException if tenantId or type is null
     */
    Promise<List<MetaDataSource>> findByType(String tenantId, MetaDataSource.DataSourceType type);

    /**
     * Finds data sources by connection status.
     *
     * @param tenantId the tenant ID (required)
     * @param status the connection status to search for (required)
     * @return Promise containing list of data sources with the specified status
     * @throws IllegalArgumentException if tenantId or status is null/empty
     */
    Promise<List<MetaDataSource>> findByConnectionStatus(String tenantId, String status);

    /**
     * Finds data sources by target collection.
     *
     * @param tenantId the tenant ID (required)
     * @param targetCollection the target collection to search for (required)
     * @return Promise containing list of data sources targeting the specified collection
     * @throws IllegalArgumentException if tenantId or targetCollection is null/empty
     */
    Promise<List<MetaDataSource>> findByTargetCollection(String tenantId, String targetCollection);

    /**
     * Saves a data source (create or update).
     *
     * @param tenantId the tenant ID (required)
     * @param dataSource the data source to save (required)
     * @return Promise containing the saved data source with generated ID if new
     * @throws IllegalArgumentException if tenantId or dataSource is null
     */
    Promise<MetaDataSource> save(String tenantId, MetaDataSource dataSource);

    /**
     * Deletes a data source by ID.
     *
     * @param tenantId the tenant ID (required)
     * @param id the data source ID to delete (required)
     * @return Promise containing true if deleted, false if not found
     * @throws IllegalArgumentException if tenantId or id is null
     */
    Promise<Boolean> delete(String tenantId, UUID id);

    /**
     * Checks if a data source exists by name.
     *
     * @param tenantId the tenant ID (required)
     * @param name the data source name (required)
     * @return Promise containing true if exists, false otherwise
     * @throws IllegalArgumentException if tenantId or name is null/empty
     */
    Promise<Boolean> existsByName(String tenantId, String name);

    /**
     * Counts data sources for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing the count of data sources
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    Promise<Long> count(String tenantId);

    /**
     * Finds data sources by owner.
     *
     * @param tenantId the tenant ID (required)
     * @param owner the owner to search for (required)
     * @return Promise containing list of data sources owned by the specified owner
     * @throws IllegalArgumentException if tenantId or owner is null/empty
     */
    Promise<List<MetaDataSource>> findByOwner(String tenantId, String owner);

    /**
     * Finds data sources by tag.
     *
     * @param tenantId the tenant ID (required)
     * @param tag the tag to search for (required)
     * @return Promise containing list of data sources with the specified tag
     * @throws IllegalArgumentException if tenantId or tag is null/empty
     */
    Promise<List<MetaDataSource>> findByTag(String tenantId, String tag);
}
