package com.ghatana.yappc.ai.router;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Semantic cache for AI responses with similarity matching.
 * 
 * <p>Uses semantic fingerprinting to cache and retrieve similar requests.
 * Supports TTL-based expiration and LRU eviction.
 * 
 * @doc.type class
 * @doc.purpose AI response caching with semantic matching
 
 * @doc.layer core
 * @doc.pattern ValueObject
* @doc.gaa.memory semantic
*/
public final class SemanticCache {
    
    private static final Logger logger = LoggerFactory.getLogger(SemanticCache.class);
    
    private final Map<String, CachedResponse> cache;
    private final CacheConfig config;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final ScheduledExecutorService evictionScheduler;
    
    public SemanticCache(CacheConfig config) {
        this.config = config;
        this.cache = new ConcurrentHashMap<>();
        
        // Schedule periodic eviction
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "semantic-cache-eviction");
            t.setDaemon(true);
            return t;
        });
        evictionScheduler.scheduleAtFixedRate(this::evictExpiredEntries, 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * Gets cached response for request if available.
     * 
     * @param request the AI request
     * @return Promise containing cached response or null
     */
    public Promise<AIResponse> get(AIRequest request) {
        return Promise.ofCallback(cb -> {
            if (!config.isEnabled()) {
                misses.incrementAndGet();
                cb.set(null);
                return;
            }
            
            String fingerprint = request.getSemanticFingerprint();
            CachedResponse cached = cache.get(fingerprint);
            
            if (cached != null && !cached.isExpired()) {
                hits.incrementAndGet();
                cached.updateAccessTime();
                logger.debug("Cache hit for fingerprint: {}", fingerprint);
                cb.set(cached.response);
            } else {
                misses.incrementAndGet();
                if (cached != null) {
                    cache.remove(fingerprint); // Remove expired entry
                }
                cb.set(null);
            }
        });
    }
    
    /**
     * Caches an AI response.
     * 
     * @param request the AI request
     * @param response the AI response
     */
    public void put(AIRequest request, AIResponse response) {
        if (!config.isEnabled()) {
            return;
        }
        
        String fingerprint = request.getSemanticFingerprint();
        
        // Check cache size limit
        if (cache.size() >= config.getMaxSize()) {
            evictLRU();
        }
        
        cache.put(fingerprint, new CachedResponse(response, config.getTtlSeconds()));
        logger.debug("Cached response for fingerprint: {}", fingerprint);
    }
    
    /**
     * Clears all cached responses.
     */
    public void clear() {
        cache.clear();
        hits.set(0);
        misses.set(0);
        logger.info("Cache cleared");
    }
    
    /**
     * Gets cache statistics.
     */
    public CacheStatistics getStatistics() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;
        double hitRate = total > 0 ? (double) totalHits / total : 0.0;
        
        return new CacheStatistics(
            cache.size(),
            totalHits,
            totalMisses,
            hitRate
        );
    }
    
    /**
     * Evicts least recently used entry.
     */
    private void evictLRU() {
        Optional<Map.Entry<String, CachedResponse>> lruEntry = cache.entrySet().stream()
            .min(Comparator.comparingLong(e -> e.getValue().lastAccessTime));
        
        lruEntry.ifPresent(entry -> {
            cache.remove(entry.getKey());
            logger.debug("Evicted LRU entry: {}", entry.getKey());
        });
    }
    
    /**
     * Evicts expired entries from the cache. Called periodically by the scheduler.
     */
    private void evictExpiredEntries() {
        int removed = 0;
        Iterator<Map.Entry<String, CachedResponse>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedResponse> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            logger.debug("Evicted {} expired cache entries", removed);
        }
    }
    
    /**
     * Cached response wrapper with TTL.
     */
    private static class CachedResponse {
        final AIResponse response;
        final long expiresAt;
        volatile long lastAccessTime;
        
        CachedResponse(AIResponse response, long ttlSeconds) {
            this.response = response;
            this.lastAccessTime = System.currentTimeMillis();
            this.expiresAt = lastAccessTime + (ttlSeconds * 1000);
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
        
        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Cache configuration.
     */
    public static final class CacheConfig {
        private final boolean enabled;
        private final int maxSize;
        private final long ttlSeconds;
        
        private CacheConfig(Builder builder) {
            this.enabled = builder.enabled;
            this.maxSize = builder.maxSize;
            this.ttlSeconds = builder.ttlSeconds;
        }
        
        public boolean isEnabled() { return enabled; }
        public int getMaxSize() { return maxSize; }
        public long getTtlSeconds() { return ttlSeconds; }
        
        public static CacheConfig defaults() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean enabled = true;
            private int maxSize = 10000;
            private long ttlSeconds = 3600; // 1 hour
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder maxSize(int maxSize) {
                this.maxSize = maxSize;
                return this;
            }
            
            public Builder ttlSeconds(long ttlSeconds) {
                this.ttlSeconds = ttlSeconds;
                return this;
            }
            
            public CacheConfig build() {
                return new CacheConfig(this);
            }
        }
    }
}
