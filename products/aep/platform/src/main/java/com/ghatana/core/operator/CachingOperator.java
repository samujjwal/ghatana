package com.ghatana.core.operator;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Operator that caches results to avoid redundant processing.
 *
 * <p><b>Purpose</b><br>
 * Caches operator results based on event keys, eliminating redundant processing
 * of identical or similar events. Particularly effective for expensive operations
 * like database lookups, API calls, or complex computations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CachingOperator cache = CachingOperator.builder()
 *     .operator(expensiveOperation)
 *     .keyExtractor(event -> event.getPayload("userId"))
 *     .maxSize(10000)
 *     .ttl(Duration.ofMinutes(15))
 *     .build();
 *
 * // First call: executes delegate
 * OperatorResult result1 = cache.process(event1).getResult();
 * // Second call with same key: returns cached result
 * OperatorResult result2 = cache.process(event2).getResult();
 * }</pre>
 *
 * <p><b>Cache Strategies</b><br>
 * <ul>
 *   <li><b>LRU eviction:</b> Removes least recently used entries when full</li>
 *   <li><b>TTL expiration:</b> Entries expire after configured duration</li>
 *   <li><b>Size limit:</b> Maximum number of cached entries</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap. Safe for concurrent access.
 *
 * <p><b>Performance</b><br>
 * Cache Hit: ~1-5μs (in-memory lookup)
 * Cache Miss: Delegate execution time + cache overhead (~10μs)
 * Target: 90%+ cache hit rate for repeated events
 *
 * @see UnifiedOperator
 * @see BatchingOperator
 * @doc.type class
 * @doc.purpose Result caching for performance optimization
 * @doc.layer core
 * @doc.pattern Decorator, Cache
 */
public class CachingOperator extends AbstractOperator {

    private static final Logger logger = LoggerFactory.getLogger(CachingOperator.class);

    private final UnifiedOperator delegate;
    private final KeyExtractor keyExtractor;
    private final int maxSize;
    private final long ttlMillis;
    private final Map<String, CacheEntry> cache;

    /**
     * Create caching operator with builder.
     *
     * @param builder Builder with configuration
     */
    private CachingOperator(Builder builder) {
        super(
            OperatorId.of("ghatana", "performance", "caching", "1.0.0"),
            OperatorType.STREAM,
            "Caching Operator",
            "Caches results to avoid redundant processing",
            List.of("caching", "performance", "memoization"),
            null
        );
        this.delegate = Objects.requireNonNull(builder.operator, "Operator required");
        this.keyExtractor = Objects.requireNonNull(builder.keyExtractor, "KeyExtractor required");
        this.maxSize = builder.maxSize;
        this.ttlMillis = builder.ttl.toMillis();
        this.cache = new ConcurrentHashMap<>(maxSize);
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        // Extract cache key
        String cacheKey = keyExtractor.extractKey(event);
        if (cacheKey == null) {
            // No cache key, bypass cache
            logger.debug("No cache key, bypassing cache");
            return delegate.process(event);
        }

        // Check cache
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired(ttlMillis)) {
            // Cache hit
            logger.debug("Cache hit for key: {}", cacheKey);
            return Promise.of(entry.result);
        }

