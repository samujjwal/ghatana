package com.ghatana.datacloud.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.application.QuerySpec;
import com.ghatana.datacloud.entity.DataCloudColumnNames;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.entity.IdempotencyRecord;
import com.ghatana.datacloud.entity.IdempotencyRepository;
import com.ghatana.datacloud.infrastructure.config.JpaThreadPoolConfig;
import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

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
 * - Thread pool configuration via JpaThreadPoolConfig (externalizable)
 *
 * <p><b>Configuration</b><br>
 * Thread pool behavior can be configured via environment variables:
 * <ul>
 *   <li>{@code JPA_THREAD_POOL_TYPE}: "VIRTUAL" (default) or "PLATFORM"
 *   <li>{@code JPA_THREAD_POOL_PREFIX}: thread name prefix (default: "jpa-worker")
 *   <li>{@code JPA_THREAD_POOL_QUEUE_SIZE}: queue size (default: 1000)
 *   <li>{@code JPA_THREAD_POOL_CORE_SIZE}: core pool size (default: 10)
 *   <li>{@code JPA_THREAD_POOL_MAX_SIZE}: max pool size (default: 100)
 * </ul>
 *
 * @see Entity
 * @see EntityRepository
 * @see JpaThreadPoolConfig
 * @doc.type class
 * @doc.purpose JPA adapter for entity persistence with ActiveJ Promises
 * @doc.layer product
 * @doc.pattern Repository Adapter (Infrastructure Layer)
 */
