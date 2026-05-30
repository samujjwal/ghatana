package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for MetaDataset persistence operations.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for dataset metadata persistence with ActiveJ Promise-based async operations.
 * All operations are tenant-scoped and return ActiveJ Promises for non-blocking execution on the eventloop.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DatasetRepository repo = new JpaDatasetRepositoryImpl(entityManager);
 * Promise<MetaDataset> promise = repo.findByName("tenant-123", "customer-analytics");
 * promise.then(dataset -> {
 *     // Process dataset
 *     return dataset;
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
 * @see MetaDataset
 * @doc.type interface
 * @doc.purpose Repository contract for dataset persistence
 * @doc.layer domain
 * @doc.pattern Repository (Domain Layer)
 */
public interface DatasetRepository {

    /**
     * Finds a dataset by tenant ID and name.
     *
     * @param tenantId the tenant ID (required)
     * @param name the dataset name (required)
     * @return Promise containing the dataset if found, empty otherwise
     * @throws IllegalArgumentException if tenantId or name is null/empty
     */
    Promise<Optional<MetaDataset>> findByName(String tenantId, String name);

    /**
     * Finds a dataset by its unique ID.
     *
     * @param tenantId the tenant ID (required)
     * @param id the dataset ID (required)
     * @return Promise containing the dataset if found, empty otherwise
     * @throws IllegalArgumentException if tenantId or id is null
     */
    Promise<Optional<MetaDataset>> findById(String tenantId, UUID id);

    /**
     * Lists all datasets for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing list of all datasets for the tenant
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    Promise<List<MetaDataset>> findAll(String tenantId);

    /**
     * Lists all datasets for a tenant (alias for findAll).
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing list of all datasets for the tenant
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    default Promise<List<MetaDataset>> findAllByTenant(String tenantId) {
        return findAll(tenantId);
    }

    /**
     * Finds all unique tenant IDs that have datasets.
     *
     * <p>Used by cache warm-up service to discover all tenants for pre-loading.
     *
     * @return Promise containing list of tenant IDs
     */
    Promise<List<String>> findAllTenantIds();

    /**
     * Finds datasets that contain a specific collection.
     *
     * @param tenantId the tenant ID (required)
     * @param collectionId the collection ID to search for (required)
     * @return Promise containing list of datasets that include the collection
     * @throws IllegalArgumentException if tenantId or collectionId is null/empty
     */
    Promise<List<MetaDataset>> findByCollectionId(String tenantId, String collectionId);

    /**
     * Saves a dataset (create or update).
     *
     * @param tenantId the tenant ID (required)
     * @param dataset the dataset to save (required)
     * @return Promise containing the saved dataset with generated ID if new
     * @throws IllegalArgumentException if tenantId or dataset is null
     */
    Promise<MetaDataset> save(String tenantId, MetaDataset dataset);

    /**
     * Deletes a dataset by ID.
     *
     * @param tenantId the tenant ID (required)
     * @param id the dataset ID to delete (required)
     * @return Promise containing true if deleted, false if not found
     * @throws IllegalArgumentException if tenantId or id is null
     */
    Promise<Boolean> delete(String tenantId, UUID id);

    /**
     * Checks if a dataset exists by name.
     *
     * @param tenantId the tenant ID (required)
     * @param name the dataset name (required)
     * @return Promise containing true if exists, false otherwise
     * @throws IllegalArgumentException if tenantId or name is null/empty
     */
    Promise<Boolean> existsByName(String tenantId, String name);

    /**
     * Counts datasets for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing the count of datasets
     * @throws IllegalArgumentException if tenantId is null/empty
     */
    Promise<Long> count(String tenantId);

    /**
     * Finds datasets by owner.
     *
     * @param tenantId the tenant ID (required)
     * @param owner the owner to search for (required)
     * @return Promise containing list of datasets owned by the specified owner
     * @throws IllegalArgumentException if tenantId or owner is null/empty
     */
    Promise<List<MetaDataset>> findByOwner(String tenantId, String owner);

    /**
     * Finds datasets by tag.
     *
     * @param tenantId the tenant ID (required)
     * @param tag the tag to search for (required)
     * @return Promise containing list of datasets with the specified tag
     * @throws IllegalArgumentException if tenantId or tag is null/empty
     */
    Promise<List<MetaDataset>> findByTag(String tenantId, String tag);
}
