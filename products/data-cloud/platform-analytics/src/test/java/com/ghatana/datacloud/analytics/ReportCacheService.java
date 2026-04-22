package com.ghatana.datacloud.analytics;

import io.activej.promise.Promise;

public interface ReportCacheService {
    Promise<Void> cache(String reportId, Report report); // GH-90000

    Promise<Report> get(String reportId); // GH-90000

    Promise<Void> invalidate(String reportId); // GH-90000

    Promise<Void> invalidateTenant(String tenantId); // GH-90000

    Promise<Void> invalidateOnSchemaChange(String collection); // GH-90000

    Promise<Boolean> isCached(String reportId); // GH-90000

    Promise<CacheStats> getStats(); // GH-90000

    record CacheStats(long hits, long misses, long size, double hitRate) {} // GH-90000
}
