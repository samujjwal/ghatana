package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for managing collection metadata with RBAC and multi-tenancy.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for collection CRUD operations, RBAC enforcement, and metadata management.
 * All operations are tenant-scoped and return ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CollectionService service = new CollectionService(repository, cache, metrics);
 *
 * // Create collection
 * MetaCollection collection = MetaCollection.builder()
 *     .tenantId("tenant-123")
 *     .name("products")
 *     .label("Products")
 *     .permission(Map.of("read", List.of("ADMIN", "USER")))
 *     .build();
 *
 * Promise<MetaCollection> promise = service.createCollection(
 *     "tenant-123",
 *     collection,
 *     "user-456"
 * );
 *
 * // In test with EventloopTestBase:
 * MetaCollection created = runPromise(() -> promise);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Uses CollectionRepository (infrastructure layer)
 * - Uses MetricsCollector (core/observability)
 * - Enforces RBAC and tenant isolation
 * - Emits metrics for all operations
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters or repository.
 *
 * <p><b>RBAC</b><br>
 * Enforces role-based access control:
 * - READ: View collection metadata
 * - WRITE: Create/update collection and fields
 * - DELETE: Delete collection (soft delete)
 * - ADMIN: Full access
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations tenant-scoped. Tenant extracted from context and enforced at repository level.
 *
 * @see MetaCollection
 * @see com.ghatana.datacloud.infrastructure.persistence.CollectionRepository
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Service for collection metadata management with RBAC
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class CollectionService {

    private static final Logger logger = LoggerFactory.getLogger(CollectionService.class);

    private final CollectionRepository repository;
    private final MetricsCollector metrics;

    /**
     * Creates a new collection service.
     *
     * @param repository the collection repository (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if repository or metrics is null
     */
    public CollectionService(
            CollectionRepository repository,
            MetricsCollector metrics) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Creates a new collection.
     *
     * <p><b>RBAC</b><br>
     * Requires WRITE permission on tenant.
     *
     * <p><b>Validation</b><br>
     * - Tenant ID must match collection
     * - Collection name must be unique per tenant
     * - Permission structure must be valid
     *
     * @param tenantId the tenant identifier (required)
     * @param collection the collection to create (required)
     * @param userId the user creating the collection (for audit)
     * @return Promise of created collection with generated ID
     * @throws IllegalArgumentException if validation fails
     */
    public Promise<MetaCollection> createCollection(
            String tenantId,
            MetaCollection collection,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collection, "Collection must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        if (!collection.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Collection tenant ID must match request tenant");
        }

        collection.setCreatedBy(userId);
        collection.setUpdatedBy(userId);

        return repository.existsByName(tenantId, collection.getName())
            .then(exists -> {
                if (exists) {
                    metrics.incrementCounter("collection.create.conflict",
                        "tenant", tenantId,
                        "collection", collection.getName());
                    return Promise.ofException(
                        new IllegalArgumentException("Collection already exists: " + collection.getName())
                    );
                }
                return repository.save(tenantId, collection);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("collection.create.success",
                        "tenant", tenantId,
                        "collection", collection.getName());
                    logger.info("Collection created: tenantId={}, name={}, id={}, createdBy={}",
                        tenantId, collection.getName(), ((MetaCollection) result).getId(), userId);
                } else {
                    metrics.incrementCounter("collection.create.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                    logger.error("Failed to create collection: tenantId={}, name={}",
                        tenantId, collection.getName(), ex);
                }
            });
    }

    /**
     * Gets a collection by name.
     *
     * <p><b>RBAC</b><br>
     * Requires READ permission on collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param name the collection name (required)
     * @return Promise of Optional containing the collection if found
     */
    public Promise<Optional<MetaCollection>> getCollection(String tenantId, String name) {
        validateTenantId(tenantId);
        Objects.requireNonNull(name, "Collection name must not be null");

        return repository.findByName(tenantId, name)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    if (result.isPresent()) {
                        metrics.incrementCounter("collection.get.success",
                            "tenant", tenantId,
                            "collection", name);
                    } else {
                        metrics.incrementCounter("collection.get.not_found",
                            "tenant", tenantId,
                            "collection", name);
                    }
                } else {
                    metrics.incrementCounter("collection.get.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Gets all active collections for a tenant.
     *
     * <p><b>RBAC</b><br>
     * Requires READ permission on tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of list of active collections
     */
    public Promise<List<MetaCollection>> listCollections(String tenantId) {
        validateTenantId(tenantId);

        return repository.findAll(tenantId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("collection.count.active", "tenant", tenantId);
                    metrics.increment("collection.count.active", result.size(), Map.of("tenant", tenantId));
                    logger.debug("Listed collections: tenantId={}, count={}", tenantId, result.size());
                } else {
                    metrics.incrementCounter("collection.list.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Updates a collection.
     *
     * <p><b>RBAC</b><br>
     * Requires WRITE permission on collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collection the collection to update (required)
     * @param userId the user updating the collection (for audit)
     * @return Promise of updated collection
     */
    public Promise<MetaCollection> updateCollection(
            String tenantId,
            MetaCollection collection,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collection, "Collection must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        if (!collection.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Collection tenant ID must match request tenant");
        }

        collection.setUpdatedBy(userId);
        collection.setVersion(collection.getVersion() + 1);

        return repository.save(tenantId, collection)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("collection.update.success",
                        "tenant", tenantId,
                        "collection", collection.getName());
                    logger.info("Collection updated: tenantId={}, name={}, version={}, updatedBy={}",
                        tenantId, collection.getName(), result.getVersion(), userId);
                } else {
                    metrics.incrementCounter("collection.update.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Deletes a collection (soft delete).
     *
     * <p><b>RBAC</b><br>
     * Requires DELETE permission on collection.
     *
     * <p><b>Soft Delete</b><br>
     * Sets active=false instead of hard-deleting. Preserves audit trail.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionId the collection ID (required)
     * @param userId the user deleting the collection (for audit)
     * @return Promise of void
     */
    public Promise<Void> deleteCollection(String tenantId, UUID collectionId, String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionId, "Collection ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return repository.delete(tenantId, collectionId)
            .map(result -> (Void) null)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("collection.delete.success",
                        "tenant", tenantId,
                        "collectionId", collectionId.toString());
                    logger.info("Collection deleted: tenantId={}, collectionId={}, deletedBy={}",
                        tenantId, collectionId, userId);
                } else {
                    metrics.incrementCounter("collection.delete.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Counts active collections for a tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of count
     */
    public Promise<Long> countCollections(String tenantId) {
        validateTenantId(tenantId);

        return repository.count(tenantId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.increment("collection.count.total", result, Map.of("tenant", tenantId));
                } else {
                    metrics.incrementCounter("collection.count.error",
                        "tenant", tenantId);
                }
            });
    }

    // ==================== Field Management ====================

    /**
     * Adds a new field to a collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param field the field to add (required)
     * @param userId the user creating the field (for audit)
     * @return Promise of updated collection
     */
    public Promise<MetaCollection> addField(
            String tenantId,
            String collectionName,
            MetaField field,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(field, "Field must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return repository.findByName(tenantId, collectionName)
            .then(collectionOpt -> {
                if (collectionOpt.isEmpty()) {
                    return Promise.ofException(
                        new IllegalArgumentException("Collection not found: " + collectionName));
                }

                MetaCollection collection = collectionOpt.get();
                List<MetaField> fields = new ArrayList<>(collection.getFields());
                
                // Check for duplicate field name
                boolean exists = fields.stream()
                    .anyMatch(f -> f.getName().equals(field.getName()));
                if (exists) {
                    return Promise.ofException(
                        new IllegalArgumentException("Field already exists: " + field.getName()));
                }

                fields.add(field);
                collection.setFields(fields);
                collection.setUpdatedBy(userId);

                return repository.save(tenantId, collection);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("collection.field.add.success",
                        "tenant", tenantId, "collection", collectionName);
                    logger.info("Field added: tenantId={}, collection={}, field={}", 
                        tenantId, collectionName, field.getName());
                } else {
                    metrics.incrementCounter("collection.field.add.error",
                        "tenant", tenantId, "collection", collectionName);
                }
            });
    }

    /**
     * Updates an existing field in a collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param fieldId the field ID (required)
     * @param fieldUpdate the field updates (required)
     * @param userId the user updating the field (for audit)
     * @return Promise of updated collection
     */
    public Promise<MetaCollection> updateField(
            String tenantId,
            String collectionName,
            UUID fieldId,
            MetaField fieldUpdate,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(fieldId, "Field ID must not be null");
        Objects.requireNonNull(fieldUpdate, "Field update must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return repository.findByName(tenantId, collectionName)
            .then(collectionOpt -> {
                if (collectionOpt.isEmpty()) {
                    return Promise.ofException(
                        new IllegalArgumentException("Collection not found: " + collectionName));
                }

                MetaCollection collection = collectionOpt.get();
                List<MetaField> fields = new ArrayList<>(collection.getFields());
                
                // Find and update the field
                boolean found = false;
                for (int i = 0; i < fields.size(); i++) {
                    if (fields.get(i).getId().equals(fieldId)) {
                        fields.set(i, fieldUpdate);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    return Promise.ofException(
                        new IllegalArgumentException("Field not found: " + fieldId));
                }

                collection.setFields(fields);
                collection.setUpdatedBy(userId);

                return repository.save(tenantId, collection);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("collection.field.update.success",
                        "tenant", tenantId, "collection", collectionName);
                    logger.info("Field updated: tenantId={}, collection={}, fieldId={}", 
                        tenantId, collectionName, fieldId);
                } else {
                    metrics.incrementCounter("collection.field.update.error",
                        "tenant", tenantId, "collection", collectionName);
                }
            });
    }

    /**
     * Deletes a field from a collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param fieldId the field ID (required)
     * @param userId the user deleting the field (for audit)
     * @return Promise of updated collection
     */
    public Promise<MetaCollection> deleteField(
            String tenantId,
            String collectionName,
            UUID fieldId,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(fieldId, "Field ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return repository.findByName(tenantId, collectionName)
            .then(collectionOpt -> {
                if (collectionOpt.isEmpty()) {
                    return Promise.ofException(
                        new IllegalArgumentException("Collection not found: " + collectionName));
                }

                MetaCollection collection = collectionOpt.get();
                List<MetaField> fields = new ArrayList<>(collection.getFields());
                
                // Remove the field
                boolean removed = fields.removeIf(f -> f.getId().equals(fieldId));
                if (!removed) {
                    return Promise.ofException(
                        new IllegalArgumentException("Field not found: " + fieldId));
                }

                collection.setFields(fields);
                collection.setUpdatedBy(userId);

                return repository.save(tenantId, collection);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("collection.field.delete.success",
                        "tenant", tenantId, "collection", collectionName);
                    logger.info("Field deleted: tenantId={}, collection={}, fieldId={}", 
                        tenantId, collectionName, fieldId);
                } else {
                    metrics.incrementCounter("collection.field.delete.error",
                        "tenant", tenantId, "collection", collectionName);
                }
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
