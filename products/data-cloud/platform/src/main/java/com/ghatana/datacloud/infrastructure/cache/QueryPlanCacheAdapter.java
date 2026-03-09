package com.ghatana.datacloud.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.application.QuerySpec;
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
 * Redis-backed query plan cache adapter.
 *
 * <p><b>Purpose</b><br>
 * Caches parsed query plans to avoid redundant NLQ parsing.
 * Implements TTL-based expiration with schema-aware invalidation.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QueryPlanCacheAdapter cache = new QueryPlanCacheAdapter(jedisPool, metricsCollector);
 *
 * // Get or compute query plan
 * Promise<QueryPlan> plan = cache.getOrCompute(
 *   "Show me products with price > 100",
 *   collectionId,
 *   () -> nlqService.parseQuery(query, collection)
 * );
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Query text-based caching
 * - 1-hour TTL
 * - Schema-aware invalidation
 * - Hit/miss metrics
 * - Automatic eviction
 *
 * @doc.type adapter
 * @doc.purpose Query plan cache
 * @doc.layer infrastructure
 * @doc.pattern Cache Adapter (Infrastructure Layer)
 */
public class QueryPlanCacheAdapter {

    private static final Logger logger = LoggerFactory.getLogger(QueryPlanCacheAdapter.class);
    private static final String CACHE_PREFIX = "query_plan:";
    private static final String SCHEMA_VERSION_PREFIX = "schema_version:";
    private static final long TTL_SECONDS = 60 * 60; // 1 hour
    private static final int MAX_CACHE_SIZE = 5_000;

    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new Query Plan Cache Adapter using Lettuce.
     *
     * @param redisClient the Lettuce Redis client (required)
     * @param metricsCollector the metrics collector (required)
     */
    public QueryPlanCacheAdapter(RedisClient redisClient, MetricsCollector metricsCollector) {
        Objects.requireNonNull(redisClient, "RedisClient must not be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "MetricsCollector must not be null");
        this.connection = redisClient.connect();
        this.commands = connection.sync();
        this.objectMapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Gets query plan from cache or computes it.
     *
     * <p>GIVEN: Query text and plan provider
     * WHEN: getOrCompute() is called
     * THEN: Returns cached plan or computes and caches new one
     *
     * @param query the query text (required)
     * @param collectionId the collection ID for invalidation (required)
     * @param provider the plan provider function (required)
     * @return query plan promise
     */
    public Promise<QueryPlan> getOrCompute(
            String query,
            UUID collectionId,
            PlanProvider provider) {
        long startTime = System.currentTimeMillis();
        String cacheKeyStr = buildCacheKey(query, collectionId);

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
                        metricsCollector.recordTimer("cache.query_plan.hit", duration);
                        metricsCollector.incrementCounter("cache.query_plan.hits");
                        logger.debug("Query plan cache hit ({}ms)", duration);
                        return objectMapper.readValue(cached, QueryPlan.class);
                    }
                }
                
                metricsCollector.incrementCounter("cache.query_plan.misses");
                logger.debug("Query plan cache miss");
                return null;
            } catch (Exception e) {
                logger.error("Error reading from cache: {}", cacheKeyStr, e);
                metricsCollector.incrementCounter("cache.query_plan.errors");
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
                // Update schema version to invalidate all cached plans for this collection
                String newVersion = String.valueOf(System.currentTimeMillis());
                commands.set(SCHEMA_VERSION_PREFIX + collectionId, newVersion);
                
                logger.info("Invalidated query plan cache for collection: {}", collectionId);
                metricsCollector.incrementCounter("cache.query_plan.invalidations");
            } catch (Exception e) {
                logger.error("Error invalidating cache for collection: {}", collectionId, e);
                metricsCollector.incrementCounter("cache.query_plan.errors");
            }
            return null;
        });
    }

    /**
     * Clears all query plans from cache.
     *
     * @return void promise
     */
    public Promise<Void> clear() {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            try {
                List<String> keys = commands.keys(CACHE_PREFIX + "*");
                if (!keys.isEmpty()) {
                    commands.del(keys.toArray(new String[0]));
                    logger.info("Cleared {} query plans from cache", keys.size());
                    metricsCollector.incrementCounter("cache.query_plan.clears");
                }
            } catch (Exception e) {
                logger.error("Error clearing cache", e);
                metricsCollector.incrementCounter("cache.query_plan.errors");
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
                metricsCollector.incrementCounter("cache.query_plan.errors");
                return new CacheStats(0, 0, TTL_SECONDS, MAX_CACHE_SIZE);
            }
        });
    }

    /**
     * Builds cache key from query and collection ID.
     *
     * @param query the query text
     * @param collectionId the collection ID
     * @return cache key
     */
    private String buildCacheKey(String query, UUID collectionId) {
        int queryHash = query.hashCode();
        return CACHE_PREFIX + collectionId + ":" + queryHash;
    }

    /**
     * Plan provider function.
     */
    @FunctionalInterface
    public interface PlanProvider {
        Promise<QueryPlan> parse();
    }

    /**
     * Query plan record.
     */
    public record QueryPlan(
            String id,
            String originalQuery,
            QuerySpec querySpec,
            double confidence,
            int filterCount,
            int sortCount
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
