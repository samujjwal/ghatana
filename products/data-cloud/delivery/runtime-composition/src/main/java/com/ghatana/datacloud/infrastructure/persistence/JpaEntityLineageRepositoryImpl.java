package com.ghatana.datacloud.infrastructure.persistence;

import com.ghatana.datacloud.entity.EntityLineage;
import com.ghatana.datacloud.entity.EntityLineageRepository;
import com.ghatana.datacloud.infrastructure.config.JpaThreadPoolConfig;
import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * JPA implementation of EntityLineageRepository with ActiveJ Promise support.
 *
 * <p><b>Purpose</b><br>
 * Provides non-blocking entity lineage persistence operations using JPA and Hibernate.
 * Wraps blocking JPA calls in Promise.ofBlocking() for ActiveJ compatibility.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * @Autowired
 * private EntityLineageRepository repo;
 *
 * Promise<List<EntityLineage>> lineage = repo.findByEntityId(entityId);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Repository adapter in infrastructure layer (hexagonal architecture)
 * - Implements EntityLineageRepository port from domain layer
 * - Uses JPA EntityManager for persistence
 * - Wraps blocking operations in Promise.ofBlocking()
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. EntityManager is request-scoped via @PersistenceContext.
 *
 * @see EntityLineage
 * @see EntityLineageRepository
 * @doc.type class
 * @doc.purpose JPA adapter for entity lineage persistence with ActiveJ Promises
 * @doc.layer product
 * @doc.pattern Repository Adapter (Infrastructure Layer)
 */
public class JpaEntityLineageRepositoryImpl implements EntityLineageRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaEntityLineageRepositoryImpl.class);

    private final ExecutorService dbExecutor;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Creates a repository backed by the default virtual-thread executor.
     */
    public JpaEntityLineageRepositoryImpl() {
        this(JpaThreadPoolConfig.fromEnvironment());
    }

    /**
     * Creates a repository with configurable thread pool.
     *
     * @param config thread pool configuration
     */
    public JpaEntityLineageRepositoryImpl(JpaThreadPoolConfig config) {
        this(Objects.requireNonNull(config, "config must not be null").createExecutorService());
    }

    /**
     * Creates a repository backed by the supplied blocking-work executor.
     *
     * @param dbExecutor executor used for blocking JPA calls
     */
    public JpaEntityLineageRepositoryImpl(ExecutorService dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor must not be null");
    }

    @Override
    public Promise<List<EntityLineage>> findByEntityId(UUID entityId) {
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<EntityLineage> query = entityManager.createQuery(
                "SELECT l FROM EntityLineage l WHERE l.entityId = :entityId ORDER BY l.createdAt ASC",
                EntityLineage.class
            );
            query.setParameter("entityId", entityId);
            List<EntityLineage> results = query.getResultList();
            log.debug("findByEntityId: entityId={}, found={}", entityId, results.size());
            return results;
        });
    }

    @Override
    public Promise<List<EntityLineage>> findByTenantAndCollection(String tenantId, String collectionName) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<EntityLineage> query = entityManager.createQuery(
                "SELECT l FROM EntityLineage l WHERE l.tenantId = :tenantId " +
                "AND l.collectionName = :collectionName ORDER BY l.createdAt DESC",
                EntityLineage.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            List<EntityLineage> results = query.getResultList();
            log.debug("findByTenantAndCollection: tenantId={}, collection={}, found={}",
                tenantId, collectionName, results.size());
            return results;
        });
    }

    @Override
    public Promise<List<EntityLineage>> findBySource(String sourceType, String sourceId) {
        Objects.requireNonNull(sourceType, "Source type must not be null");
        Objects.requireNonNull(sourceId, "Source ID must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<EntityLineage> query = entityManager.createQuery(
                "SELECT l FROM EntityLineage l WHERE l.sourceType = :sourceType " +
                "AND l.sourceId = :sourceId ORDER BY l.createdAt DESC",
                EntityLineage.class
            );
            query.setParameter("sourceType", sourceType);
            query.setParameter("sourceId", sourceId);
            List<EntityLineage> results = query.getResultList();
            log.debug("findBySource: sourceType={}, sourceId={}, found={}",
                sourceType, sourceId, results.size());
            return results;
        });
    }

    @Override
    public Promise<List<EntityLineage>> findByParentEntityId(UUID parentEntityId) {
        Objects.requireNonNull(parentEntityId, "Parent entity ID must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<EntityLineage> query = entityManager.createQuery(
                "SELECT l FROM EntityLineage l WHERE l.parentEntityId = :parentEntityId " +
                "ORDER BY l.createdAt ASC",
                EntityLineage.class
            );
            query.setParameter("parentEntityId", parentEntityId);
            List<EntityLineage> results = query.getResultList();
            log.debug("findByParentEntityId: parentEntityId={}, found={}", parentEntityId, results.size());
            return results;
        });
    }

    @Override
    public Promise<EntityLineage> save(EntityLineage lineage) {
        Objects.requireNonNull(lineage, "Lineage must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            EntityLineage saved = entityManager.merge(lineage);
            entityManager.flush();
            log.debug("save: entityId={}, sourceType={}, sourceId={}",
                saved.getEntityId(), saved.getSourceType(), saved.getSourceId());
            return saved;
        });
    }

    @Override
    public Promise<Void> deleteByEntityId(UUID entityId) {
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            Query query = entityManager.createQuery(
                "DELETE FROM EntityLineage l WHERE l.entityId = :entityId"
            );
            query.setParameter("entityId", entityId);
            int deleted = query.executeUpdate();
            log.debug("deleteByEntityId: entityId={}, deleted={}", entityId, deleted);
            return null;
        });
    }

    @Override
    public Promise<List<EntityLineage>> findAncestryChain(UUID entityId, int maxDepth) {
        Objects.requireNonNull(entityId, "Entity ID must not be null");
        if (maxDepth <= 0) {
            maxDepth = 10;
        }

        final int finalMaxDepth = maxDepth;

        return Promise.ofBlocking(dbExecutor, () -> {
            List<EntityLineage> chain = new ArrayList<>();
            UUID[] currentEntityIdRef = new UUID[]{entityId};
            int[] depthRef = new int[]{0};

            while (currentEntityIdRef[0] != null && depthRef[0] < finalMaxDepth) {
                TypedQuery<EntityLineage> query = entityManager.createQuery(
                    "SELECT l FROM EntityLineage l WHERE l.entityId = :entityId " +
                    "AND l.parentEntityId IS NOT NULL ORDER BY l.createdAt ASC",
                    EntityLineage.class
                );
                query.setParameter("entityId", currentEntityIdRef[0]);
                query.setMaxResults(1);
                List<EntityLineage> results = query.getResultList();

                if (results.isEmpty()) {
                    break;
                }

                EntityLineage lineage = results.get(0);
                chain.add(lineage);
                currentEntityIdRef[0] = lineage.getParentEntityId();
                depthRef[0]++;
            }

            log.debug("findAncestryChain: entityId={}, depth={}, chainSize={}",
                entityId, depthRef[0], chain.size());
            return chain;
        });
    }
}