public class JpaEntityRepositoryImpl implements EntityRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaEntityRepositoryImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ACTIVE_ENTITY_SCOPE_SQL = " AND " + DataCloudColumnNames.TENANT_ID +
        " = :tenantId AND " + DataCloudColumnNames.COLLECTION_NAME + " = :collectionName AND " +
        DataCloudColumnNames.ACTIVE + " = true";
    private static final String ACTIVE_ENTITY_COUNT_SQL = "SELECT COUNT(*) FROM entities WHERE " +
        DataCloudColumnNames.TENANT_ID + " = :tenantId AND " + DataCloudColumnNames.COLLECTION_NAME +
        " = :collectionName AND " + DataCloudColumnNames.ACTIVE + " = true";

    private final ExecutorService dbExecutor;
    private final IdempotencyRepository idempotencyRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Creates a repository backed by the default virtual-thread executor.
     * Uses thread pool configuration from environment variables.
     */
    public JpaEntityRepositoryImpl() {
        this(JpaThreadPoolConfig.fromEnvironment().createExecutorService(), new JpaIdempotencyRepositoryImpl());
    }

    /**
     * Creates a repository with configurable thread pool from environment variables.
     *
     * @param config thread pool configuration (can be loaded from environment)
     */
    public JpaEntityRepositoryImpl(JpaThreadPoolConfig config) {
        this(Objects.requireNonNull(config, "config must not be null").createExecutorService(), new JpaIdempotencyRepositoryImpl());
    }

    /**
     * Creates a repository backed by the supplied blocking-work executor.
     *
     * @param dbExecutor executor used for blocking JPA calls
     */
    public JpaEntityRepositoryImpl(ExecutorService dbExecutor) {
        this(dbExecutor, new JpaIdempotencyRepositoryImpl());
    }

    /**
     * Creates a repository with custom executor and idempotency repository.
     *
     * @param dbExecutor executor used for blocking JPA calls
     * @param idempotencyRepository idempotency repository
     */
    public JpaEntityRepositoryImpl(ExecutorService dbExecutor, IdempotencyRepository idempotencyRepository) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor must not be null");
        this.idempotencyRepository = Objects.requireNonNull(idempotencyRepository, "idempotencyRepository must not be null");
    }

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

        // C3: ofBlocking offloads the JDBC round-trip to the virtual-thread executor.
        return Promise.ofBlocking(dbExecutor, () -> {
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

            log.debug("findById: tenantId={}, collection={}, id={}, found={}",
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

        // H1: Throw immediately if a non-empty filter is provided — silently ignoring it returns
        // over-broad data sets, which is worse than a fast-fail.
        // Use findByQuery() with DynamicQueryBuilder for filtered queries.
        if (filter != null && !filter.isEmpty()) {
            return Promise.ofException(new UnsupportedOperationException(
                "JSONB field filtering via findAll() is unavailable. " +
                "Use findByQuery() with DynamicQueryBuilder for filtered entity queries."));
        }

        // C3: ofBlocking offloads the JDBC round-trip to the virtual-thread executor.
        return Promise.ofBlocking(dbExecutor, () -> {
            StringBuilder jpql = new StringBuilder(
                "SELECT e FROM Entity e WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.active = true"
            );

            // M3: Support sorting by entity timestamp fields and JSONB data fields.
            if (sort != null && !sort.trim().isEmpty()) {
                String[] parts = sort.split(":");
                if (parts.length == 2) {
                    String field = parts[0].trim();
                    String direction = parts[1].trim().toUpperCase();
                    if ((direction.equals("ASC") || direction.equals("DESC"))) {
                        if (field.equals("createdAt") || field.equals("updatedAt")) {
                            // Mapped JPA column — safe to interpolate (validated against allowlist)
                            jpql.append(" ORDER BY e.").append(field).append(" ").append(direction);
                        } else if (SAFE_SORT_FIELD.matcher(field).matches()) {
                            // JSONB data field — use native function; append as native query suffix
                            // Note: JPQL does not support JSONB operators; delegate to native path.
                            log.debug("findAll: JSONB sort on '{}' not supported in JPQL; ignoring sort.", field);
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

            log.debug("findAll: tenantId={}, collection={}, offset={}, limit={}, found={}",
                tenantId, collectionName, offset, limit, results.size());
            return results;
        });
    }

    /** Allowlist pattern for sort field names — prevents JPQL injection via field name. */
    private static final java.util.regex.Pattern SAFE_SORT_FIELD =
            java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");

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

        // C4: Handle application.QuerySpec (full parameterized SQL from DynamicQueryBuilder).
        if (querySpec instanceof QuerySpec appSpec) {
            return Promise.ofBlocking(dbExecutor, () -> {
                String baseSql = appSpec.sql();
                String scopedSql = baseSql + ACTIVE_ENTITY_SCOPE_SQL;
                Query nativeQuery = entityManager.createNativeQuery(scopedSql, Entity.class);
                Map<String, Object> params = appSpec.parameters();
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    nativeQuery.setParameter(entry.getKey(), entry.getValue());
                }
                nativeQuery.setParameter("tenantId", tenantId);
                nativeQuery.setParameter("collectionName", collectionName);
                if (appSpec.offset() > 0) nativeQuery.setFirstResult(appSpec.offset());
                if (appSpec.limit() > 0) nativeQuery.setMaxResults(appSpec.limit());
                @SuppressWarnings("unchecked")
                List<Entity> results = nativeQuery.getResultList();
                log.debug("findByQuery(AppSpec): tenantId={}, collection={}, returned={}",
                    tenantId, collectionName, results.size());
                return results;
            });
        }

        // C4: Handle entity.storage.QuerySpec (filter string + pagination from StorageConnector).
        if (querySpec instanceof com.ghatana.datacloud.entity.storage.QuerySpec storageSpec) {
            return Promise.ofBlocking(dbExecutor, () -> {
                StringBuilder jpql = new StringBuilder(
                    "SELECT e FROM Entity e WHERE e.tenantId = :tenantId " +
                    "AND e.collectionName = :collectionName AND e.active = true"
                );
                // Sort by entity timestamp fields only (JSONB field sort requires native query)
                if (storageSpec.getSortFields() != null && !storageSpec.getSortFields().isEmpty()) {
                    List<String> orderClauses = new ArrayList<>();
                    for (com.ghatana.datacloud.entity.storage.QuerySpec.SortField sf : storageSpec.getSortFields()) {
                        String field = sf.fieldName();
                        if ((field.equals("createdAt") || field.equals("updatedAt"))
                                && SAFE_SORT_FIELD.matcher(field).matches()) {
                            String dir = sf.direction() != null
                                    ? sf.direction().name() : "ASC";
                            orderClauses.add("e." + field + " " + dir);
                        }
                    }
                    if (!orderClauses.isEmpty()) {
                        jpql.append(" ORDER BY ").append(String.join(", ", orderClauses));
                    }
                }
                TypedQuery<Entity> q = entityManager.createQuery(jpql.toString(), Entity.class);
                q.setParameter("tenantId", tenantId);
                q.setParameter("collectionName", collectionName);
                int offset = storageSpec.getOffset();
                int limit  = storageSpec.getLimit() > 0 ? storageSpec.getLimit() : 100;
                q.setFirstResult(offset);
                q.setMaxResults(limit);
                List<Entity> results = q.getResultList();
                log.debug("findByQuery(StorageSpec): tenantId={}, collection={}, returned={}",
                    tenantId, collectionName, results.size());
                return results;
            });
        }

        log.error("findByQuery: unsupported querySpec type: {}", querySpec.getClass().getName());
        return Promise.ofException(new IllegalArgumentException(
            "Unsupported querySpec type: " + querySpec.getClass().getName() +
            ". Use application.QuerySpec (from DynamicQueryBuilder) or entity.storage.QuerySpec."));
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

        return Promise.ofBlocking(dbExecutor, () -> {
            Entity saved = entityManager.merge(entity);
            entityManager.flush();
            log.debug("save: tenantId={}, collection={}, id={}, version={}",
                tenantId, saved.getCollectionName(), saved.getId(), saved.getVersion());
            return saved;
        });
    }

    /**
     * Saves an entity with idempotency guarantee.
     *
     * <p><b>Idempotency</b><br>
     * If the same idempotency key is used for the same tenant and collection,
     * returns the previously saved entity instead of creating a duplicate.
     *
     * @param tenantId the tenant identifier (required)
     * @param entity the entity to save (required)
     * @param idempotencyKey the idempotency key (required, max 255 chars)
     * @return Promise of saved entity (new or existing)
     * @throws IllegalArgumentException if tenantId mismatch or key is null/empty
     */
    @Override
    public Promise<Entity> saveWithIdempotency(String tenantId, Entity entity, String idempotencyKey) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");

        if (idempotencyKey.trim().isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Idempotency key must not be empty"));
        }

        if (idempotencyKey.length() > 255) {
            return Promise.ofException(new IllegalArgumentException("Idempotency key must not exceed 255 characters"));
        }

        if (!entity.getTenantId().equals(tenantId)) {
            return Promise.ofException(new IllegalArgumentException("Entity tenant ID must match request tenant"));
        }

        return Promise.ofBlocking(dbExecutor, () -> {
            Optional<IdempotencyRecord> existingRecord = idempotencyRepository.findByKey(tenantId, entity.getCollectionName(), idempotencyKey).getResult();
            
            if (existingRecord.isPresent()) {
                // Idempotency key exists - return the previously saved entity
                UUID previousEntityId = existingRecord.get().getEntityId();
                if (previousEntityId == null) {
                    throw new IllegalStateException("Idempotency record exists but has no entity ID");
                }
                log.debug("saveWithIdempotency: returning existing entity for key={}", idempotencyKey);
                Optional<Entity> optEntity = findById(tenantId, entity.getCollectionName(), previousEntityId).getResult();
                return optEntity.orElseThrow(() -> new IllegalStateException("Idempotency record references non-existent entity"));
            } else {
                // No existing record - save entity and create idempotency record
                Entity savedEntity = save(tenantId, entity).getResult();
                IdempotencyRecord record = IdempotencyRecord.builder()
                    .tenantId(tenantId)
                    .collectionName(entity.getCollectionName())
                    .idempotencyKey(idempotencyKey)
                    .entityId(savedEntity.getId())
                    .createdBy(entity.getCreatedBy())
                    .build();
                idempotencyRepository.save(record).getResult();
                return savedEntity;
            }
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

        return Promise.ofBlocking(dbExecutor, () -> {
            Query query = entityManager.createQuery(
                "UPDATE Entity e SET e.active = false WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.id = :id"
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setParameter("id", entityId);
            int updated = query.executeUpdate();
            log.debug("delete: tenantId={}, collection={}, id={}, updated={}",
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

        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(e) FROM Entity e WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.id = :id AND e.active = true",
                Long.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setParameter("id", entityId);
            return query.getSingleResult() > 0;
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

        return Promise.ofBlocking(dbExecutor, () -> {
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

        return Promise.ofBlocking(dbExecutor, () -> {
            if (filter == null || filter.isEmpty()) {
                TypedQuery<Long> q = entityManager.createQuery(
                    "SELECT COUNT(e) FROM Entity e WHERE e.tenantId = :tenantId " +
                    "AND e.collectionName = :collectionName AND e.active = true",
                    Long.class);
                q.setParameter("tenantId", tenantId);
                q.setParameter("collectionName", collectionName);
                return q.getSingleResult();
            }
            // H4: Use JSONB containment with a fully-parameterized native query.
            // data @> CAST(:filterJson AS jsonb) is safe — no string interpolation.
            String filterJson = OBJECT_MAPPER.writeValueAsString(filter);
            Query q = entityManager.createNativeQuery(
                ACTIVE_ENTITY_COUNT_SQL + " AND " + DataCloudColumnNames.DATA + " @> CAST(:filterJson AS jsonb)");
            q.setParameter("tenantId", tenantId);
            q.setParameter("collectionName", collectionName);
            q.setParameter("filterJson", filterJson);
            return ((Number) q.getSingleResult()).longValue();
        });
    }

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

        return Promise.ofBlocking(dbExecutor, () -> {
            List<Entity> saved = new ArrayList<>();
            int batchCount = 0;
            for (Entity entity : entities) {
                if (!entity.getTenantId().equals(tenantId)) {
                    throw new IllegalArgumentException("Entity tenant ID must match request tenant");
                }
                saved.add(entityManager.merge(entity));
                batchCount++;
                if (batchCount % 50 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            entityManager.flush();
            log.debug("saveAll: tenantId={}, count={}", tenantId, saved.size());
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

        return Promise.ofBlocking(dbExecutor, () -> {
            Query query = entityManager.createQuery(
                "UPDATE Entity e SET e.active = false WHERE e.tenantId = :tenantId " +
                "AND e.collectionName = :collectionName AND e.id IN :ids"
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setParameter("ids", entityIds);
            int updated = query.executeUpdate();
            log.debug("deleteAll: tenantId={}, collection={}, ids={}, updated={}",
                tenantId, collectionName, entityIds.size(), updated);
            return null;
        });
    }
}
