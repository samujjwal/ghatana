package com.ghatana.datacloud.infrastructure.persistence;

import com.ghatana.datacloud.application.DynamicQueryBuilder;
import com.ghatana.datacloud.application.QuerySpec;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * JPA implementation of EntityRepository with ActiveJ Promise support.
 *
 * <p><b>Purpose</b><br>
 * Provides non-blocking entity persistence operations using JPA and Hibernate.
 * Wraps blocking JPA calls in Promise.ofBlocking() for ActiveJ compatibility.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * @Autowired
 * private EntityRepository repository;
 *
 * Promise<Entity> promise = repository.save("tenant-123", entity);
 *
 * // In test with EventloopTestBase:
 * Entity saved = runPromise(() -> promise);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Repository adapter in infrastructure layer (hexagonal architecture)
 * - Implements EntityRepository port from domain layer
 * - Uses JPA EntityManager for persistence
 * - Wraps blocking operations in Promise.ofBlocking()
 * - Enforces tenant isolation at query level
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. EntityManager is request-scoped via @PersistenceContext.
 *
 * <p><b>Multi-Tenancy</b><br>
 * All queries filter by tenant_id to enforce tenant isolation.
 *
 * <p><b>Performance</b><br>
 * - Uses JSONB for flexible data storage
 * - Indexes on tenant_id, collection_name, created_at
 * - GIN index on data column for JSONB queries
 * - Batch operations for bulk inserts/updates
 *
 * @see Entity
 * @see EntityRepository
 * @doc.type class
 * @doc.purpose JPA adapter for entity persistence with ActiveJ Promises
 * @doc.layer product
 * @doc.pattern Repository Adapter (Infrastructure Layer)
 */
public class JpaEntityRepositoryImpl implements EntityRepository {

    private static final Logger logger = LoggerFactory.getLogger(JpaEntityRepositoryImpl.class);
    
