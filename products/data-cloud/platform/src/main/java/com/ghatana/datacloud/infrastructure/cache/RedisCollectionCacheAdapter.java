package com.ghatana.datacloud.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis cache adapter for MetaCollection objects using Lettuce.
 *
 * <p><b>Purpose</b><br>
 * Provides caching layer for collection metadata using Redis via Lettuce (async-compatible client).
 * Reduces database queries for frequently accessed collections.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RedisClient redisClient = RedisClient.create("redis://localhost:6379");
 * MetricsCollector metrics = ...;
 * RedisCollectionCacheAdapter cache = new RedisCollectionCacheAdapter(redisClient, metrics);
 *
 * // Get from cache
 * Promise<Optional<MetaCollection>> promise = cache.get("tenant-123", "products");
 * MetaCollection collection = runPromise(() -> promise).orElse(null);
 *
 * // Set in cache
 * runPromise(() -> cache.set(collection, Duration.ofHours(1)));
 *
 * // Invalidate
 * runPromise(() -> cache.delete("tenant-123", "products"));
 * }</pre>
 *
 * <p><b>Cache Key Format</b><br>
 * Keys are tenant-scoped: `collection:{tenantId}:{collectionName}`
 *
 * <p><b>TTL</b><br>
 * Default TTL: 1 hour. Configurable per set operation.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Uses Lettuce's connection pooling and thread-safe API.
 *
 * <p><b>ActiveJ Integration</b><br>
 * All methods return ActiveJ Promise<T> for async operations on EventLoop.
 * Blocking operations (Redis I/O) executed on ForkJoinPool to avoid blocking EventLoop.
 *
 * @see MetaCollection
 * @see RedisClient
 * @doc.type class
 * @doc.purpose Redis cache adapter for collections (Lettuce-based)
 * @doc.layer product
 * @doc.pattern Cache Adapter (Infrastructure Layer)
 */
public class RedisCollectionCacheAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RedisCollectionCacheAdapter.class);
    private static final String CACHE_PREFIX = "collection:";
    private static final int DEFAULT_TTL_SECONDS = 3600; // 1 hour

    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new Redis collection cache adapter using Lettuce.
     *
     * @param redisClient the Lettuce Redis client (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if redisClient or metrics is null
     */
    public RedisCollectionCacheAdapter(RedisClient redisClient, MetricsCollector metrics) {
        this.redisClient = java.util.Objects.requireNonNull(redisClient, "RedisClient must not be null");
        this.metrics = java.util.Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.connection = redisClient.connect();
        this.commands = connection.sync();
        this.objectMapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Gets a collection from cache.
     *
     * <p><b>Cache Hit/Miss</b><br>
     * Metrics are recorded for cache hits and misses.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise of Optional containing collection if found in cache
     */
    public Promise<java.util.Optional<MetaCollection>> get(String tenantId, String collectionName) {
        java.util.Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        java.util.Objects.requireNonNull(collectionName, "Collection name must not be null");

        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            String key = buildKey(tenantId, collectionName);
            
            try {
                String json = commands.get(key);
                
                if (json != null) {
                    metrics.incrementCounter("cache.hit", "type", "collection");
                    MetaCollection collection = objectMapper.readValue(json, MetaCollection.class);
                    logger.debug("Cache hit for collection: {}", key);
                    return java.util.Optional.of(collection);
                } else {
                    metrics.incrementCounter("cache.miss", "type", "collection");
                    logger.debug("Cache miss for collection: {}", key);
                    return java.util.Optional.empty();
                }
            } catch (Exception e) {
                logger.error("Error reading from cache: {}", key, e);
                metrics.incrementCounter("cache.error", "type", "collection", "operation", "get");
                return java.util.Optional.empty();
            }
        });
    }

    /**
     * Sets a collection in cache.
     *
     * @param collection the collection to cache (required)
     * @param ttl the time-to-live duration (required)
     * @return Promise of void
     */
    public Promise<Void> set(MetaCollection collection, java.time.Duration ttl) {
        java.util.Objects.requireNonNull(collection, "Collection must not be null");
        java.util.Objects.requireNonNull(ttl, "TTL must not be null");

        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            String key = buildKey(collection.getTenantId(), collection.getName());
            long ttlSeconds = ttl.getSeconds();
            
            try {
                String json = objectMapper.writeValueAsString(collection);
                commands.setex(key, ttlSeconds, json);
                metrics.incrementCounter("cache.set", "type", "collection");
                logger.debug("Cached collection: {} (TTL: {} seconds)", key, ttlSeconds);
            } catch (Exception e) {
                logger.error("Error writing to cache: {}", key, e);
                metrics.incrementCounter("cache.error", "type", "collection", "operation", "set");
            }
            return null;
        });
    }

    /**
     * Deletes a collection from cache.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise of void
     */
    public Promise<Void> delete(String tenantId, String collectionName) {
        java.util.Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        java.util.Objects.requireNonNull(collectionName, "Collection name must not be null");

        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            String key = buildKey(tenantId, collectionName);
            
            try {
                long deleted = commands.del(key);
                if (deleted > 0) {
                    metrics.incrementCounter("cache.delete", "type", "collection");
                    logger.debug("Deleted from cache: {}", key);
                }
            } catch (Exception e) {
                logger.error("Error deleting from cache: {}", key, e);
                metrics.incrementCounter("cache.error", "type", "collection", "operation", "delete");
            }
            return null;
        });
    }

    /**
     * Invalidates all collections for a tenant.
     *
     * <p><b>Pattern Matching</b><br>
     * Uses Redis SCAN to find all keys matching the tenant pattern.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of void
     */
    public Promise<Void> invalidateAll(String tenantId) {
        java.util.Objects.requireNonNull(tenantId, "Tenant ID must not be null");

        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            String pattern = CACHE_PREFIX + tenantId + ":*";
            
            try {
                ScanCursor cursor = ScanCursor.INITIAL;
                long deleted = 0;
                
                do {
                    KeyScanCursor<String> scanResult = commands.scan(cursor,
                            ScanArgs.Builder.matches(pattern).limit(1000));
                    cursor = scanResult;
                    
                    for (String key : scanResult.getKeys()) {
                        if (key.startsWith(CACHE_PREFIX + tenantId)) {
                            deleted += commands.del(key);
                        }
                    }
                } while (!cursor.isFinished());
                
                if (deleted > 0) {
                    metrics.incrementCounter("cache.invalidate_all", "type", "collection");
                    logger.info("Invalidated {} collections for tenant: {}", deleted, tenantId);
                }
            } catch (Exception e) {
                logger.error("Error invalidating cache for tenant: {}", tenantId, e);
                metrics.incrementCounter("cache.error", "type", "collection", "operation", "invalidate_all");
            }
            return null;
        });
    }

    /**
     * Builds a cache key from tenant ID and collection name.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return the cache key
     */
    private String buildKey(String tenantId, String collectionName) {
        return CACHE_PREFIX + tenantId + ":" + collectionName;
    }

    /**
     * Closes the Redis connection.
     * Called by container lifecycle management.
     */
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}
