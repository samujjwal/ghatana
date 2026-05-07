/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.cache;

import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Duration;

/**
 * Service interface for API response caching operations.
 *
 * <p>Provides cache management for HTTP API responses with:
 * <ul>
 *   <li>Cache storage with configurable TTL</li>
 *   <li>Cache retrieval by cache key</li>
 *   <li>Cache invalidation by key, tenant, or pattern</li>
 *   <li>Cache statistics tracking</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 *
 * <p>Cache keys are typically composed of:
 * <ul>
 *   <li>HTTP method and path</li>
 *   <li>Tenant ID for multi-tenant isolation</li>
 *   <li>Relevant query parameters</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Cache service for API response performance optimization
 * @doc.layer product
 * @doc.pattern Cache, Service Interface
 */
public interface ApiResponseCacheService {

    /**
     * Store an HTTP response in the cache.
     *
     * @param cacheKey unique cache key (typically method:path:tenant:params)
     * @param response the HTTP response to cache
     * @param ttl time-to-live for this cache entry
     * @return promise completing when cached
     */
    Promise<Void> put(String cacheKey, HttpResponse response, Duration ttl);

    /**
     * Store an HTTP response in the cache with default TTL.
     *
     * @param cacheKey unique cache key
     * @param response the HTTP response to cache
     * @return promise completing when cached
     */
    Promise<Void> put(String cacheKey, HttpResponse response);

    /**
     * Retrieve an HTTP response from cache.
     *
     * @param cacheKey unique cache key
     * @return promise of cached response or null if not found or expired
     */
    Promise<HttpResponse> get(String cacheKey);

    /**
     * Invalidate a cached response by key.
     *
     * @param cacheKey unique cache key
     * @return promise completing when invalidated
     */
    Promise<Void> invalidate(String cacheKey);

    /**
     * Invalidate all cached responses for a tenant.
     *
     * @param tenantId tenant identifier
     * @return promise completing when invalidated
     */
    Promise<Void> invalidateTenant(String tenantId);

    /**
     * Invalidate cached responses matching a path pattern.
     *
     * @param pathPattern path pattern (e.g., "/api/v1/collections/*")
     * @return promise completing when invalidated
     */
    Promise<Void> invalidatePattern(String pathPattern);

    /**
     * Check if response is cached and not expired.
     *
     * @param cacheKey unique cache key
     * @return promise of true if cached and valid
     */
    Promise<Boolean> isCached(String cacheKey);

    /**
     * Get cache statistics.
     *
     * @return promise of cache stats
     */
    Promise<CacheStats> getStats();

    /**
     * Clear all cached responses.
     *
     * @return promise completing when cleared
     */
    Promise<Void> clearAll();

    /**
     * Cache statistics.
     */
    record CacheStats(
        long hits,
        long misses,
        long size,
        double hitRate,
        long evictions,
        long invalidations
    ) {}
}
