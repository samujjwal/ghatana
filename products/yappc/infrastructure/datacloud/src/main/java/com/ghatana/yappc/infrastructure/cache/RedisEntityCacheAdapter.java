package com.ghatana.yappc.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCacheService;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.observability.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed entity cache adapter for YAPPC using platform DistributedCacheService.
 *
 * <p><b>Purpose</b><br>
 * Provides distributed Redis caching for YAPPC entities with tenant isolation,
 * TTL support, and cache hit/miss metrics. Wraps the platform DistributedCacheService
 * to provide YAPPC-specific caching semantics.
 *
 * <p><b>Features</b><br>
 * - Redis-backed distributed caching<br>
 * - Tenant-scoped cache keys<br>
 * - TTL-based expiration<br>
 * - Cache hit/miss metrics<br>
 * - Graceful degradation (returns empty on cache errors)<br>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RedisEntityCacheAdapter<ProjectEntity> cache = new RedisEntityCacheAdapter<>(
 *     distributedCacheService,
 *     objectMapper,
 *     Duration.ofMinutes(5),
 *     "projects");
 *
 * // Get or load
 * Optional<ProjectEntity> project = cache.get(projectId);
 * if (project.isEmpty()) {
 *     project = loadFromDataCloud(projectId);
 *     cache.put(projectId, project.get());
 * }
 * }</pre>
 *
 * @param <T> The entity type
 *
 * @doc.type class
 * @doc.purpose Redis-backed entity cache adapter for YAPPC
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class RedisEntityCacheAdapter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(RedisEntityCacheAdapter.class);
    private static final String CACHE_HIT_METRIC = "cache.hit";
    private static final String CACHE_MISS_METRIC = "cache.miss";
    private static final String CACHE_ERROR_METRIC = "cache.error";

    private final DistributedCacheService distributedCacheService;
    private final ObjectMapper objectMapper;
    private final Duration defaultTtl;
    private final String collectionName;

    /**
     * Creates a Redis-backed entity cache adapter.
     *
     * @param distributedCacheService platform distributed cache service
     * @param objectMapper Jackson ObjectMapper for serialization
     * @param defaultTtl default TTL for cached entries
     * @param collectionName collection name for cache key prefix
     */
    public RedisEntityCacheAdapter(
            DistributedCacheService distributedCacheService,
            ObjectMapper objectMapper,
            Duration defaultTtl,
            String collectionName) {
        this.distributedCacheService = distributedCacheService;
        this.objectMapper = objectMapper;
        this.defaultTtl = defaultTtl;
        this.collectionName = collectionName;
    }

    /**
     * Retrieves entity from cache if present and not expired.
     *
     * @param key the cache key (entity ID)
     * @return optional containing entity if cached
     */
    public Optional<T> get(String key) {
        try {
            String cacheKey = buildCacheKey(key);
            Optional<T> cached = distributedCacheService.get(cacheKey, Object.class)
                    .map(obj -> objectMapper.convertValue(obj, Object.class));

            if (cached.isPresent()) {
                MetricsRegistry.incrementCounter(CACHE_HIT_METRIC, "collection", collectionName);
                LOG.debug("Cache hit for {}:{}", collectionName, key);
                return cached;
            } else {
                MetricsRegistry.incrementCounter(CACHE_MISS_METRIC, "collection", collectionName);
                LOG.debug("Cache miss for {}:{}", collectionName, key);
                return Optional.empty();
            }
        } catch (Exception e) {
            MetricsRegistry.incrementCounter(CACHE_ERROR_METRIC, "collection", collectionName);
            LOG.warn("Cache get error for {}:{}", collectionName, key, e);
            return Optional.empty();
        }
    }

    /**
     * Stores entity in cache with default TTL.
     *
     * @param key the cache key (entity ID)
     * @param value the entity to cache
     */
    public void put(String key, T value) {
        put(key, value, defaultTtl);
    }

    /**
     * Stores entity in cache with custom TTL.
     *
     * @param key the cache key (entity ID)
     * @param value the entity to cache
     * @param ttl custom TTL for this entry
     */
    public void put(String key, T value, Duration ttl) {
        try {
            String cacheKey = buildCacheKey(key);
            distributedCacheService.put(cacheKey, value, ttl.getSeconds());
            LOG.debug("Cached {}:{}", collectionName, key);
        } catch (Exception e) {
            MetricsRegistry.incrementCounter(CACHE_ERROR_METRIC, "collection", collectionName);
            LOG.warn("Cache put error for {}:{}", collectionName, key, e);
            // Non-blocking: cache write failures are non-critical
        }
    }

    /**
     * Removes entity from cache.
     *
     * @param key the cache key (entity ID)
     */
    public void invalidate(String key) {
        try {
            String cacheKey = buildCacheKey(key);
            distributedCacheService.invalidate(cacheKey);
            LOG.debug("Invalidated cache for {}:{}", collectionName, key);
        } catch (Exception e) {
            MetricsRegistry.incrementCounter(CACHE_ERROR_METRIC, "collection", collectionName);
            LOG.warn("Cache invalidate error for {}:{}", collectionName, key, e);
        }
    }

    /**
     * Clears all cached entries for this collection.
     */
    public void clear() {
        try {
            String pattern = buildCachePattern();
            distributedCacheService.invalidatePattern(pattern);
            LOG.info("Cleared cache for pattern: {}", pattern);
        } catch (Exception e) {
            MetricsRegistry.incrementCounter(CACHE_ERROR_METRIC, "collection", collectionName);
            LOG.warn("Cache clear error for {}", collectionName, e);
        }
    }

    /**
     * Returns cache statistics.
     *
     * @return cache statistics with key count and size
     */
    public DistributedCacheService.CacheStatistics getStatistics() {
        try {
            String pattern = buildCachePattern();
            return distributedCacheService.getStatistics(pattern);
        } catch (Exception e) {
            LOG.warn("Cache statistics error for {}", collectionName, e);
            return new DistributedCacheService.CacheStatistics(0, 0);
        }
    }

    /**
     * Builds tenant-scoped cache key.
     */
    private String buildCacheKey(String key) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context required for caching");
        }
        return String.format("%s:%s:%s", tenantId, collectionName, key);
    }

    /**
     * Builds cache pattern for tenant-scoped queries.
     */
    private String buildCachePattern() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context required for caching");
        }
        return String.format("tenant:%s:%s:*", tenantId, collectionName);
    }
}
