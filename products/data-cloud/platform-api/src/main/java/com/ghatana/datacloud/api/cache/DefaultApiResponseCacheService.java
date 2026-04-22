/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.cache;

import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of API response cache service.
 *
 * <p>Provides in-memory caching for HTTP responses with:
 * <ul>
 *   <li>Thread-safe cache operations using ConcurrentHashMap</li>
 *   <li>TTL-based expiration</li>
 *   <li>Cache statistics tracking</li>
 *   <li>Multi-tenant isolation support</li>
 *   <li>Pattern-based invalidation</li>
 * </ul>
 *
 * <p>Cache entries include:
 * <ul>
 *   <li>Response body bytes</li>
 *   <li>HTTP status code</li>
 *   <li>Response headers</li>
 *   <li>Expiration timestamp</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose In-memory cache for API responses with TTL and statistics
 * @doc.layer product
 * @doc.pattern Cache, Service Implementation
 */
public class DefaultApiResponseCacheService implements ApiResponseCacheService {

    private static final Logger log = LoggerFactory.getLogger(DefaultApiResponseCacheService.class);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHE_SIZE = 10000;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong invalidations = new AtomicLong(0);
    private final Executor executor;

    /**
     * Creates a new API response cache service with default executor.
     */
    public DefaultApiResponseCacheService() {
        this(ForkJoinPool.commonPool());
    }

    /**
     * Creates a new API response cache service with custom executor.
     *
     * @param executor executor for blocking operations
     */
    public DefaultApiResponseCacheService(Executor executor) {
        this.executor = executor;
    }

    /**
     * Cache entry with expiration.
     */
    private static class CacheEntry {
        final HttpResponse response;
        final Instant expiresAt;

        CacheEntry(HttpResponse response, Instant expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    @Override
    public Promise<Void> put(String cacheKey, HttpResponse response, Duration ttl) {
        return Promise.ofBlocking(executor, () -> {
            Instant expiresAt = Instant.now().plus(ttl);
            cache.put(cacheKey, new CacheEntry(response, expiresAt));
            
            // Evict if over size limit
            if (cache.size() > MAX_CACHE_SIZE) {
                evictOldest();
            }
            
            log.debug("Cached response for key: {}", cacheKey);
            return null;
        });
    }

    @Override
    public Promise<Void> put(String cacheKey, HttpResponse response) {
        return put(cacheKey, response, DEFAULT_TTL);
    }

    @Override
    public Promise<HttpResponse> get(String cacheKey) {
        return Promise.ofBlocking(executor, () -> {
            CacheEntry entry = cache.get(cacheKey);
            
            if (entry == null) {
                misses.incrementAndGet();
                log.debug("Cache miss for key: {}", cacheKey);
                return null;
            }
            
            if (entry.isExpired()) {
                cache.remove(cacheKey);
                misses.incrementAndGet();
                log.debug("Cache expired for key: {}", cacheKey);
                return null;
            }
            
            hits.incrementAndGet();
            log.debug("Cache hit for key: {}", cacheKey);
            return entry.response;
        });
    }

    @Override
    public Promise<Void> invalidate(String cacheKey) {
        return Promise.ofBlocking(executor, () -> {
            cache.remove(cacheKey);
            invalidations.incrementAndGet();
            log.debug("Invalidated cache for key: {}", cacheKey);
            return null;
        });
    }

    @Override
    public Promise<Void> invalidateTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String pattern = ":" + tenantId + ":";
            cache.keySet().removeIf(key -> key.contains(pattern));
            invalidations.incrementAndGet();
            log.debug("Invalidated all cache entries for tenant: {}", tenantId);
            return null;
        });
    }

    @Override
    public Promise<Void> invalidatePattern(String pathPattern) {
        return Promise.ofBlocking(executor, () -> {
            String normalizedPattern = pathPattern.replace("*", "");
            cache.keySet().removeIf(key -> key.contains(normalizedPattern));
            invalidations.incrementAndGet();
            log.debug("Invalidated cache entries matching pattern: {}", pathPattern);
            return null;
        });
    }

    @Override
    public Promise<Boolean> isCached(String cacheKey) {
        return Promise.ofBlocking(executor, () -> {
            CacheEntry entry = cache.get(cacheKey);
            if (entry == null) {
                return false;
            }
            if (entry.isExpired()) {
                cache.remove(cacheKey);
                return false;
            }
            return true;
        });
    }

    @Override
    public Promise<CacheStats> getStats() {
        return Promise.ofBlocking(executor, () -> {
            long totalHits = hits.get();
            long totalMisses = misses.get();
            long total = totalHits + totalMisses;
            double hitRate = total > 0 ? (double) totalHits / total : 0.0;
            
            return new CacheStats(
                totalHits,
                totalMisses,
                cache.size(),
                hitRate,
                evictions.get(),
                invalidations.get()
            );
        });
    }

    @Override
    public Promise<Void> clearAll() {
        return Promise.ofBlocking(executor, () -> {
            long size = cache.size();
            cache.clear();
            hits.set(0);
            misses.set(0);
            evictions.set(0);
            invalidations.set(0);
            log.info("Cleared all cache entries (count: {})", size);
            return null;
        });
    }

    /**
     * Evict the oldest cache entry.
     */
    private void evictOldest() {
        cache.entrySet().stream()
            .min((e1, e2) -> e1.getValue().expiresAt.compareTo(e2.getValue().expiresAt))
            .ifPresent(entry -> {
                cache.remove(entry.getKey());
                evictions.incrementAndGet();
                log.debug("Evicted oldest cache entry: {}", entry.getKey());
            });
    }

    /**
     * Clean up expired entries (should be called periodically).
     */
    public Promise<Void> cleanupExpired() {
        return Promise.ofBlocking(executor, () -> {
            boolean removed = cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            if (removed) {
                log.debug("Cleaned up expired cache entries");
            }
            return null;
        });
    }
}
