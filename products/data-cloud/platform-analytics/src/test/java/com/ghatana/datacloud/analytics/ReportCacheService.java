package com.ghatana.datacloud.analytics;

import io.activej.promise.Promise;

public interface ReportCacheService {
    Promise<Void> cache(String reportId, Report report);

    Promise<Report> get(String reportId);

    Promise<Void> invalidate(String reportId);

    Promise<Void> invalidateTenant(String tenantId);

    Promise<Void> invalidateOnSchemaChange(String collection);

    Promise<Boolean> isCached(String reportId);

    Promise<CacheStats> getStats();

    record CacheStats(long hits, long misses, long size, double hitRate) {}
}
