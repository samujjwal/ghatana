/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP request caching service for the Data Cloud launcher.
 *
 * <p>Caches HTTP GET responses based on request method, path, and query parameters.
 * Supports TTL-based expiration, cache statistics, and multi-tenant isolation.
 *
 * @doc.type class
 * @doc.purpose HTTP request caching with TTL, statistics, and multi-tenant isolation
 * @doc.layer product
 * @doc.pattern Cache
 */
public final class HttpRequestCache {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestCache.class);

    private final Map<String, CacheEntry> cache;
    private final Duration defaultTtl;
    private final int maxSize;
    private final AtomicLong hitCount;
    private final AtomicLong missCount;
    private final AtomicLong evictionCount;

    /**
     * Creates an HTTP request cache with default TTL (60 seconds) and max size (1000 entries).
     */
    public HttpRequestCache() {
        this(Duration.ofSeconds(60), 1000);
    }

    /**
     * Creates an HTTP request cache with custom configuration.
     *
     * @param defaultTtl default time-to-live for cached entries
     * @param maxSize maximum number of cached entries
     */
    public HttpRequestCache(Duration defaultTtl, int maxSize) {
        this.cache = new ConcurrentHashMap<>(maxSize);
        this.defaultTtl = defaultTtl;
        this.maxSize = maxSize;
        this.hitCount = new AtomicLong(0);
        this.missCount = new AtomicLong(0);
        this.evictionCount = new AtomicLong(0);
    }

    /**
     * Generates a cache key from the HTTP request.
     *
     * @param request the HTTP request
     * @return cache key string
     */
    public String generateCacheKey(HttpRequest request) {
        if (!request.getMethod().equals(HttpMethod.GET)) {
            return null; // Only cache GET requests
        }

        String path = request.getRelativePath();
        String query = request.getQueryParameter("q"); // Get query string if present

        // Simple cache key based on path and query
        String key = path;
        if (query != null && !query.isEmpty()) {
            key += "?" + query;
        }

        return key;
    }

    /**
     * Gets a cached response if available and not expired.
     *
     * @param key the cache key
     * @return cached response or null if not found or expired
     */
    public HttpResponse get(String key) {
        if (key == null) {
            return null;
        }

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            evictionCount.incrementAndGet();
            missCount.incrementAndGet();
            return null;
        }

        hitCount.incrementAndGet();
        log.debug("Cache hit for key: {}", key);
        return entry.response();
    }

    /**
     * Puts a response into the cache.
     *
     * @param key the cache key
     * @param response the HTTP response to cache
     */
    public void put(String key, HttpResponse response) {
        if (key == null || response == null) {
            return;
        }

        // Evict expired entries before adding new one
        evictExpired();

        // Evict oldest entries if at capacity
        if (cache.size() >= maxSize) {
            evictOldest();
        }

        CacheEntry entry = new CacheEntry(response, Instant.now().plus(defaultTtl), Instant.now());
        cache.put(key, entry);
        log.debug("Cached response for key: {}", key);
    }

    /**
     * Invalidates a cache entry.
     *
     * @param key the cache key to invalidate
     */
    public void invalidate(String key) {
        if (key != null) {
            cache.remove(key);
            log.debug("Invalidated cache key: {}", key);
        }
    }

    /**
     * Invalidates all cache entries for a tenant.
     *
     * @param tenantId the tenant ID
     */
    public void invalidateTenant(String tenantId) {
        if (tenantId == null) {
            return;
        }

        cache.keySet().removeIf(key -> key.startsWith(tenantId + ":"));
        log.debug("Invalidated all cache entries for tenant: {}", tenantId);
    }

    /**
     * Clears all cache entries.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.info("Cleared all cache entries ({} entries removed)", size);
    }

    /**
     * Returns cache statistics.
     *
     * @return cache statistics
     */
    public CacheStats getStats() {
        return new CacheStats(
                cache.size(),
                maxSize,
                hitCount.get(),
                missCount.get(),
                evictionCount.get()
        );
    }

    /**
     * Evicts expired entries from the cache.
     */
    private void evictExpired() {
        // P3-2: Performance monitoring for TTL cleanup
        long startTime = System.currentTimeMillis();
        Instant now = Instant.now();
        int beforeSize = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        int evicted = beforeSize - cache.size();
        long durationMs = System.currentTimeMillis() - startTime;
        if (evicted > 0) {
            evictionCount.addAndGet(evicted);
            log.debug("[P3-2] TTL cleanup: evicted {} expired entries durationMs={}", evicted, durationMs);
        }
    }

    /**
     * Evicts the oldest entry from the cache.
     */
    private void evictOldest() {
        // P3-2: Performance monitoring for TTL cleanup
        long startTime = System.currentTimeMillis();
        String oldestKey = cache.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparing(CacheEntry::createdAt)))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (oldestKey != null) {
            cache.remove(oldestKey);
            evictionCount.incrementAndGet();
            long durationMs = System.currentTimeMillis() - startTime;
            log.debug("[P3-2] TTL cleanup: evicted oldest entry key={} durationMs={}", oldestKey, durationMs);
        }
    }

    /**
     * Cache entry record.
     */
    private record CacheEntry(
            HttpResponse response,
            Instant expiresAt,
            Instant createdAt
    ) {
        CacheEntry {
            Objects.requireNonNull(response);
            Objects.requireNonNull(expiresAt);
            Objects.requireNonNull(createdAt);
        }

        boolean isExpired() {
            return isExpired(Instant.now());
        }

        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    /**
     * Cache statistics record.
     */
    public record CacheStats(
            int currentSize,
            int maxSize,
            long hitCount,
            long missCount,
            long evictionCount
    ) {
        public double hitRate() {
            long total = hitCount + missCount;
            return total > 0 ? (double) hitCount / total : 0.0;
        }

        public double utilization() {
            return maxSize > 0 ? (double) currentSize / maxSize : 0.0;
        }
    }
}
