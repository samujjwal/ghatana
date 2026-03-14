package com.ghatana.datacloud.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * L1 in-process caching layer for {@link EntityRepository}.
 *
 * <h2>Design</h2>
 * <p>Decorator pattern: wraps a delegate {@link EntityRepository} and interposes a
 * {@link Cache} built on <a href="https://github.com/ben-manes/caffeine">Caffeine 3</a>
 * between callers and the underlying database. The cache runs entirely within the
 * JVM process — no network hop, no serialization — so a L1 hit costs O(ns) vs O(ms)
 * for a PostgreSQL round trip.
 *
 * <h2>Cache-Aside Pattern</h2>
 * <ul>
 *   <li>{@link #findById} — check L1 first; on miss, delegate and populate L1.</li>
 *   <li>{@link #save}     — always delegates; invalidates the entry on success.</li>
 *   <li>{@link #delete}   — always delegates; invalidates the entry on success.</li>
 *   <li>All other read operations ({@link #findAll}, {@link #findByQuery},
 *       {@link #exists}, {@link #count}, {@link #countByFilter}) always delegate
 *       through. These are collection-scoped reads that are harder to invalidate
 *       correctly; the L1 win is on high-frequency single-entity point lookups.
 * </ul>
 *
 * <h2>Cache Key</h2>
 * <p>Keys are {@code "<tenantId>:<collectionName>:<entityId>"}, scoping each entry
 * to the (tenant, collection, entity) triple to satisfy multi-tenancy isolation.
 *
 * <h2>Tuning Defaults</h2>
 * <ul>
 *   <li>TTL after write: 5 seconds (suitable for high-read, low-write workloads)</li>
 *   <li>Maximum size: 10,000 entries (heap-efficient; Caffeine evicts by W-TinyLFU)</li>
 * </ul>
 * Both parameters are configurable via the constructor overload.
 *
 * <h2>Observability</h2>
 * <p>Emits Micrometer counters for {@code data_cloud.l1_cache.hits} and
 * {@code data_cloud.l1_cache.misses} tagged with {@code operation=findById}.
 *
 * <h2>Thread Safety</h2>
 * <p>Caffeine's {@link Cache} is thread-safe. Concurrent reads for the same key
 * may all miss and trigger concurrent fetches (no "thundering herd" protection
 * by design — {@code get(k, loader)} is not used to avoid blocking the Eventloop
 * thread or muddling ActiveJ's async ownership).
 *
 * @doc.type class
 * @doc.purpose L1 in-process Caffeine cache decorator for EntityRepository
 * @doc.layer product
 * @doc.pattern Decorator, CacheAside
 */
public class CachingEntityRepository implements EntityRepository {

    /** Default TTL: 5 seconds. Keeps stale risk low for frequently-mutated entities. */
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(5);

    /** Default max entries. ~10 k × ~2 KB/entity ≈ 20 MB heap overhead. */
    public static final long DEFAULT_MAX_SIZE = 10_000L;

    private static final Logger LOG = LoggerFactory.getLogger(CachingEntityRepository.class);

    private final EntityRepository delegate;
    private final Cache<String, Optional<Entity>> l1Cache;

    // Micrometer counters — lazily created, null if no MeterRegistry supplied
    private final Counter hitCounter;
    private final Counter missCounter;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a caching repository with default TTL (5 s) and max-size (10 k).
     *
     * @param delegate      the backing repository (required)
     * @param meterRegistry Micrometer registry for cache metrics (may be null to
     *                      skip metrics instrumentation)
     */
    public CachingEntityRepository(EntityRepository delegate, MeterRegistry meterRegistry) {
        this(delegate, meterRegistry, DEFAULT_TTL, DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a caching repository with explicit TTL and max-size.
     *
     * @param delegate      the backing repository (required)
     * @param meterRegistry Micrometer registry for cache metrics (may be null)
     * @param ttl           TTL after a write to the cache (required, must be positive)
     * @param maxSize       maximum number of cache entries (required, must be ≥ 1)
     */
    public CachingEntityRepository(
            EntityRepository delegate,
            MeterRegistry meterRegistry,
            Duration ttl,
            long maxSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, got: " + ttl);
        }
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be >= 1, got: " + maxSize);
        }

        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();

        if (meterRegistry != null) {
            this.hitCounter = Counter.builder("data_cloud.l1_cache.hits")
                    .description("Number of L1 cache hits for findById")
                    .tag("operation", "findById")
                    .register(meterRegistry);
            this.missCounter = Counter.builder("data_cloud.l1_cache.misses")
                    .description("Number of L1 cache misses for findById")
                    .tag("operation", "findById")
                    .register(meterRegistry);
        } else {
            this.hitCounter = null;
            this.missCounter = null;
        }
    }

    // -------------------------------------------------------------------------
    // findById — the primary hot path for L1 caching
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Checks the L1 cache first. On a hit, returns the cached {@link Optional}
     * without touching the database. On a miss, delegates to the backing repository
     * and populates the cache with the result (including empty optionals, so repeated
     * misses for non-existent entities also avoid DB round trips).
     */
    @Override
    public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) {
        String key = cacheKey(tenantId, collectionName, entityId);
        Optional<Entity> cached = l1Cache.getIfPresent(key);
        if (cached != null) {
            LOG.trace("[L1 hit] findById key={}", key);
            incrementHit();
            return Promise.of(cached);
        }

        LOG.trace("[L1 miss] findById key={}", key);
        incrementMiss();

        return delegate.findById(tenantId, collectionName, entityId)
                .map(result -> {
                    l1Cache.put(key, result);
                    return result;
                });
    }

    // -------------------------------------------------------------------------
    // Mutations — always delegate, then invalidate the cache entry
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the backing repository. On success, invalidates the L1 entry
     * for the saved entity's (tenantId, collectionName, id) triple so subsequent
     * reads see the new state.
     */
    @Override
    public Promise<Entity> save(String tenantId, Entity entity) {
        return delegate.save(tenantId, entity)
                .map(saved -> {
                    if (saved.getId() != null) {
                        String key = cacheKey(tenantId, saved.getCollectionName(), saved.getId());
                        l1Cache.invalidate(key);
                        LOG.trace("[L1 invalidate] save key={}", key);
                    }
                    return saved;
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the backing repository. On success, invalidates L1 entries
     * for all saved entities.
     */
    @Override
    public Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities) {
        return delegate.saveAll(tenantId, entities)
                .map(saved -> {
                    for (Entity entity : saved) {
                        if (entity.getId() != null) {
                            String key = cacheKey(tenantId, entity.getCollectionName(), entity.getId());
                            l1Cache.invalidate(key);
                        }
                    }
                    LOG.trace("[L1 invalidate] saveAll {} entries for tenant={}", saved.size(), tenantId);
                    return saved;
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the backing repository. On success, invalidates the L1 entry
     * for the deleted entity.
     */
    @Override
    public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) {
        return delegate.delete(tenantId, collectionName, entityId)
                .map(ignored -> {
                    String key = cacheKey(tenantId, collectionName, entityId);
                    l1Cache.invalidate(key);
                    LOG.trace("[L1 invalidate] delete key={}", key);
                    return null;
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the backing repository. On success, invalidates all L1 entries
     * for the deleted entities.
     */
    @Override
    public Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds) {
        return delegate.deleteAll(tenantId, collectionName, entityIds)
                .map(ignored -> {
                    for (UUID id : entityIds) {
                        String key = cacheKey(tenantId, collectionName, id);
                        l1Cache.invalidate(key);
                    }
                    LOG.trace("[L1 invalidate] deleteAll {} entries in {}/{}", entityIds.size(), tenantId, collectionName);
                    return null;
                });
    }

    // -------------------------------------------------------------------------
    // Collection-scoped operations — always pass through
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Always delegates. Collection-level results span many entries and cannot
     * be cheaply invalidated; caching them would risk stale reads after writes.
     */
    @Override
    public Promise<List<Entity>> findAll(
            String tenantId,
            String collectionName,
            Map<String, Object> filter,
            String sort,
            int offset,
            int limit) {
        return delegate.findAll(tenantId, collectionName, filter, sort, offset, limit);
    }

    /** {@inheritDoc} Always delegates. */
    @Override
    public Promise<List<Entity>> findByQuery(String tenantId, String collectionName, Object querySpec) {
        return delegate.findByQuery(tenantId, collectionName, querySpec);
    }

    /** {@inheritDoc} Always delegates. */
    @Override
    public Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId) {
        return delegate.exists(tenantId, collectionName, entityId);
    }

    /** {@inheritDoc} Always delegates. */
    @Override
    public Promise<Long> count(String tenantId, String collectionName) {
        return delegate.count(tenantId, collectionName);
    }

    /** {@inheritDoc} Always delegates. */
    @Override
    public Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter) {
        return delegate.countByFilter(tenantId, collectionName, filter);
    }

    // -------------------------------------------------------------------------
    // Cache management utilities
    // -------------------------------------------------------------------------

    /**
     * Evicts all entries for the given (tenantId, collectionName) combination.
     *
     * <p>Use this after bulk write operations (e.g., migration backfills) that
     * mutate many entities in a collection without going through {@link #save}.
     *
     * @param tenantId       the tenant whose cache entries should be evicted (required)
     * @param collectionName the collection name prefix to evict (required)
     */
    public void invalidateCollection(String tenantId, String collectionName) {
        String prefix = tenantId + ":" + collectionName + ":";
        l1Cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        LOG.debug("[L1 invalidate-collection] tenant={} collection={}", tenantId, collectionName);
    }

    /**
     * Evicts all L1 cache entries. Useful during test teardown or after a
     * major schema change.
     */
    public void invalidateAll() {
        l1Cache.invalidateAll();
        LOG.debug("[L1 invalidate-all]");
    }

    /**
     * Returns the current estimated number of entries in the L1 cache.
     *
     * @return estimated cache size (may be approximate due to eviction in progress)
     */
    public long estimatedSize() {
        return l1Cache.estimatedSize();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String cacheKey(String tenantId, String collectionName, UUID entityId) {
        return tenantId + ":" + collectionName + ":" + entityId;
    }

    private void incrementHit() {
        if (hitCounter != null) {
            hitCounter.increment();
        }
    }

    private void incrementMiss() {
        if (missCounter != null) {
            missCounter.increment();
        }
    }
}
