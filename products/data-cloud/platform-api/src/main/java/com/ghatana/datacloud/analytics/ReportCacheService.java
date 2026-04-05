/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import io.activej.promise.Promise;

/**
 * Service interface for report caching operations.
 *
 * <p>Provides cache management for generated reports with:
 * <ul>
 *   <li>Cache storage with TTL</li>
 *   <li>Cache retrieval by ID</li>
 *   <li>Cache invalidation</li>
 *   <li>Cache eviction on schema change</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Cache service for report consistency and performance
 * @doc.layer product
 * @doc.pattern Cache, Service Interface
 */
public interface ReportCacheService {

    /**
     * Store a report in the cache.
     *
     * @param reportId unique report identifier
     * @param report the report to cache
     * @return promise completing when cached
     */
    Promise<Void> cache(String reportId, Report report);

    /**
     * Retrieve a report from cache.
     *
     * @param reportId unique report identifier
     * @return promise of cached report or null if not found
     */
    Promise<Report> get(String reportId);

    /**
     * Invalidate a cached report.
     *
     * @param reportId unique report identifier
     * @return promise completing when invalidated
     */
    Promise<Void> invalidate(String reportId);

    /**
     * Invalidate all cached reports for a tenant.
     *
     * @param tenantId tenant identifier
     * @return promise completing when invalidated
     */
    Promise<Void> invalidateTenant(String tenantId);

    /**
     * Invalidate reports when schema changes.
     *
     * @param collection collection name whose schema changed
     * @return promise completing when invalidated
     */
    Promise<Void> invalidateOnSchemaChange(String collection);

    /**
     * Check if report is cached.
     *
     * @param reportId unique report identifier
     * @return promise of true if cached
     */
    Promise<Boolean> isCached(String reportId);

    /**
     * Get cache statistics.
     *
     * @return promise of cache stats (hits, misses, size)
     */
    Promise<CacheStats> getStats();

    /**
     * Cache statistics.
     */
    record CacheStats(long hits, long misses, long size, double hitRate) {}
}
