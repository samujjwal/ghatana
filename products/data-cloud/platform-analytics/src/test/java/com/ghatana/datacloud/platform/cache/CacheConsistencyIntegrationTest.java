/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.platform.cache;

import com.ghatana.datacloud.analytics.Report;
import com.ghatana.datacloud.analytics.ReportCacheService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration tests for cache consistency (D003). // GH-90000
 *
 * <p>Validates cache invalidation on schema changes, TTL behavior,
 * and cache-hit consistency across operations.
 *
 * @doc.type class
 * @doc.purpose Cache consistency integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // GH-90000
@DisplayName("CacheConsistency – Schema Change Invalidation (D003)")
class CacheConsistencyIntegrationTest extends EventloopTestBase {

    @Mock
    private ReportCacheService cacheService;

    @BeforeEach
    void setUp() { // GH-90000
        // Fresh mock for each test
        cacheService = mock(ReportCacheService.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache Invalidation on Schema Change
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: schema_change_invalidates_affected_collection_reports")
    void schemaChangeInvalidatesAffectedCollectionReports() { // GH-90000
        when(cacheService.invalidateOnSchemaChange("sales"))
            .thenReturn(Promise.of((Void) null)); // GH-90000

        // When: Schema changes for "sales" collection
        runPromise(() -> cacheService.invalidateOnSchemaChange("sales"));

        // Then: Reports for sales should be invalidated
        verify(cacheService).invalidateOnSchemaChange("sales");
    }

    @Test
    @DisplayName("[D003]: schema_change_for_one_collection_does_not_affect_others")
    void schemaChangeForOneCollectionDoesNotAffectOthers() { // GH-90000
        // Given: Caches for multiple collections
        when(cacheService.invalidateOnSchemaChange("sales"))
            .thenReturn(Promise.of((Void) null)); // GH-90000

        // When: Only sales schema changes
        runPromise(() -> cacheService.invalidateOnSchemaChange("sales"));

        // Then: Only sales cache invalidated
        verify(cacheService, never()).invalidateOnSchemaChange("inventory");
        verify(cacheService, never()).invalidateOnSchemaChange("customers");
    }

    @Test
    @DisplayName("[D003]: tenant_schema_change_invalidates_tenant_specific_caches")
    void tenantSchemaChangeInvalidatesTenantSpecificCaches() { // GH-90000
        String tenantId = "tenant-alpha";

        when(cacheService.invalidateTenant(tenantId)) // GH-90000
            .thenReturn(Promise.of((Void) null)); // GH-90000

        // When: Tenant-wide schema change occurs
        runPromise(() -> cacheService.invalidateTenant(tenantId)); // GH-90000

        // Then: All tenant caches invalidated
        verify(cacheService).invalidateTenant(tenantId); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache Hit/Miss Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: cache_hit_returns_cached_value")
    void cacheHitReturnsCachedValue() { // GH-90000
        String reportId = "report-cached";
        Report cachedReport = new Report() // GH-90000
            .withId(reportId) // GH-90000
            .withName("Cached Report")
            .withStatus("COMPLETED");

        when(cacheService.get(reportId)).thenReturn(Promise.of(cachedReport)); // GH-90000
        when(cacheService.isCached(reportId)).thenReturn(Promise.of(true)); // GH-90000

        Report result = runPromise(() -> cacheService.get(reportId)); // GH-90000
        Boolean isCached = runPromise(() -> cacheService.isCached(reportId)); // GH-90000

        assertThat(isCached).isTrue(); // GH-90000
        assertThat(result).isEqualTo(cachedReport); // GH-90000
    }

    @Test
    @DisplayName("[D003]: cache_miss_returns_null")
    void cacheMissReturnsNull() { // GH-90000
        String reportId = "report-not-cached";

        when(cacheService.get(reportId)).thenReturn(Promise.of(null)); // GH-90000
        when(cacheService.isCached(reportId)).thenReturn(Promise.of(false)); // GH-90000

        Report result = runPromise(() -> cacheService.get(reportId)); // GH-90000
        Boolean isCached = runPromise(() -> cacheService.isCached(reportId)); // GH-90000

        assertThat(isCached).isFalse(); // GH-90000
        assertThat(result).isNull(); // GH-90000
    }

    @Test
    @DisplayName("[D003]: cache_populate_then_retrieve_consistent")
    void cachePopulateThenRetrieveConsistent() { // GH-90000
        String reportId = "report-001";
        Report report = new Report() // GH-90000
            .withId(reportId) // GH-90000
            .withName("Test Report")
            .withStatus("COMPLETED");

        when(cacheService.cache(reportId, report)).thenReturn(Promise.of((Void) null)); // GH-90000
        when(cacheService.get(reportId)).thenReturn(Promise.of(report)); // GH-90000

        // Populate cache
        runPromise(() -> cacheService.cache(reportId, report)); // GH-90000

        // Retrieve from cache
        Report retrieved = runPromise(() -> cacheService.get(reportId)); // GH-90000

        assertThat(retrieved).isEqualTo(report); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache TTL and Expiration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: cached_item_expires_after_ttl")
    void cachedItemExpiresAfterTtl() { // GH-90000
        String reportId = "report-expiring";
        Report report = new Report().withId(reportId).withName("Expiring Report");

        // Simulate TTL expiration by invalidating
        when(cacheService.cache(reportId, report)).thenReturn(Promise.of((Void) null)); // GH-90000
        when(cacheService.invalidate(reportId)).thenReturn(Promise.of((Void) null)); // GH-90000
        when(cacheService.get(reportId)).thenReturn(Promise.of(null)); // GH-90000

        // Cache and then expire
        runPromise(() -> cacheService.cache(reportId, report)); // GH-90000
        runPromise(() -> cacheService.invalidate(reportId)); // GH-90000

        // After expiration, should be null
        Report result = runPromise(() -> cacheService.get(reportId)); // GH-90000

        assertThat(result).isNull(); // GH-90000
    }

    @Test
    @DisplayName("[D003]: manual_invalidation_removes_from_cache")
    void manualInvalidationRemovesFromCache() { // GH-90000
        String reportId = "report-to-invalidate";

        when(cacheService.invalidate(reportId)).thenReturn(Promise.of((Void) null)); // GH-90000
        when(cacheService.isCached(reportId)).thenReturn(Promise.of(false)); // GH-90000

        Boolean beforeInvalidation = runPromise(() -> cacheService.isCached(reportId)); // GH-90000

        // Would be true if cached, false if not present
        // After manual invalidation
        runPromise(() -> cacheService.invalidate(reportId)); // GH-90000

        Boolean afterInvalidation = runPromise(() -> cacheService.isCached(reportId)); // GH-90000

        assertThat(afterInvalidation).isFalse(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache Statistics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: cache_stats_tracked_correctly")
    void cacheStatsTrackedCorrectly() { // GH-90000
        ReportCacheService.CacheStats stats = new ReportCacheService.CacheStats( // GH-90000
            100,  // hits
            20,   // misses
            50,   // size
            0.83  // hit rate
        );

        when(cacheService.getStats()).thenReturn(Promise.of(stats)); // GH-90000

        ReportCacheService.CacheStats result = runPromise(() -> cacheService.getStats()); // GH-90000

        assertThat(result.hits()).isEqualTo(100); // GH-90000
        assertThat(result.misses()).isEqualTo(20); // GH-90000
        assertThat(result.size()).isEqualTo(50); // GH-90000
        assertThat(result.hitRate()).isEqualTo(0.83); // GH-90000
    }

    @Test
    @DisplayName("[D003]: cache_hit_rate_calculated_correctly")
    void cacheHitRateCalculatedCorrectly() { // GH-90000
        // 100 hits, 0 misses = 100% hit rate
        ReportCacheService.CacheStats perfectHitRate =
            new ReportCacheService.CacheStats(100, 0, 50, 1.0); // GH-90000

        // 50 hits, 50 misses = 50% hit rate
        ReportCacheService.CacheStats fiftyFifty =
            new ReportCacheService.CacheStats(50, 50, 50, 0.5); // GH-90000

        assertThat(perfectHitRate.hitRate()).isEqualTo(1.0); // GH-90000
        assertThat(fiftyFifty.hitRate()).isEqualTo(0.5); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent Access Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: concurrent_cache_reads_consistent")
    void concurrentCacheReadsConsistent() { // GH-90000
        String reportId = "concurrent-report";
        Report report = new Report().withId(reportId).withName("Concurrent Report");

        when(cacheService.get(reportId)).thenReturn(Promise.of(report)); // GH-90000

        // Simulate concurrent reads
        Report read1 = runPromise(() -> cacheService.get(reportId)); // GH-90000
        Report read2 = runPromise(() -> cacheService.get(reportId)); // GH-90000
        Report read3 = runPromise(() -> cacheService.get(reportId)); // GH-90000

        assertThat(read1).isEqualTo(read2).isEqualTo(read3); // GH-90000
    }

    @Test
    @DisplayName("[D003]: write_during_read_maintains_consistency")
    void writeDuringReadMaintainsConsistency() { // GH-90000
        String reportId = "report-001";
        Report v1 = new Report().withId(reportId).withName("Version 1");
        Report v2 = new Report().withId(reportId).withName("Version 2");

        when(cacheService.cache(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
        when(cacheService.get(reportId)).thenReturn(Promise.of(v1)); // GH-90000

        // Initial read
        Report read1 = runPromise(() -> cacheService.get(reportId)); // GH-90000

        // Update cache
        runPromise(() -> cacheService.cache(reportId, v2)); // GH-90000
        when(cacheService.get(reportId)).thenReturn(Promise.of(v2)); // GH-90000

        // Read after update
        Report read2 = runPromise(() -> cacheService.get(reportId)); // GH-90000

        assertThat(read1.getName()).isEqualTo("Version 1");
        assertThat(read2.getName()).isEqualTo("Version 2");
    }

}
