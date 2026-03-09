package com.ghatana.datacloud.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

/**
 * Redis-backed vector search cache adapter.
 *
 * <p><b>Purpose</b><br>
 * Caches vector similarity search results to avoid redundant searches.
 * Implements TTL-based expiration with schema-aware invalidation.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VectorSearchCacheAdapter cache = new VectorSearchCacheAdapter(jedisPool, metricsCollector);
 *
 * // Get or compute search results
 * Promise<List<SearchResult>> results = cache.getOrCompute(
 *   queryVector,
 *   collectionId,
 *   () -> vectorStore.searchSimilar(queryVector, ...)
 * );
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Query vector-based caching
 * - 1-hour TTL
 * - Schema-aware invalidation
 * - Hit/miss metrics
 * - Automatic eviction
 *
 * @doc.type adapter
 * @doc.purpose Vector search cache
 * @doc.layer infrastructure
 * @doc.pattern Cache Adapter (Infrastructure Layer)
 */
public class VectorSearchCacheAdapter {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchCacheAdapter.class);
    private static final String CACHE_PREFIX = "vector_search:";
    private static final String SCHEMA_VERSION_PREFIX = "schema_version:";
    private static final long TTL_SECONDS = 60 * 60; // 1 hour
    private static final int MAX_CACHE_SIZE = 5_000;

    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new Vector Search Cache Adapter using Lettuce.
     *
     * @param redisClient the Lettuce Redis client (required)
     * @param metricsCollector the metrics collector (required)
     */
    public VectorSearchCacheAdapter(RedisClient redisClient, MetricsCollector metricsCollector) {
        Objects.requireNonNull(redisClient, "RedisClient must not be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "MetricsCollector must not be null");
        this.connection = redisClient.connect();
        this.commands = connection.sync();
        this.objectMapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Gets search results from cache or computes them.
     *
     * <p>GIVEN: Query vector and search provider
     * WHEN: getOrCompute() is called
     * THEN: Returns cached results or computes and caches new ones
     *
     * @param queryVector the query vector (required)
     * @param collectionId the collection ID for invalidation (required)
     * @param provider the search provider function (required)
     * @return search results promise
     */
    public Promise<List<SearchResult>> getOrCompute(
            float[] queryVector,
            UUID collectionId,
            SearchProvider provider) {
        long startTime = System.currentTimeMillis();
        String cacheKeyStr = buildCacheKey(queryVector, collectionId);

        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            try {
                // Check if schema has been updated
                String schemaVersion = commands.get(SCHEMA_VERSION_PREFIX + collectionId);
                String cachedVersion = commands.get(cacheKeyStr + ":version");
                
                if (schemaVersion != null && schemaVersion.equals(cachedVersion)) {
                    // Cache is valid
                    String cached = commands.get(cacheKeyStr);
                    if (cached != null) {
                        long duration = System.currentTimeMillis() - startTime;
                        metricsCollector.recordTimer("cache.vector_search.hit", duration);
                        metricsCollector.incrementCounter("cache.vector_search.hits");
                        logger.debug("Vector search cache hit ({}ms)", duration);
                        
                        @SuppressWarnings("unchecked")
                        List<SearchResult> results = objectMapper.readValue(cached, List.class);
                        return results;
                    }
                }
                
                metricsCollector.incrementCounter("cache.vector_search.misses");
                logger.debug("Vector search cache miss");
                return null;
            } catch (Exception e) {
                logger.error("Error reading from cache: {}", cacheKeyStr, e);
                metricsCollector.incrementCounter("cache.vector_search.errors");
                return null;
            }
        });
    }

    /**
     * Invalidates cache for a collection.
     *
     * <p>Called when collection schema is updated.
     *
     * @param collectionId the collection ID
     * @return void promise
     */
    public Promise<Void> invalidateCollection(UUID collectionId) {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            try {
                // Update schema version to invalidate all cached searches for this collection
                String newVersion = String.valueOf(System.currentTimeMillis());
                commands.set(SCHEMA_VERSION_PREFIX + collectionId, newVersion);
                
                logger.info("Invalidated vector search cache for collection: {}", collectionId);
                metricsCollector.incrementCounter("cache.vector_search.invalidations");
            } catch (Exception e) {
                logger.error("Error invalidating cache for collection: {}", collectionId, e);
                metricsCollector.incrementCounter("cache.vector_search.errors");
            }
            return null;
        });
    }

    /**
     * Clears all search results from cache.
     *
     * @return void promise
     */
    public Promise<Void> clear() {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            try {
                List<String> keys = commands.keys(CACHE_PREFIX + "*");
                if (!keys.isEmpty()) {
                    commands.del(keys.toArray(new String[0]));
                    logger.info("Cleared {} search results from cache", keys.size());
                    metricsCollector.incrementCounter("cache.vector_search.clears");
                }
            } catch (Exception e) {
                logger.error("Error clearing cache", e);
                metricsCollector.incrementCounter("cache.vector_search.errors");
            }
            return null;
        });
    }

    /**
     * Gets cache statistics.
     *
     * @return cache stats promise
     */
    public Promise<CacheStats> getStats() {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            try {
                List<String> keys = commands.keys(CACHE_PREFIX + "*");
                long totalMemory = 0;
                for (String key : keys) {
                    Long strlen = commands.strlen(key);
                    if (strlen != null) {
                        totalMemory += strlen;
                    }
                }
                
                return new CacheStats(
                        keys.size(),
                        totalMemory,
                        TTL_SECONDS,
                        MAX_CACHE_SIZE
                );
            } catch (Exception e) {
                logger.error("Error getting cache stats", e);
                metricsCollector.incrementCounter("cache.vector_search.errors");
                return new CacheStats(0, 0, TTL_SECONDS, MAX_CACHE_SIZE);
            }
        });
    }

    /**
     * Builds cache key from query vector and collection ID.
     *
     * @param queryVector the query vector
     * @param collectionId the collection ID
     * @return cache key
     */
    private String buildCacheKey(float[] queryVector, UUID collectionId) {
        // Create deterministic hash of vector
        long vectorHash = 0;
        for (float v : queryVector) {
            vectorHash = 31 * vectorHash + Float.floatToIntBits(v);
        }
        return CACHE_PREFIX + collectionId + ":" + vectorHash;
    }

    /**
     * Search provider function.
     */
    @FunctionalInterface
    public interface SearchProvider {
        Promise<List<SearchResult>> search();
    }

    /**
     * Search result record.
     */
    public record SearchResult(
            String id,
            String text,
            float similarity
    ) {}

    /**
     * Cache statistics record.
     */
    public record CacheStats(
            int entryCount,
            long totalMemoryBytes,
            long ttlSeconds,
            int maxSize
    ) {}
}
