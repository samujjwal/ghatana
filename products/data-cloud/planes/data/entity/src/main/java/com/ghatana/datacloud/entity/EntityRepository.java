package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for entity data access.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for entity persistence operations with multi-tenancy support.
 * All operations return ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityRepository repository = ...; // Get from DI container
 *
 * // Create entity
 * Entity entity = Entity.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("orders")
 *     .data(Map.of("orderId", "ORD-001", "amount", 99.99))
 *     .createdBy("user-456")
 *     .build();
 *
 * Promise<Entity> promise = repository.save("tenant-123", entity);
 *
 * // Find entity
 * Promise<Optional<Entity>> findPromise = repository.findById("tenant-123", "orders", entityId);
 *
 * // List entities with filtering
 * Promise<List<Entity>> listPromise = repository.findAll(
 *     "tenant-123",
 *     "orders",
 *     Map.of("status", "active"),
 *     "createdAt:DESC",
 *     0,
 *     50
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Port interface in domain layer (hexagonal architecture)
 * - Implemented by infrastructure layer (JpaEntityRepositoryImpl)
 * - All operations tenant-scoped for multi-tenancy
 * - ActiveJ Promise-based for non-blocking operations
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations require tenantId parameter. Implementations MUST enforce tenant isolation.
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe.
 *
 * @see Entity
 * @see io.activej.promise.Promise
 * @doc.type interface
 * @doc.purpose Repository port for entity persistence
 * @doc.layer product
 * @doc.pattern Repository Port (Domain Layer)
 */
public interface EntityRepository {

    /**
     * Finds an entity by ID within a tenant and collection.
     *
     * <p><b>Multi-Tenancy</b><br>
     * Only returns entity if it belongs to the specified tenant and collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @return Promise of Optional containing the entity if found and active
     */
    Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId);

    /**
     * Finds all active entities for a tenant and collection.
     *
     * <p><b>Filtering</b><br>
     * filter parameter is a map of field name to value for equality filtering.
     * For complex filtering, use findByQuery() instead.
     *
     * <p><b>Sorting</b><br>
     * sort parameter format: "fieldName:ASC" or "fieldName:DESC"
     *
     * <p><b>Pagination</b><br>
     * offset is 0-based, limit is max results per page.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param filter the filter criteria (optional, can be empty)
     * @param sort the sort expression (optional, can be null)
     * @param offset the offset (0-based, required)
     * @param limit the limit (max results, required)
     * @return Promise of list of entities matching criteria
     */
    Promise<List<Entity>> findAll(
            String tenantId,
            String collectionName,
            Map<String, Object> filter,
            String sort,
            int offset,
            int limit
    );

    /**
     * Saves an entity (create or update).
     *
     * <p><b>Create vs Update</b><br>
     * If entity.getId() is new, creates new entity.
     * If entity.getId() exists, updates existing entity with optimistic locking.
     *
     * <p><b>Multi-Tenancy</b><br>
     * Entity tenantId must match parameter tenantId.
     *
     * @param tenantId the tenant identifier (required)
     * @param entity the entity to save (required)
     * @return Promise of saved entity with updated version
     * @throws IllegalArgumentException if tenantId mismatch
     * @throws jakarta.persistence.OptimisticLockException if version conflict
     */
    Promise<Entity> save(String tenantId, Entity entity);

    /**
     * Deletes an entity (soft delete).
     *
     * <p><b>Soft Delete</b><br>
     * Sets active=false instead of hard-deleting. Preserves audit trail.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @return Promise of void
     */
    Promise<Void> delete(String tenantId, String collectionName, UUID entityId);

    /**
     * Checks if an entity exists for a tenant and collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @return Promise of true if entity exists and is active
     */
    Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId);

    /**
     * Counts active entities for a tenant and collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise of count
     */
    Promise<Long> count(String tenantId, String collectionName);

    /**
     * Counts entities matching filter criteria.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param filter the filter criteria (optional, can be empty)
     * @return Promise of count
     */
    Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter);

    /**
     * Finds entities by dynamic query.
     *
     * <p><b>Purpose</b><br>
     * Allows complex queries using a query specification object.
     * The querySpec parameter is typically created by DynamicQueryBuilder in the application layer.
     *
     * <p><b>SQL Injection Prevention</b><br>
     * QuerySpec must use parameterized queries (never concatenate user input).
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param querySpec the query specification (required, opaque object type to avoid layer coupling)
     * @return Promise of list of entities matching query
     */
    Promise<List<Entity>> findByQuery(
            String tenantId,
            String collectionName,
            Object querySpec
    );

    /**
     * Batch saves multiple entities.
     *
     * <p><b>Performance</b><br>
     * More efficient than individual saves for bulk operations.
     *
     * @param tenantId the tenant identifier (required)
     * @param entities the entities to save (required)
     * @return Promise of list of saved entities
     */
    Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities);

    /**
     * Batch deletes multiple entities (soft delete).
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityIds the entity IDs to delete (required)
     * @return Promise of void
     */
    Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds);
}
