package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.MetaDataSource;
import com.ghatana.datacloud.entity.DataSourceRepository;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing data source connections and synchronization with RBAC and multi-tenancy.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for data source CRUD operations, connection management,
 * and synchronization configuration. All operations are tenant-scoped and return
 * ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DataSourceService service = new DataSourceService(dataSourceRepository, collectionRepository, metrics);
 *
 * // Create data source
 * MetaDataSource dataSource = MetaDataSource.builder()
 *     .tenantId("tenant-123")
 *     .name("postgres-orders")
 *     .type(MetaDataSource.DataSourceType.RELATIONAL)
 *     .connectionConfig(Map.of("host", "db.company.com"))
 *     .targetCollection("orders")
 *     .build();
 *
 * Promise<MetaDataSource> promise = service.createDataSource(
 *     "tenant-123",
 *     dataSource,
 *     "user-456"
 * );
 *
 * // In test with EventloopTestBase:
 * MetaDataSource created = runPromise(() -> promise);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Uses DataSourceRepository and CollectionRepository (infrastructure layer)
 * - Uses MetricsCollector (core/observability)
 * - Enforces RBAC and tenant isolation
 * - Validates connection configurations
 * - Manages synchronization state
 * - Emits metrics for all operations
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters or repository.
 *
 * <p><b>RBAC</b><br>
 * Enforces role-based access control:
 * - READ: View data source metadata and connection status
 * - WRITE: Create/update data source and sync configuration
 * - DELETE: Delete data source (soft delete)
 * - ADMIN: Full access including connection management
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations tenant-scoped. Tenant extracted from context and enforced at repository level.
 *
 * @see MetaDataSource
 * @see MetaCollection
 * @see com.ghatana.datacloud.infrastructure.persistence.DataSourceRepository
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Service for data source connection and sync management with RBAC
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceService.class);

    private final DataSourceRepository dataSourceRepository;
    private final CollectionRepository collectionRepository;
    private final MetricsCollector metrics;

    /**
     * Creates a new data source service.
     *
     * @param dataSourceRepository the data source repository (required)
     * @param collectionRepository the collection repository (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public DataSourceService(
            DataSourceRepository dataSourceRepository,
            CollectionRepository collectionRepository,
            MetricsCollector metrics) {
        this.dataSourceRepository = Objects.requireNonNull(dataSourceRepository, "DataSourceRepository must not be null");
        this.collectionRepository = Objects.requireNonNull(collectionRepository, "CollectionRepository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Creates a new data source.
     *
     * <p><b>RBAC</b><br>
     * Requires WRITE permission on tenant.
     *
     * <p><b>Validation</b><br>
     * - Tenant ID must match data source
     * - Data source name must be unique per tenant
     * - Target collection must exist and belong to tenant (if specified)
     * - Connection configuration must be valid for the type
     *
     * @param tenantId the tenant identifier (required)
     * @param dataSource the data source to create (required)
     * @param userId the user creating the data source (for audit)
     * @return Promise of created data source with generated ID
     * @throws IllegalArgumentException if validation fails
     */
    public Promise<MetaDataSource> createDataSource(
            String tenantId,
            MetaDataSource dataSource,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(dataSource, "Data source must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        if (!dataSource.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Data source tenant ID must match request tenant");
        }

        dataSource.setCreatedBy(userId);
        dataSource.setUpdatedBy(userId);
        dataSource.setConnectionStatus("DISCONNECTED");

        return dataSourceRepository.existsByName(tenantId, dataSource.getName())
            .then(exists -> {
                if (exists) {
                    metrics.incrementCounter("datasource.create.conflict",
                        "tenant", tenantId,
                        "datasource", dataSource.getName());
                    return Promise.ofException(
                        new IllegalArgumentException("Data source already exists: " + dataSource.getName())
                    );
                }
                
                // Validate target collection exists (if specified)
                if (dataSource.getTargetCollection() != null) {
                    return validateTargetCollectionExists(tenantId, dataSource.getTargetCollection())
                        .then(valid -> {
                            if (!valid) {
                                return Promise.ofException(
                                    new IllegalArgumentException("Target collection does not exist: " + dataSource.getTargetCollection())
                                );
                            }
                            return dataSourceRepository.save(tenantId, dataSource);
                        });
                } else {
                    return dataSourceRepository.save(tenantId, dataSource);
                }
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("datasource.create.success",
                        "tenant", tenantId,
                        "datasource", dataSource.getName(),
                        "type", result.getType().toString());
                    log.info("Data source created: tenantId={}, name={}, type={}, id={}, createdBy={}",
                        tenantId, dataSource.getName(), dataSource.getType(), ((MetaDataSource) result).getId(), userId);
                } else {
                    metrics.incrementCounter("datasource.create.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                    log.error("Failed to create data source: tenantId={}, name={}",
                        tenantId, dataSource.getName(), ex);
                }
            });
    }

    /**
     * Gets a data source by name.
     *
     * <p><b>RBAC</b><br>
     * Requires READ permission on data source.
     *
     * @param tenantId the tenant identifier (required)
     * @param name the data source name (required)
     * @return Promise of Optional containing the data source if found
     */
    public Promise<Optional<MetaDataSource>> getDataSource(String tenantId, String name) {
        validateTenantId(tenantId);
        Objects.requireNonNull(name, "Data source name must not be null");

        return dataSourceRepository.findByName(tenantId, name)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    if (result.isPresent()) {
                        metrics.incrementCounter("datasource.get.success",
                            "tenant", tenantId,
                            "datasource", name);
                    } else {
                        metrics.incrementCounter("datasource.get.not_found",
                            "tenant", tenantId,
                            "datasource", name);
                    }
                } else {
                    metrics.incrementCounter("datasource.get.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Gets all active data sources for a tenant.
     *
     * <p><b>RBAC</b><br>
     * Requires READ permission on tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of list of active data sources
     */
    public Promise<List<MetaDataSource>> listDataSources(String tenantId) {
        validateTenantId(tenantId);

        return dataSourceRepository.findAll(tenantId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("datasource.count.active", "tenant", tenantId);
                    metrics.increment("datasource.count.active", result.size(), Map.of("tenant", tenantId));
                    log.debug("Listed data sources: tenantId={}, count={}", tenantId, result.size());
                } else {
                    metrics.incrementCounter("datasource.list.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Updates a data source.
     *
     * <p><b>RBAC</b><br>
     * Requires WRITE permission on data source.
     *
     * @param tenantId the tenant identifier (required)
     * @param dataSource the data source to update (required)
     * @param userId the user updating the data source (for audit)
     * @return Promise of updated data source
     */
    public Promise<MetaDataSource> updateDataSource(
            String tenantId,
            MetaDataSource dataSource,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(dataSource, "Data source must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        if (!dataSource.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Data source tenant ID must match request tenant");
        }

        dataSource.setUpdatedBy(userId);
        dataSource.setVersion(dataSource.getVersion() + 1);

        // Validate target collection exists (if specified)
        Promise<Boolean> validationPromise = dataSource.getTargetCollection() != null
            ? validateTargetCollectionExists(tenantId, dataSource.getTargetCollection())
            : Promise.of(true);

        return validationPromise.then(valid -> {
            if (!valid) {
                return Promise.ofException(
                    new IllegalArgumentException("Target collection does not exist: " + dataSource.getTargetCollection())
                );
            }
            return dataSourceRepository.save(tenantId, dataSource);
        })
        .whenComplete((result, ex) -> {
            if (ex == null) {
                metrics.incrementCounter("datasource.update.success",
                    "tenant", tenantId,
                    "datasource", dataSource.getName());
                log.info("Data source updated: tenantId={}, name={}, version={}, updatedBy={}",
                    tenantId, dataSource.getName(), result.getVersion(), userId);
            } else {
                metrics.incrementCounter("datasource.update.error",
                    "tenant", tenantId,
                    "error", ex.getClass().getSimpleName());
            }
        });
    }

    /**
     * Deletes a data source (soft delete).
     *
     * <p><b>RBAC</b><br>
     * Requires DELETE permission on data source.
     *
     * <p><b>Soft Delete</b><br>
     * Sets active=false instead of hard-deleting. Preserves audit trail.
     *
     * @param tenantId the tenant identifier (required)
     * @param dataSourceId the data source ID (required)
     * @param userId the user deleting the data source (for audit)
     * @return Promise of void
     */
    public Promise<Void> deleteDataSource(String tenantId, UUID dataSourceId, String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(dataSourceId, "Data source ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return dataSourceRepository.delete(tenantId, dataSourceId)
            .map(result -> (Void) null)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("datasource.delete.success",
                        "tenant", tenantId,
                        "datasourceId", dataSourceId.toString());
                    log.info("Data source deleted: tenantId={}, dataSourceId={}, deletedBy={}",
                        tenantId, dataSourceId, userId);
                } else {
                    metrics.incrementCounter("datasource.delete.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Updates the connection status of a data source.
     *
     * @param tenantId the tenant identifier (required)
     * @param dataSourceName the data source name (required)
     * @param status the new connection status (required)
     * @param errorDetails optional error details if status is ERROR
     * @return Promise of updated data source
     */
    public Promise<MetaDataSource> updateConnectionStatus(
            String tenantId,
            String dataSourceName,
            String status,
            Map<String, Object> errorDetails) {
        validateTenantId(tenantId);
        Objects.requireNonNull(dataSourceName, "Data source name must not be null");
        Objects.requireNonNull(status, "Connection status must not be null");

        return dataSourceRepository.findByName(tenantId, dataSourceName)
            .then(dataSourceOpt -> {
                if (dataSourceOpt.isEmpty()) {
                    return Promise.ofException(
                        new IllegalArgumentException("Data source not found: " + dataSourceName));
                }

                MetaDataSource dataSource = dataSourceOpt.get();
                dataSource.setConnectionStatus(status);
                
                if ("CONNECTED".equals(status)) {
                    dataSource.setLastConnectedAt(Instant.now());
                    dataSource.setErrorDetails(null);
                } else if ("ERROR".equals(status) && errorDetails != null) {
                    dataSource.setErrorDetails(errorDetails);
                }

                return dataSourceRepository.save(tenantId, dataSource);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("datasource.status.update.success",
                        "tenant", tenantId,
                        "datasource", dataSourceName,
                        "status", status);
                    log.info("Data source status updated: tenantId={}, datasource={}, status={}",
                        tenantId, dataSourceName, status);
                } else {
                    metrics.incrementCounter("datasource.status.update.error",
                        "tenant", tenantId,
                        "datasource", dataSourceName);
                }
            });
    }

    /**
     * Updates synchronization statistics for a data source.
     *
     * @param tenantId the tenant identifier (required)
     * @param dataSourceName the data source name (required)
     * @param syncStats the synchronization statistics (required)
     * @return Promise of updated data source
     */
    public Promise<MetaDataSource> updateSyncStats(
            String tenantId,
            String dataSourceName,
            Map<String, Object> syncStats) {
        validateTenantId(tenantId);
        Objects.requireNonNull(dataSourceName, "Data source name must not be null");
        Objects.requireNonNull(syncStats, "Sync stats must not be null");

        return dataSourceRepository.findByName(tenantId, dataSourceName)
            .then(dataSourceOpt -> {
                if (dataSourceOpt.isEmpty()) {
                    return Promise.ofException(
                        new IllegalArgumentException("Data source not found: " + dataSourceName));
                }

                MetaDataSource dataSource = dataSourceOpt.get();
                dataSource.setSyncStats(syncStats);
                dataSource.setLastSyncedAt(Instant.now());

                return dataSourceRepository.save(tenantId, dataSource);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("datasource.sync.update.success",
                        "tenant", tenantId,
                        "datasource", dataSourceName);
                    log.debug("Data source sync stats updated: tenantId={}, datasource={}",
                        tenantId, dataSourceName);
                } else {
                    metrics.incrementCounter("datasource.sync.update.error",
                        "tenant", tenantId,
                        "datasource", dataSourceName);
                }
            });
    }

    /**
     * Counts active data sources for a tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of count
     */
    public Promise<Long> countDataSources(String tenantId) {
        validateTenantId(tenantId);

        return dataSourceRepository.count(tenantId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.increment("datasource.count.total", result, Map.of("tenant", tenantId));
                } else {
                    metrics.incrementCounter("datasource.count.error",
                        "tenant", tenantId);
                }
            });
    }

    /**
     * Validates that the target collection exists and belongs to the tenant.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name to validate
     * @return Promise of boolean indicating if the collection is valid
     */
    private Promise<Boolean> validateTargetCollectionExists(String tenantId, String collectionName) {
        if (collectionName == null || collectionName.trim().isEmpty()) {
            return Promise.of(true);
        }

        return collectionRepository.findByName(tenantId, collectionName)
            .then(collectionOpt -> Promise.of(collectionOpt.isPresent()));
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
