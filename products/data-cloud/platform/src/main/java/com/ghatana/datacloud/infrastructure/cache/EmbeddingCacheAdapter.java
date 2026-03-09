package com.ghatana.datacloud.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ForkJoinPool;

/**
 * Redis-backed embedding cache adapter using Lettuce.
 *
 * <p><b>Purpose</b><br>
 * Caches embedding vectors by text hash to avoid redundant API calls.
 * Implements TTL-based expiration and metrics tracking.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RedisClient redisClient = RedisClient.create("redis://localhost:6379");
 * EmbeddingCacheAdapter cache = new EmbeddingCacheAdapter(redisClient, metricsCollector);
 *
 * // Get or compute embedding
 * Promise<float[]> embedding = cache.getOrCompute(
 *   "text to embed",
 *   () -> embeddingService.embed("text to embed")
 * );
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Text hash-based caching with Lettuce
 * - 24-hour TTL
 * - Max 10,000 entries
 * - Hit/miss metrics
 * - Automatic eviction
 * - ActiveJ Promise integration
 *
 * @doc.type class
 * @doc.purpose Embedding vector caching adapter (Lettuce-based)
 * @doc.layer infrastructure
 * @doc.pattern Cache Adapter (Infrastructure Layer)
 */
public class EmbeddingCacheAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingCacheAdapter.class);
    private static final String CACHE_PREFIX = "embedding:";
    private static final long TTL_SECONDS = 24 * 60 * 60; // 24 hours
    private static final int MAX_CACHE_SIZE = 10_000;

    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new Embedding Cache Adapter using Lettuce.
     *
     * @param redisClient the Lettuce Redis client (required)
     * @param metricsCollector the metrics collector (required)
     */
    public EmbeddingCacheAdapter(RedisClient redisClient, MetricsCollector metricsCollector) {
        this.redisClient = java.util.Objects.requireNonNull(redisClient, "RedisClient must not be null");
        this.metricsCollector = java.util.Objects.requireNonNull(metricsCollector, "MetricsCollector must not be null");
        this.connection = redisClient.connect();
        this.commands = connection.sync();
        this.objectMapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Gets embedding from cache or computes it.
     *
     * <p>GIVEN: Text and embedding provider
     * WHEN: getOrCompute() is called
     * THEN: Returns cached embedding or computes and caches new one
     *
     * @param text the text to embed (required)
     * @param provider the embedding provider function (required)
     * @return embedding vector
     */
    public Promise<float[]> getOrCompute(String text, EmbeddingProvider provider) {
        long startTime = System.currentTimeMillis();
        String cacheKeyValue = buildCacheKey(text);

        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            try {
                // Try to get from cache
                String cached = commands.get(cacheKeyValue);
                if (cached != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    metricsCollector.incrementCounter("cache.embedding.hits");
                    logger.debug("Embedding cache hit for text: {} ({}ms)", text.hashCode(), duration);
                    return objectMapper.readValue(cached, float[].class);
                }

                metricsCollector.incrementCounter("cache.embedding.misses");
                logger.debug("Embedding cache miss for text: {}", text.hashCode());
                return null;
            } catch (Exception e) {
                logger.error("Error reading from embedding cache", e);
                metricsCollector.incrementCounter("cache.embedding.errors");
                return null;
            }
        }).then(cached -> {
            if (cached != null) {
                return Promise.of(cached);
            }

            // Compute embedding
            return provider.provide()
                    .then(embedding -> {
                        // Store in cache
                        return Promise.ofBlocking(new ForkJoinPool(), () -> {
                            try {
                                String keyForStorage = buildCacheKey(text);
                                String serialized = objectMapper.writeValueAsString(embedding);
                                commands.setex(keyForStorage, TTL_SECONDS, serialized);
                                
                                // Check cache size and evict if needed
                                checkAndEvictIfNeeded();
                                
                                long duration = System.currentTimeMillis() - startTime;
                                logger.debug("Embedding computed and cached: {} ({}ms)", text.hashCode(), duration);
                                
                                return embedding;
                            } catch (Exception e) {
                                logger.error("Error storing embedding in cache", e);
                                metricsCollector.incrementCounter("cache.embedding.errors");
                                throw e;
                            }
                        });
                    });
        }).mapException(error -> {
            logger.error("Error in embedding cache: {}", error.getMessage());
            metricsCollector.incrementCounter("cache.embedding.errors");
            throw new RuntimeException("Failed to get embedding: " + error.getMessage());
        });
    }

    /**
     * Clears all embeddings from cache.
     *
     * @return void
     */
    public Promise<Void> clear() {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            try {
                ArrayList<String> keysToDelete = new ArrayList<>();
                ScanCursor cursor = ScanCursor.INITIAL;
                
                do {
                    KeyScanCursor<String> scanResult = commands.scan(cursor,
                            ScanArgs.Builder.matches(CACHE_PREFIX + "*"));
                    cursor = scanResult;
                    keysToDelete.addAll(scanResult.getKeys());
                } while (!cursor.isFinished());
                
                if (!keysToDelete.isEmpty()) {
                    commands.del(keysToDelete.toArray(new String[0]));
                    logger.info("Cleared {} embeddings from cache", keysToDelete.size());
                }
            } catch (Exception e) {
                logger.error("Error clearing embedding cache", e);
            }
            return null;
        });
    }

    /**
     * Gets cache statistics.
     *
     * @return cache stats
     */
    public Promise<CacheStats> getStats() {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            try {
                ArrayList<String> keysToCount = new ArrayList<>();
                ScanCursor cursor = ScanCursor.INITIAL;
                long totalMemory = 0;
                
                do {
                    KeyScanCursor<String> scanResult = commands.scan(cursor,
                            ScanArgs.Builder.matches(CACHE_PREFIX + "*"));
                    cursor = scanResult;
                    keysToCount.addAll(scanResult.getKeys());
                } while (!cursor.isFinished());
                
                for (String key : keysToCount) {
                    totalMemory += commands.strlen(key);
                }
                
                return new CacheStats(
                        keysToCount.size(),
                        totalMemory,
                        TTL_SECONDS,
                        MAX_CACHE_SIZE
                );
            } catch (Exception e) {
                logger.error("Error getting cache stats", e);
                return new CacheStats(0, 0, TTL_SECONDS, MAX_CACHE_SIZE);
            }
        });
    }

    /**
     * Builds cache key from text.
     *
     * @param text the text
     * @return cache key
     */
    private String buildCacheKey(String text) {
        int hash = text.hashCode();
        return CACHE_PREFIX + hash;
    }

    /**
     * Checks cache size and evicts oldest entries if needed.
     */
    private void checkAndEvictIfNeeded() {
        try {
            ArrayList<String> keys = new ArrayList<>();
            ScanCursor cursor = ScanCursor.INITIAL;
            
            do {
                KeyScanCursor<String> scanResult = commands.scan(cursor,
                        ScanArgs.Builder.matches(CACHE_PREFIX + "*"));
                cursor = scanResult;
                keys.addAll(scanResult.getKeys());
            } while (!cursor.isFinished());
            
            if (keys.size() > MAX_CACHE_SIZE) {
                int toEvict = keys.size() - MAX_CACHE_SIZE;
                Collections.shuffle(keys);
                
                String[] keysToDelete = new String[toEvict];
                for (int i = 0; i < toEvict; i++) {
                    keysToDelete[i] = keys.get(i);
                }
                
                commands.del(keysToDelete);
                logger.warn("Evicted {} embeddings from cache due to size limit", toEvict);
                metricsCollector.incrementCounter("cache.embedding.evictions");
            }
        } catch (Exception e) {
            logger.error("Error checking/evicting cache", e);
        }
    }

    /**
     * Closes the Redis connection.
     */
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    /**
     * Embedding provider function.
     */
    @FunctionalInterface
    public interface EmbeddingProvider {
        Promise<float[]> provide();
    }

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
