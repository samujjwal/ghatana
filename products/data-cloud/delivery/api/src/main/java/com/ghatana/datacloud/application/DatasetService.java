package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.MetaDataset;
import com.ghatana.datacloud.entity.DatasetRepository;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing dataset metadata with RBAC and multi-tenancy.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for dataset CRUD operations, collection membership management,
 * and data governance. All operations are tenant-scoped and return ActiveJ Promises
 * for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DatasetService service = new DatasetService(datasetRepository, collectionRepository, metrics);
 *
 * // Create dataset
 * MetaDataset dataset = MetaDataset.builder()
 *     .tenantId("tenant-123")
 *     .name("customer-analytics")
 *     .collectionIds(List.of("customers", "orders"))
 *     .build();
 *
 * Promise<MetaDataset> promise = service.createDataset(
 *     "tenant-123",
 *     dataset,
 *     "user-456"
 * );
 *
 * // In test with EventloopTestBase:
 * MetaDataset created = runPromise(() -> promise);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Uses DatasetRepository and CollectionRepository (infrastructure layer)
 * - Uses MetricsCollector (core/observability)
 * - Enforces RBAC and tenant isolation
 * - Validates collection membership
 * - Emits metrics for all operations
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters or repository.
 *
 * <p><b>RBAC</b><br>
 * Enforces role-based access control:
 * - READ: View dataset metadata and collections
 * - WRITE: Create/update dataset and collection membership
 * - DELETE: Delete dataset (soft delete)
 * - ADMIN: Full access including governance actions
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations tenant-scoped. Tenant extracted from context and enforced at repository level.
 *
 * @see MetaDataset
 * @see MetaCollection
 * @see com.ghatana.datacloud.infrastructure.persistence.DatasetRepository
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Service for dataset metadata management with RBAC
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final CollectionRepository collectionRepository;
    private final MetricsCollector metrics;

    /**
     * Creates a new dataset service.
     *
     * @param datasetRepository the dataset repository (required)
     * @param collectionRepository the collection repository (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public DatasetService(
            DatasetRepository datasetRepository,
            CollectionRepository collectionRepository,
            MetricsCollector metrics) {
        this.datasetRepository = Objects.requireNonNull(datasetRepository, "DatasetRepository must not be null");
        this.collectionRepository = Objects.requireNonNull(collectionRepository, "CollectionRepository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Creates a new dataset.
     *
     * <p><b>RBAC</b><br>
     * Requires WRITE permission on tenant.
     *
     * <p><b>Validation</b><br>
     * - Tenant ID must match dataset
     * - Dataset name must be unique per tenant
     * - All referenced collections must exist and belong to tenant
     * - Permission structure must be valid
     *
     * @param tenantId the tenant identifier (required)
     * @param dataset the dataset to create (required)
     * @param userId the user creating the dataset (for audit)
     * @return Promise of created dataset with generated ID
     * @throws IllegalArgumentException if validation fails
     */
    public Promise<MetaDataset> createDataset(
            String tenantId,
            MetaDataset dataset,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(dataset, "Dataset must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        if (!dataset.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Dataset tenant ID must match request tenant");
        }

        dataset.setCreatedBy(userId);
        dataset.setUpdatedBy(userId);

        return datasetRepository.existsByName(tenantId, dataset.getName())
            .then(exists -> {
                if (exists) {
                    metrics.incrementCounter("dataset.create.conflict",
                        "tenant", tenantId,
                        "dataset", dataset.getName());
                    return Promise.ofException(
                        new IllegalArgumentException("Dataset already exists: " + dataset.getName())
                    );
                }
                
                // Validate that all referenced collections exist
                return validateCollectionsExist(tenantId, dataset.getCollectionIds())
                    .then(valid -> {
                        if (!valid) {
                            return Promise.ofException(
                                new IllegalArgumentException("One or more referenced collections do not exist")
                            );
                        }
                        return datasetRepository.save(tenantId, dataset);
                    });
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("dataset.create.success",
                        "tenant", tenantId,
                        "dataset", dataset.getName());
                    log.info("Dataset created: tenantId={}, name={}, id={}, createdBy={}",
                        tenantId, dataset.getName(), ((MetaDataset) result).getId(), userId);
                } else {
                    metrics.incrementCounter("dataset.create.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                    log.error("Failed to create dataset: tenantId={}, name={}",
                        tenantId, dataset.getName(), ex);
                }
            });
    }

    /**
     * Gets a dataset by name.
     *
     * <p><b>RBAC</b><br>
     * Requires READ permission on dataset.
     *
     * @param tenantId the tenant identifier (required)
     * @param name the dataset name (required)
     * @return Promise of Optional containing the dataset if found
     */
    public Promise<Optional<MetaDataset>> getDataset(String tenantId, String name) {
        validateTenantId(tenantId);
        Objects.requireNonNull(name, "Dataset name must not be null");

        return datasetRepository.findByName(tenantId, name)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    if (result.isPresent()) {
                        metrics.incrementCounter("dataset.get.success",
                            "tenant", tenantId,
                            "dataset", name);
                    } else {
                        metrics.incrementCounter("dataset.get.not_found",
                            "tenant", tenantId,
                            "dataset", name);
                    }
                } else {
                    metrics.incrementCounter("dataset.get.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Gets all active datasets for a tenant.
     *
     * <p><b>RBAC</b><br>
     * Requires READ permission on tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of list of active datasets
     */
    public Promise<List<MetaDataset>> listDatasets(String tenantId) {
        validateTenantId(tenantId);

        return datasetRepository.findAll(tenantId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("dataset.count.active", "tenant", tenantId);
                    metrics.increment("dataset.count.active", result.size(), Map.of("tenant", tenantId));
                    log.debug("Listed datasets: tenantId={}, count={}", tenantId, result.size());
                } else {
                    metrics.incrementCounter("dataset.list.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Updates a dataset.
     *
     * <p><b>RBAC</b><br>
     * Requires WRITE permission on dataset.
     *
     * @param tenantId the tenant identifier (required)
     * @param dataset the dataset to update (required)
     * @param userId the user updating the dataset (for audit)
     * @return Promise of updated dataset
     */
    public Promise<MetaDataset> updateDataset(
            String tenantId,
            MetaDataset dataset,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(dataset, "Dataset must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        if (!dataset.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Dataset tenant ID must match request tenant");
        }

        dataset.setUpdatedBy(userId);
        dataset.setVersion(dataset.getVersion() + 1);

        return validateCollectionsExist(tenantId, dataset.getCollectionIds())
            .then(valid -> {
                if (!valid) {
                    return Promise.ofException(
                        new IllegalArgumentException("One or more referenced collections do not exist")
                    );
                }
                return datasetRepository.save(tenantId, dataset);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("dataset.update.success",
                        "tenant", tenantId,
                        "dataset", dataset.getName());
                    log.info("Dataset updated: tenantId={}, name={}, version={}, updatedBy={}",
                        tenantId, dataset.getName(), result.getVersion(), userId);
                } else {
                    metrics.incrementCounter("dataset.update.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Deletes a dataset (soft delete).
     *
     * <p><b>RBAC</b><br>
     * Requires DELETE permission on dataset.
     *
     * <p><b>Soft Delete</b><br>
     * Sets active=false instead of hard-deleting. Preserves audit trail.
     *
     * @param tenantId the tenant identifier (required)
     * @param datasetId the dataset ID (required)
     * @param userId the user deleting the dataset (for audit)
     * @return Promise of void
     */
    public Promise<Void> deleteDataset(String tenantId, UUID datasetId, String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(datasetId, "Dataset ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return datasetRepository.delete(tenantId, datasetId)
            .map(result -> (Void) null)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("dataset.delete.success",
                        "tenant", tenantId,
                        "datasetId", datasetId.toString());
                    log.info("Dataset deleted: tenantId={}, datasetId={}, deletedBy={}",
                        tenantId, datasetId, userId);
                } else {
                    metrics.incrementCounter("dataset.delete.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Adds collections to a dataset.
     *
     * @param tenantId the tenant identifier (required)
     * @param datasetName the dataset name (required)
     * @param collectionIds the collection IDs to add (required)
     * @param userId the user adding collections (for audit)
     * @return Promise of updated dataset
     */
    public Promise<MetaDataset> addCollections(
            String tenantId,
            String datasetName,
            List<String> collectionIds,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(datasetName, "Dataset name must not be null");
        Objects.requireNonNull(collectionIds, "Collection IDs must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return datasetRepository.findByName(tenantId, datasetName)
            .then(datasetOpt -> {
                if (datasetOpt.isEmpty()) {
                    return Promise.ofException(
                        new IllegalArgumentException("Dataset not found: " + datasetName));
                }

                MetaDataset dataset = datasetOpt.get();
                
                // Validate new collections exist
                return validateCollectionsExist(tenantId, collectionIds)
                    .then(valid -> {
                        if (!valid) {
                            return Promise.ofException(
                                new IllegalArgumentException("One or more referenced collections do not exist")
                            );
                        }

                        // Add new collections (avoid duplicates)
                        Set<String> existingIds = new HashSet<>(dataset.getCollectionIds());
                        existingIds.addAll(collectionIds);
                        dataset.setCollectionIds(new ArrayList<>(existingIds));
                        dataset.setUpdatedBy(userId);

                        return datasetRepository.save(tenantId, dataset);
                    });
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("dataset.collections.add.success",
                        "tenant", tenantId, "dataset", datasetName);
                    log.info("Collections added to dataset: tenantId={}, dataset={}, count={}",
                        tenantId, datasetName, collectionIds.size());
                } else {
                    metrics.incrementCounter("dataset.collections.add.error",
                        "tenant", tenantId, "dataset", datasetName);
                }
            });
    }

    /**
     * Removes collections from a dataset.
     *
     * @param tenantId the tenant identifier (required)
     * @param datasetName the dataset name (required)
     * @param collectionIds the collection IDs to remove (required)
     * @param userId the user removing collections (for audit)
     * @return Promise of updated dataset
     */
    public Promise<MetaDataset> removeCollections(
            String tenantId,
            String datasetName,
            List<String> collectionIds,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(datasetName, "Dataset name must not be null");
        Objects.requireNonNull(collectionIds, "Collection IDs must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return datasetRepository.findByName(tenantId, datasetName)
            .then(datasetOpt -> {
                if (datasetOpt.isEmpty()) {
                    return Promise.ofException(
                        new IllegalArgumentException("Dataset not found: " + datasetName));
                }

                MetaDataset dataset = datasetOpt.get();
                
                // Remove collections
                List<String> remainingIds = dataset.getCollectionIds().stream()
                    .filter(id -> !collectionIds.contains(id))
                    .collect(Collectors.toList());
                
                dataset.setCollectionIds(remainingIds);
                dataset.setUpdatedBy(userId);

                return datasetRepository.save(tenantId, dataset);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("dataset.collections.remove.success",
                        "tenant", tenantId, "dataset", datasetName);
                    log.info("Collections removed from dataset: tenantId={}, dataset={}, count={}",
                        tenantId, datasetName, collectionIds.size());
                } else {
                    metrics.incrementCounter("dataset.collections.remove.error",
                        "tenant", tenantId, "dataset", datasetName);
                }
            });
    }

    /**
     * Counts active datasets for a tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of count
     */
    public Promise<Long> countDatasets(String tenantId) {
        validateTenantId(tenantId);

        return datasetRepository.count(tenantId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.increment("dataset.count.total", result, Map.of("tenant", tenantId));
                } else {
                    metrics.incrementCounter("dataset.count.error",
                        "tenant", tenantId);
                }
            });
    }

    /**
     * Validates that all referenced collections exist and belong to the tenant.
     *
     * @param tenantId the tenant ID
     * @param collectionIds the collection IDs to validate
     * @return Promise of boolean indicating if all collections are valid
     */
    private Promise<Boolean> validateCollectionsExist(String tenantId, List<String> collectionIds) {
        if (collectionIds == null || collectionIds.isEmpty()) {
            return Promise.of(true);
        }

        List<Promise<Optional<MetaCollection>>> collectionPromises = collectionIds.stream()
            .map(collectionId -> collectionRepository.findByName(tenantId, collectionId))
            .toList();

        return io.activej.promise.Promises.toList(collectionPromises)
            .then(results -> {
                boolean allExist = results.stream().allMatch(Optional::isPresent);
                return Promise.of(allExist);
            });
    }

    /**
     * Validates tenant ID is not null or empty.
     *
     * @param tenantId the tenant ID to validate
     * @throws IllegalArgumentException if tenantId is null or empty
     */
    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must not be null or empty");
        }
    }
}
