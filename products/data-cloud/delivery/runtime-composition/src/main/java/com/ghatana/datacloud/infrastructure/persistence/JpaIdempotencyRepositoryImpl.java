package com.ghatana.datacloud.infrastructure.persistence;

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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * JPA implementation of IdempotencyRepository with ActiveJ Promise support.
 *
 * <p><b>Purpose</b><br>
 * Provides non-blocking idempotency record persistence operations using JPA and Hibernate.
 * Wraps blocking JPA calls in Promise.ofBlocking() for ActiveJ compatibility.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * @Autowired
 * private IdempotencyRepository repo;
 *
 * Promise<Optional<IdempotencyRecord>> existing = repo.findByKey(
 *     "tenant-123", "orders", "req-abc-123"
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Repository adapter in infrastructure layer (hexagonal architecture)
 * - Implements IdempotencyRepository port from domain layer
 * - Uses JPA EntityManager for persistence
 * - Wraps blocking operations in Promise.ofBlocking()
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. EntityManager is request-scoped via @PersistenceContext.
 *
 * @see IdempotencyRecord
 * @see IdempotencyRepository
 * @doc.type class
 * @doc.purpose JPA adapter for idempotency persistence with ActiveJ Promises
 * @doc.layer product
 * @doc.pattern Repository Adapter (Infrastructure Layer)
 */
public class JpaIdempotencyRepositoryImpl implements IdempotencyRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaIdempotencyRepositoryImpl.class);

    private final ExecutorService dbExecutor;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Creates a repository backed by the default virtual-thread executor.
     */
    public JpaIdempotencyRepositoryImpl() {
        this(JpaThreadPoolConfig.fromEnvironment());
    }

    /**
     * Creates a repository with configurable thread pool.
     *
     * @param config thread pool configuration
     */
    public JpaIdempotencyRepositoryImpl(JpaThreadPoolConfig config) {
        this(Objects.requireNonNull(config, "config must not be null").createExecutorService());
    }

    /**
     * Creates a repository backed by the supplied blocking-work executor.
     *
     * @param dbExecutor executor used for blocking JPA calls
     */
    public JpaIdempotencyRepositoryImpl(ExecutorService dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor must not be null");
    }

    @Override
    public Promise<Optional<IdempotencyRecord>> findByKey(String tenantId, String collectionName, String idempotencyKey) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<IdempotencyRecord> query = entityManager.createQuery(
                "SELECT r FROM IdempotencyRecord r WHERE r.tenantId = :tenantId " +
                "AND r.collectionName = :collectionName AND r.idempotencyKey = :idempotencyKey " +
                "AND r.expiresAt > :now",
                IdempotencyRecord.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            query.setParameter("idempotencyKey", idempotencyKey);
            query.setParameter("now", Instant.now());

            java.util.List<IdempotencyRecord> results = query.getResultList();
            Optional<IdempotencyRecord> result = results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

            log.debug("findByKey: tenantId={}, collection={}, key={}, found={}",
                tenantId, collectionName, idempotencyKey, result.isPresent());
            return result;
        });
    }

    @Override
    public Promise<IdempotencyRecord> save(IdempotencyRecord record) {
        Objects.requireNonNull(record, "Record must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            IdempotencyRecord saved = entityManager.merge(record);
            entityManager.flush();
            log.debug("save: tenantId={}, collection={}, key={}, id={}",
                saved.getTenantId(), saved.getCollectionName(), saved.getIdempotencyKey(), saved.getId());
            return saved;
        });
    }

    @Override
    public Promise<Integer> deleteExpired() {
        return Promise.ofBlocking(dbExecutor, () -> {
            Query query = entityManager.createQuery(
                "DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now"
            );
            query.setParameter("now", Instant.now());
            int deleted = query.executeUpdate();
            log.debug("deleteExpired: deleted={}", deleted);
            return deleted;
        });
    }

    @Override
    public Promise<Void> deleteById(UUID id) {
        Objects.requireNonNull(id, "ID must not be null");

        return Promise.ofBlocking(dbExecutor, () -> {
            Query query = entityManager.createQuery(
                "DELETE FROM IdempotencyRecord r WHERE r.id = :id"
            );
            query.setParameter("id", id);
            query.executeUpdate();
            log.debug("deleteById: id={}", id);
            return null;
        });
    }
}