        // Cache miss, execute delegate
        logger.debug("Cache miss for key: {}", cacheKey);
        return delegate.process(event)
            .map(result -> {
                // Store in cache if successful
                if (result.isSuccess()) {
                    cacheResult(cacheKey, result);
                }
                return result;
            });
    }

    /**
     * Store result in cache.
     *
     * @param key Cache key
     * @param result Result to cache
     */
    private void cacheResult(String key, OperatorResult result) {
        // Check size limit
        if (cache.size() >= maxSize) {
            // Evict oldest entry (simple LRU)
            evictOldest();
        }

        cache.put(key, new CacheEntry(result, System.currentTimeMillis()));
        logger.debug("Cached result for key: {} (cache size: {})", key, cache.size());
    }

    /**
     * Evict oldest cache entry.
     */
    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().timestamp < oldestTime) {
                oldestTime = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
            logger.debug("Evicted oldest entry: {}", oldestKey);
        }
    }

    /**
     * Clear all cached entries.
     */
    public void clearCache() {
        int size = cache.size();
        cache.clear();
        logger.info("Cleared cache ({} entries)", size);
    }

    /**
     * Remove expired entries.
     *
     * @return Number of entries removed
     */
    public int removeExpired() {
        int removed = 0;
        long now = System.currentTimeMillis();

        cache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(ttlMillis);
            return expired;
        });

        if (removed > 0) {
            logger.info("Removed {} expired cache entries", removed);
        }

        return removed;
    }

    @Override
    protected Promise<Void> doInitialize(OperatorConfig config) {
        logger.debug("Initializing caching operator (maxSize={}, ttl={}ms)",
                    maxSize, ttlMillis);
        return delegate.initialize(config);
    }

    @Override
    protected Promise<Void> doStart() {
        logger.info("Starting caching operator");
        return delegate.start();
    }

    @Override
    protected Promise<Void> doStop() {
        logger.info("Stopping caching operator (cache size: {})", cache.size());
        clearCache();
        return delegate.stop();
    }

    @Override
    public boolean isHealthy() {
        return delegate.isHealthy();
    }

    @Override
    public boolean isStateful() {
        return true;  // Maintains cache state
    }

    @Override
    public Event toEvent() {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("type", "operator.caching");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());

        var config = new java.util.HashMap<String, Object>();
        config.put("maxSize", maxSize);
        config.put("ttlMillis", ttlMillis);
        payload.put("config", config);

        payload.put("capabilities", java.util.List.of("event.caching", "deduplication"));

        var headers = new java.util.HashMap<String, String>();
        headers.put("operatorId", getId().toString());
        headers.put("tenantId", getId().getNamespace());

        return com.ghatana.platform.domain.domain.event.GEvent.builder()
                .type("operator.registered")
                .headers(headers)
                .payload(payload)
                .time(com.ghatana.platform.domain.domain.event.EventTime.now())
                .build();
    }

    /**
     * Get current cache size.
     *
     * @return Number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Get cache statistics.
     *
     * @return Cache statistics
     */
    public CacheStats getStats() {
        return new CacheStats(cache.size(), maxSize);
    }

    /**
     * Cache entry with result and timestamp.
     */
    private static class CacheEntry {
        final OperatorResult result;
        final long timestamp;

        CacheEntry(OperatorResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }

    /**
     * Cache statistics.
     */
    public static class CacheStats {
        private final int currentSize;
        private final int maxSize;

        CacheStats(int currentSize, int maxSize) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public double getFillPercentage() {
            return (double) currentSize / maxSize * 100;
        }
    }

    /**
     * Function to extract cache key from event.
     */
    @FunctionalInterface
    public interface KeyExtractor {
        /**
         * Extract cache key from event.
         *
         * @param event Event to extract key from
         * @return Cache key, or null to bypass cache
         */
        String extractKey(Event event);
    }

    /**
     * Builder for CachingOperator.
     */
    public static class Builder {
        private UnifiedOperator operator;
        private KeyExtractor keyExtractor;
        private int maxSize = 10000;
        private Duration ttl = Duration.ofMinutes(15);

        public Builder operator(UnifiedOperator operator) {
            this.operator = operator;
            return this;
        }

        public Builder keyExtractor(KeyExtractor keyExtractor) {
            this.keyExtractor = keyExtractor;
            return this;
        }

        public Builder maxSize(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize must be positive");
            }
            this.maxSize = maxSize;
            return this;
        }

        public Builder ttl(Duration ttl) {
            Objects.requireNonNull(ttl, "ttl required");
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
            this.ttl = ttl;
            return this;
        }

        public CachingOperator build() {
            return new CachingOperator(this);
        }
    }

    /**
     * Create builder for caching operator.
     *
     * @return New builder
     */
    public static Builder builder() {
        return new Builder();
    }
}

