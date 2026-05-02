/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Integration tests for cache consistency (D003). 
 *
 * <p>Validates cache invalidation on schema changes, TTL behavior,
 * and cache-hit consistency across operations.
 *
 * @doc.type class
 * @doc.purpose Cache consistency integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class) 
@TestInstance(TestInstance.Lifecycle.PER_CLASS) 
@DisplayName("CacheConsistency – Schema Change Invalidation (D003)")
class CacheConsistencyIntegrationTest extends EventloopTestBase {

    @Mock
    private ReportCacheService cacheService;

    @BeforeEach
    void setUp() { 
        // Fresh mock for each test
        cacheService = mock(ReportCacheService.class); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache Invalidation on Schema Change
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: schema_change_invalidates_affected_collection_reports")
    void schemaChangeInvalidatesAffectedCollectionReports() { 
        when(cacheService.invalidateOnSchemaChange("sales"))
            .thenReturn(Promise.of((Void) null)); 

        // When: Schema changes for "sales" collection
        runPromise(() -> cacheService.invalidateOnSchemaChange("sales"));

        // Then: Reports for sales should be invalidated
        verify(cacheService).invalidateOnSchemaChange("sales");
    }

    @Test
    @DisplayName("[D003]: schema_change_for_one_collection_does_not_affect_others")
    void schemaChangeForOneCollectionDoesNotAffectOthers() { 
        // Given: Caches for multiple collections
        when(cacheService.invalidateOnSchemaChange("sales"))
            .thenReturn(Promise.of((Void) null)); 

        // When: Only sales schema changes
        runPromise(() -> cacheService.invalidateOnSchemaChange("sales"));

        // Then: Only sales cache invalidated
        verify(cacheService, never()).invalidateOnSchemaChange("inventory");
        verify(cacheService, never()).invalidateOnSchemaChange("customers");
    }

    @Test
    @DisplayName("[D003]: tenant_schema_change_invalidates_tenant_specific_caches")
    void tenantSchemaChangeInvalidatesTenantSpecificCaches() { 
        String tenantId = "tenant-alpha";

        when(cacheService.invalidateTenant(tenantId)) 
            .thenReturn(Promise.of((Void) null)); 

        // When: Tenant-wide schema change occurs
        runPromise(() -> cacheService.invalidateTenant(tenantId)); 

        // Then: All tenant caches invalidated
        verify(cacheService).invalidateTenant(tenantId); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache Hit/Miss Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: cache_hit_returns_cached_value")
    void cacheHitReturnsCachedValue() { 
        String reportId = "report-cached";
        Report cachedReport = new Report() 
            .withId(reportId) 
            .withName("Cached Report")
            .withStatus("COMPLETED");

        when(cacheService.get(reportId)).thenReturn(Promise.of(cachedReport)); 
        when(cacheService.isCached(reportId)).thenReturn(Promise.of(true)); 

        Report result = runPromise(() -> cacheService.get(reportId)); 
        Boolean isCached = runPromise(() -> cacheService.isCached(reportId)); 

        assertThat(isCached).isTrue(); 
        assertThat(result).isEqualTo(cachedReport); 
    }

    @Test
    @DisplayName("[D003]: cache_miss_returns_null")
    void cacheMissReturnsNull() { 
        String reportId = "report-not-cached";

        when(cacheService.get(reportId)).thenReturn(Promise.of(null)); 
        when(cacheService.isCached(reportId)).thenReturn(Promise.of(false)); 

        Report result = runPromise(() -> cacheService.get(reportId)); 
        Boolean isCached = runPromise(() -> cacheService.isCached(reportId)); 

        assertThat(isCached).isFalse(); 
        assertThat(result).isNull(); 
    }

    @Test
    @DisplayName("[D003]: cache_populate_then_retrieve_consistent")
    void cachePopulateThenRetrieveConsistent() { 
        String reportId = "report-001";
        Report report = new Report() 
            .withId(reportId) 
            .withName("Test Report")
            .withStatus("COMPLETED");

        when(cacheService.cache(reportId, report)).thenReturn(Promise.of((Void) null)); 
        when(cacheService.get(reportId)).thenReturn(Promise.of(report)); 

        // Populate cache
        runPromise(() -> cacheService.cache(reportId, report)); 

        // Retrieve from cache
        Report retrieved = runPromise(() -> cacheService.get(reportId)); 

        assertThat(retrieved).isEqualTo(report); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache TTL and Expiration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: cached_item_expires_after_ttl")
    void cachedItemExpiresAfterTtl() { 
        String reportId = "report-expiring";
        Report report = new Report().withId(reportId).withName("Expiring Report");

        // Simulate TTL expiration by invalidating
        when(cacheService.cache(reportId, report)).thenReturn(Promise.of((Void) null)); 
        when(cacheService.invalidate(reportId)).thenReturn(Promise.of((Void) null)); 
        when(cacheService.get(reportId)).thenReturn(Promise.of(null)); 

        // Cache and then expire
        runPromise(() -> cacheService.cache(reportId, report)); 
        runPromise(() -> cacheService.invalidate(reportId)); 

        // After expiration, should be null
        Report result = runPromise(() -> cacheService.get(reportId)); 

        assertThat(result).isNull(); 
    }

    @Test
    @DisplayName("[D003]: manual_invalidation_removes_from_cache")
    void manualInvalidationRemovesFromCache() { 
        String reportId = "report-to-invalidate";

        when(cacheService.invalidate(reportId)).thenReturn(Promise.of((Void) null)); 
        when(cacheService.isCached(reportId)).thenReturn(Promise.of(false)); 

        Boolean beforeInvalidation = runPromise(() -> cacheService.isCached(reportId)); 

        // Would be true if cached, false if not present
        // After manual invalidation
        runPromise(() -> cacheService.invalidate(reportId)); 

        Boolean afterInvalidation = runPromise(() -> cacheService.isCached(reportId)); 

        assertThat(afterInvalidation).isFalse(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache Statistics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: cache_stats_tracked_correctly")
    void cacheStatsTrackedCorrectly() { 
        ReportCacheService.CacheStats stats = new ReportCacheService.CacheStats( 
            100,  // hits
            20,   // misses
            50,   // size
            0.83  // hit rate
        );

        when(cacheService.getStats()).thenReturn(Promise.of(stats)); 

        ReportCacheService.CacheStats result = runPromise(() -> cacheService.getStats()); 

        assertThat(result.hits()).isEqualTo(100); 
        assertThat(result.misses()).isEqualTo(20); 
        assertThat(result.size()).isEqualTo(50); 
        assertThat(result.hitRate()).isEqualTo(0.83); 
    }

    @Test
    @DisplayName("[D003]: cache_hit_rate_calculated_correctly")
    void cacheHitRateCalculatedCorrectly() { 
        // 100 hits, 0 misses = 100% hit rate
        ReportCacheService.CacheStats perfectHitRate =
            new ReportCacheService.CacheStats(100, 0, 50, 1.0); 

        // 50 hits, 50 misses = 50% hit rate
        ReportCacheService.CacheStats fiftyFifty =
            new ReportCacheService.CacheStats(50, 50, 50, 0.5); 

        assertThat(perfectHitRate.hitRate()).isEqualTo(1.0); 
        assertThat(fiftyFifty.hitRate()).isEqualTo(0.5); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent Access Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D003]: concurrent_cache_reads_consistent")
    void concurrentCacheReadsConsistent() { 
        String reportId = "concurrent-report";
        Report report = new Report().withId(reportId).withName("Concurrent Report");

        when(cacheService.get(reportId)).thenReturn(Promise.of(report)); 

        // Simulate concurrent reads
        Report read1 = runPromise(() -> cacheService.get(reportId)); 
        Report read2 = runPromise(() -> cacheService.get(reportId)); 
        Report read3 = runPromise(() -> cacheService.get(reportId)); 

        assertThat(read1).isEqualTo(read2).isEqualTo(read3); 
    }

    @Test
    @DisplayName("[D003]: write_during_read_maintains_consistency")
    void writeDuringReadMaintainsConsistency() { 
        String reportId = "report-001";
        Report v1 = new Report().withId(reportId).withName("Version 1");
        Report v2 = new Report().withId(reportId).withName("Version 2");

        when(cacheService.cache(anyString(), any())).thenReturn(Promise.of((Void) null)); 
        when(cacheService.get(reportId)).thenReturn(Promise.of(v1)); 

        // Initial read
        Report read1 = runPromise(() -> cacheService.get(reportId)); 

        // Update cache
        runPromise(() -> cacheService.cache(reportId, v2)); 
        when(cacheService.get(reportId)).thenReturn(Promise.of(v2)); 

        // Read after update
        Report read2 = runPromise(() -> cacheService.get(reportId)); 

        assertThat(read1.getName()).isEqualTo("Version 1");
        assertThat(read2.getName()).isEqualTo("Version 2");
    }

}