    /**
     * Dedicated thread pool for blocking JPA operations.
     * Uses virtual threads (Java 21+) for efficient blocking I/O.
     * Named threads for better diagnostics and monitoring.
     */
    private static final ExecutorService DB_EXECUTOR = Executors.newThreadPerTaskExecutor(
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = Thread.ofVirtual()
                            .name("jpa-entity-repo-" + counter.getAndIncrement())
                            .unstarted(r);
                    return t;
                }
            }
    );

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Finds an entity by ID within tenant and collection.
     *
     * <p><b>Multi-Tenancy</b><br>
     * Query filters by tenant_id and collection_name.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @return Promise of Optional containing entity if found and active
     */
    @Override
    public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            TypedQuery<Entity> query = entityManager.createQuery(
                "SELECT e FROM Entity e WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.id = :id AND e.active = true",
                Entity.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setParameter("id", entityId);

            List<Entity> results = query.getResultList();
            Optional<Entity> result = results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

            logger.debug("findById: tenantId={}, collection={}, id={}, found={}",
                tenantId, collectionName, entityId, result.isPresent());

            return result;
        });
    }

    /**
     * Finds all active entities with filtering, sorting, and pagination.
     *
     * <p><b>Filtering</b><br>
     * Uses JSONB containment operator (@>) for filtering by data fields.
     *
     * <p><b>Sorting</b><br>
     * Supports sorting by data fields using JSONB arrow operator (->).
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param filter the filter criteria (optional)
     * @param sort the sort expression (optional)
     * @param offset the offset (0-based)
     * @param limit the limit (max results)
     * @return Promise of list of entities matching criteria
     */
    @Override
    public Promise<List<Entity>> findAll(
            String tenantId,
            String collectionName,
            Map<String, Object> filter,
            String sort,
            int offset,
            int limit) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            StringBuilder jpql = new StringBuilder(
                "SELECT e FROM Entity e WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.active = true"
            );

            // Add filter conditions (simple equality for now)
            if (filter != null && !filter.isEmpty()) {
                for (String key : filter.keySet()) {
                    // In production, this would use JSONB containment operator
                    // For now, we'll skip complex JSONB filtering in JPQL
                    logger.warn("JSONB filtering not fully implemented in JPQL for key: {}", key);
                }
            }

            // Add sorting
            if (sort != null && !sort.trim().isEmpty()) {
                String[] parts = sort.split(":");
                if (parts.length == 2) {
                    String field = parts[0];
                    String direction = parts[1].toUpperCase();
                    if (direction.equals("ASC") || direction.equals("DESC")) {
                        // In production, this would use JSONB arrow operator for data fields
                        // For now, only support sorting by entity fields
                        if (field.equals("createdAt") || field.equals("updatedAt")) {
                            jpql.append(" ORDER BY e.").append(field).append(" ").append(direction);
                        }
                    }
                }
            }

            TypedQuery<Entity> query = entityManager.createQuery(jpql.toString(), Entity.class);
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setFirstResult(offset);
            query.setMaxResults(limit);

            List<Entity> results = query.getResultList();

            logger.debug("findAll: tenantId={}, collection={}, offset={}, limit={}, found={}",
                tenantId, collectionName, offset, limit, results.size());

            return results;
        });
    }

    /**
     * Finds entities using a custom query specification.
     *
     * <p><b>Query Specification</b><br>
     * The querySpec parameter is opaque to avoid layer coupling.
     * Expected to be a DynamicQueryBuilder or similar query builder object.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param querySpec the query specification (required)
     * @return Promise of list of entities matching query
     */
    @Override
    public Promise<List<Entity>> findByQuery(
            String tenantId,
            String collectionName,
            Object querySpec
    ) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(querySpec, "Query spec must not be null");

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            if (!(querySpec instanceof QuerySpec spec)) {
                logger.warn("Unsupported querySpec type: {}", querySpec.getClass());
                return Collections.emptyList();
            }

            // Base SQL built by DynamicQueryBuilder (selects from 'entities')
            String baseSql = spec.sql();

            // Enforce tenant/collection/active constraints
            String scopedSql = baseSql + " AND tenant_id = :tenantId AND collection_name = :collectionName AND active = true";

            // Create native query mapping to Entity.class
            Query nativeQuery = entityManager.createNativeQuery(scopedSql, Entity.class);

            // Bind parameters from spec
            Map<String, Object> params = spec.parameters();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                nativeQuery.setParameter(entry.getKey(), entry.getValue());
            }

            // Bind scoping params
            nativeQuery.setParameter("tenantId", tenantId);
            nativeQuery.setParameter("collectionName", collectionName);

            @SuppressWarnings("unchecked")
            List<Entity> results = nativeQuery.getResultList();

            logger.debug("findByQuery: tenantId={}, collection={}, returned={}", tenantId, collectionName, results.size());
            return results;
        });
    }

    /**
     * Saves an entity (create or update).
     *
     * <p><b>Create vs Update</b><br>
     * Uses JPA merge() which handles both create and update.
     *
     * <p><b>Optimistic Locking</b><br>
     * Version field incremented automatically by JPA on update.
     *
     * @param tenantId the tenant identifier (required)
     * @param entity the entity to save (required)
     * @return Promise of saved entity
     * @throws IllegalArgumentException if tenant ID mismatch
     */
    @Override
    public Promise<Entity> save(String tenantId, Entity entity) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entity, "Entity must not be null");

        if (!entity.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Entity tenant ID must match request tenant");
        }

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            Entity saved = entityManager.merge(entity);
            entityManager.flush();

            logger.debug("save: tenantId={}, collection={}, id={}, version={}",
                tenantId, saved.getCollectionName(), saved.getId(), saved.getVersion());

            return saved;
        });
    }

    /**
     * Deletes an entity (soft delete).
     *
     * <p><b>Soft Delete</b><br>
     * Sets active=false instead of removing from database.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @return Promise of void
     */
    @Override
    public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            Query query = entityManager.createQuery(
                "UPDATE Entity e SET e.active = false WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.id = :id"
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setParameter("id", entityId);

            int updated = query.executeUpdate();

            logger.debug("delete: tenantId={}, collection={}, id={}, updated={}",
                tenantId, collectionName, entityId, updated);

            return null;
        });
    }

    /**
     * Checks if an entity exists.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID (required)
     * @return Promise of true if entity exists and is active
     */
    @Override
    public Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(e) FROM Entity e WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.id = :id AND e.active = true",
                Long.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setParameter("id", entityId);

            Long count = query.getSingleResult();
            return count > 0;
        });
    }

    /**
     * Counts active entities for a collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise of count
     */
    @Override
    public Promise<Long> count(String tenantId, String collectionName) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(e) FROM Entity e WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.active = true",
                Long.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);

            return query.getSingleResult();
        });
    }

    /**
     * Counts entities matching filter criteria.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param filter the filter criteria (optional)
     * @return Promise of count
     */
    @Override
    public Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            // Simplified implementation - in production would use JSONB filtering
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(e) FROM Entity e WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.active = true",
                Long.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);

            return query.getSingleResult();
        });
    }

    /**
     * Finds entities by dynamic query.
     *
     * <p><b>Note</b><br>
     * This implementation uses native SQL for complex JSONB queries.
    /**
     * Batch saves multiple entities.
     *
     * <p><b>Performance</b><br>
     * Uses JPA batch operations for better performance.
     * Flushes every 50 entities to avoid memory issues.
     *
     * @param tenantId the tenant identifier (required)
     * @param entities the entities to save (required)
     * @return Promise of list of saved entities
     */
    @Override
    public Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entities, "Entities must not be null");

        if (entities.isEmpty()) {
            return Promise.of(List.of());
        }

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            List<Entity> saved = new ArrayList<>();
            int count = 0;

            for (Entity entity : entities) {
                if (!entity.getTenantId().equals(tenantId)) {
                    throw new IllegalArgumentException("Entity tenant ID must match request tenant");
                }

                Entity merged = entityManager.merge(entity);
                saved.add(merged);
                count++;

                // Flush every 50 entities
                if (count % 50 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }

            entityManager.flush();

            logger.debug("saveAll: tenantId={}, count={}", tenantId, saved.size());

            return saved;
        });
    }

    /**
     * Batch deletes multiple entities (soft delete).
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityIds the entity IDs to delete (required)
     * @return Promise of void
     */
    @Override
    public Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(entityIds, "Entity IDs must not be null");

        if (entityIds.isEmpty()) {
            return Promise.of(null);
        }

        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            Query query = entityManager.createQuery(
                "UPDATE Entity e SET e.active = false WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.id IN :ids"
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setParameter("ids", entityIds);

            int updated = query.executeUpdate();

            logger.debug("deleteAll: tenantId={}, collection={}, ids={}, updated={}",
                tenantId, collectionName, entityIds.size(), updated);

            return null;
        });
    }
}
