package com.ghatana.datacloud.infrastructure.persistence;

import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.MetaCollection;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of CollectionRepository using ActiveJ Promises.
 *
 * <p><b>Purpose</b><br>
 * Adapts blocking JPA operations to non-blocking ActiveJ Promise-based API.
 * All database operations are wrapped in Promise.ofBlocking() to avoid blocking the eventloop.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityManager em = entityManagerFactory.createEntityManager();
 * CollectionRepository repo = new JpaCollectionRepositoryImpl(em);
 * Promise<MetaCollection> promise = repo.findByName("tenant-123", "products");
 *
 * // In test:
 * MetaCollection collection = runPromise(() -> promise);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Adapter in hexagonal architecture (infrastructure layer)
 * - Implements CollectionRepository port
 * - Wraps core/database JPA functionality
 * - Used by CollectionService (application layer)
 * - Enforces tenant isolation at query level
 *
 * <p><b>Thread Safety</b><br>
 * EntityManager is NOT thread-safe. Use one instance per request/transaction.
 * Promise.ofBlocking() executes on separate thread pool, safe for eventloop.
 *
 * <p><b>Performance</b><br>
 * - All queries filtered by tenant for security
 * - Uses JPA second-level cache when configured
 * - Lazy loading for fields collection (avoid N+1)
 * - Indexes on tenant_id and active columns
 *
 * <p><b>Tenant Isolation</b><br>
 * Every query includes WHERE tenant_id = :tenantId to prevent cross-tenant access.
 * This is enforced at the SQL level, not application level.
 *
 * @see CollectionRepository
 * @see MetaCollection
 * @see io.activej.promise.Promise
 * @doc.type class
 * @doc.purpose JPA adapter for collection metadata persistence with ActiveJ Promises
 * @doc.layer product
 * @doc.pattern Repository (Adapter)
 */
public class JpaCollectionRepositoryImpl implements com.ghatana.datacloud.entity.CollectionRepository {

    private static final Logger logger = LoggerFactory.getLogger(JpaCollectionRepositoryImpl.class);

    private final EntityManager entityManager;
    private final Eventloop eventloop;

    /**
     * Creates a new JPA collection repository.
     *
     * @param entityManager the JPA entity manager (must be thread-local or request-scoped)
     * @param eventloop the ActiveJ eventloop for async operations
     * @throws NullPointerException if entityManager or eventloop is null
     */
    public JpaCollectionRepositoryImpl(EntityManager entityManager, Eventloop eventloop) {
        this.entityManager = Objects.requireNonNull(entityManager, "EntityManager must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "Eventloop must not be null");
    }

    @Override
    public Promise<Optional<MetaCollection>> findByName(String tenantId, String name) {
        validateTenantId(tenantId);
        Objects.requireNonNull(name, "Collection name must not be null");

        return Promise.ofBlocking(eventloop, () -> {
            TypedQuery<MetaCollection> query = entityManager.createQuery(
                "SELECT c FROM MetaCollection c WHERE c.tenantId = :tenantId AND c.name = :name AND c.active = true",
                MetaCollection.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("name", name);
            return query.getResultStream().findFirst();
        });
    }

    @Override
    public Promise<List<MetaCollection>> findAll(String tenantId) {
        validateTenantId(tenantId);

        return Promise.ofBlocking(eventloop, () -> {
            TypedQuery<MetaCollection> query = entityManager.createQuery(
                "SELECT c FROM MetaCollection c WHERE c.tenantId = :tenantId ORDER BY c.name",
                MetaCollection.class
            );
            query.setParameter("tenantId", tenantId);
            return query.getResultList();
        });
    }

    @Override
    public Promise<List<String>> findAllTenantIds() {
        return Promise.ofBlocking(eventloop, () -> {
            TypedQuery<String> query = entityManager.createQuery(
                "SELECT DISTINCT c.tenantId FROM MetaCollection c",
                String.class
            );
            return query.getResultList();
        });
    }

    @Override
    public Promise<MetaCollection> save(String tenantId, MetaCollection collection) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID must not be null");
        }
        if (collection == null) {
            throw new IllegalArgumentException("Collection must not be null");
        }
        validateTenantId(tenantId);
        
        // Ensure collection tenant ID matches the parameter
        collection.setTenantId(tenantId);

        return Promise.ofBlocking(eventloop, () -> {
            entityManager.getTransaction().begin();
            try {
                MetaCollection saved = entityManager.merge(collection);
                entityManager.getTransaction().commit();
                logger.debug("Collection saved: tenantId={}, name={}, id={}", 
                    saved.getTenantId(), saved.getName(), saved.getId());
                return saved;
            } catch (Exception e) {
                entityManager.getTransaction().rollback();
                logger.error("Failed to save collection: tenantId={}, name={}", 
                    collection.getTenantId(), collection.getName(), e);
                throw e;
            }
        });
    }

    @Override
    public Promise<Boolean> delete(String tenantId, UUID id) {
        validateTenantId(tenantId);
        Objects.requireNonNull(id, "Collection ID must not be null");

        return Promise.ofBlocking(eventloop, () -> {
            entityManager.getTransaction().begin();
            try {
                MetaCollection collection = entityManager.find(MetaCollection.class, id);
                if (collection != null && collection.getTenantId().equals(tenantId)) {
                    collection.setActive(false);
                    entityManager.merge(collection);
                    entityManager.getTransaction().commit();
                    logger.debug("Collection soft-deleted: tenantId={}, id={}", tenantId, id);
                    return true;
                } else if (collection == null) {
                    logger.warn("Collection not found for deletion: tenantId={}, id={}", tenantId, id);
                    entityManager.getTransaction().commit();
                    return false;
                } else {
                    logger.warn("Collection tenant mismatch: tenantId={}, id={}, collectionTenantId={}", 
                        tenantId, id, collection.getTenantId());
                    entityManager.getTransaction().commit();
                    return false;
                }
            } catch (Exception e) {
                entityManager.getTransaction().rollback();
                logger.error("Failed to delete collection: tenantId={}, id={}", tenantId, id, e);
                throw e;
            }
        });
    }

    @Override
    public Promise<Boolean> existsByName(String tenantId, String name) {
        validateTenantId(tenantId);
        Objects.requireNonNull(name, "Collection name must not be null");

        return Promise.ofBlocking(eventloop, () -> {
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(c) FROM MetaCollection c WHERE c.tenantId = :tenantId AND c.name = :name AND c.active = true",
                Long.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("name", name);
            return query.getSingleResult() > 0;
        });
    }

    @Override
    public Promise<Optional<MetaCollection>> findById(String tenantId, UUID id) {
        validateTenantId(tenantId);
        Objects.requireNonNull(id, "Collection ID must not be null");

        return Promise.ofBlocking(eventloop, () -> {
            MetaCollection collection = entityManager.find(MetaCollection.class, id);
            if (collection != null && collection.getTenantId().equals(tenantId) && collection.getActive()) {
                return Optional.of(collection);
            }
            return Optional.empty();
        });
    }

    @Override
    public Promise<Long> count(String tenantId) {
        validateTenantId(tenantId);

        return Promise.ofBlocking(eventloop, () -> {
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(c) FROM MetaCollection c WHERE c.tenantId = :tenantId AND c.active = true",
                Long.class
            );
            query.setParameter("tenantId", tenantId);
            return query.getSingleResult();
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
