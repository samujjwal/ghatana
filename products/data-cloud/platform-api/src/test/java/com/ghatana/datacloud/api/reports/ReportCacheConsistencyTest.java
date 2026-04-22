/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.api.reports;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for report cache hit/miss, invalidation, and refresh semantics.
 *
 * @doc.type    class
 * @doc.purpose Tests for report cache consistency and invalidation behavior
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Report Cache Consistency Tests [GH-90000]")
class ReportCacheConsistencyTest extends EventloopTestBase {

    // ── Cache model ───────────────────────────────────────────────────────────

    record CacheKey(String reportType, String tenantId, String windowHash) {} // GH-90000

    record CacheEntry(String reportData, Instant cachedAt, Duration ttl) { // GH-90000
        boolean isStale() { return Instant.now().isAfter(cachedAt.plus(ttl)); } // GH-90000
    }

    private ReportCache cache;
    private AtomicInteger backendCallCount;

    @BeforeEach
    void setUp() { // GH-90000
        cache = new ReportCache(Duration.ofMinutes(5)); // GH-90000
        backendCallCount = new AtomicInteger(0); // GH-90000
    }

    // ── Cache hit ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cache returns stored entry on second request without calling backend [GH-90000]")
    void cacheReturnsCachedEntryOnHit() { // GH-90000
        CacheKey key = new CacheKey("EVENT_TREND", "tenant-cache", "window-2026-03"); // GH-90000
        String firstResult = fetchWithCache(key, "report-data-v1"); // GH-90000
        String secondResult = fetchWithCache(key, "report-data-v2"); // different "backend" data // GH-90000

        // Second call must hit the cache and return the first result
        assertThat(secondResult).isEqualTo("report-data-v1 [GH-90000]");
        assertThat(backendCallCount.get()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("cache tracks hit count correctly [GH-90000]")
    void cacheTracksHitCount() { // GH-90000
        CacheKey key = new CacheKey("MODEL_PERF", "tenant-x", "w1"); // GH-90000
        fetchWithCache(key, "data-a"); // GH-90000
        fetchWithCache(key, "ignored"); // GH-90000
        fetchWithCache(key, "ignored"); // GH-90000

        assertThat(cache.hitCount()).isEqualTo(2); // GH-90000
        assertThat(cache.missCount()).isEqualTo(1); // GH-90000
    }

    // ── Cache miss ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cache miss occurs for a key not yet stored [GH-90000]")
    void cacheMissOnFirstRequest() { // GH-90000
        CacheKey key = new CacheKey("COLLECTION_SUMMARY", "tenant-miss", "w-new"); // GH-90000
        fetchWithCache(key, "miss-data"); // GH-90000

        assertThat(cache.missCount()).isEqualTo(1); // GH-90000
        assertThat(cache.hitCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("different cache keys produce independent entries [GH-90000]")
    void differentKeysProduceIndependentEntries() { // GH-90000
        CacheKey key1 = new CacheKey("PIPELINE_HEALTH", "tenant-A", "w1"); // GH-90000
        CacheKey key2 = new CacheKey("PIPELINE_HEALTH", "tenant-B", "w1"); // GH-90000

        fetchWithCache(key1, "data-for-A"); // GH-90000
        fetchWithCache(key2, "data-for-B"); // GH-90000

        // Each key should have had one miss
        assertThat(backendCallCount.get()).isEqualTo(2); // GH-90000
    }

    // ── Cache invalidation ────────────────────────────────────────────────────

    @Test
    @DisplayName("invalidate removes the entry and forces a backend call on next request [GH-90000]")
    void invalidateRemovesEntryAndForcesNextBackendCall() { // GH-90000
        CacheKey key = new CacheKey("ANALYTICS", "tenant-inv", "w-inv"); // GH-90000
        fetchWithCache(key, "v1"); // GH-90000

        cache.invalidate(key); // GH-90000
        fetchWithCache(key, "v2"); // GH-90000

        // After invalidation, the second fetch must be a cache miss
        assertThat(backendCallCount.get()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("invalidating a non-existent key is a no-op [GH-90000]")
    void invalidatingNonExistentKeyIsNoOp() { // GH-90000
        CacheKey nonExistent = new CacheKey("MISSING", "tenant-ghost", "w-ghost"); // GH-90000
        cache.invalidate(nonExistent); // must not throw // GH-90000
        assertThat(cache.size()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("invalidateByTenant removes all cache entries for that tenant [GH-90000]")
    void invalidateByTenantRemovesAllEntriesForTenant() { // GH-90000
        CacheKey k1 = new CacheKey("T1", "tenant-evict", "w1"); // GH-90000
        CacheKey k2 = new CacheKey("T2", "tenant-evict", "w2"); // GH-90000
        CacheKey k3 = new CacheKey("T3", "tenant-keep", "w3"); // GH-90000

        fetchWithCache(k1, "d1"); // GH-90000
        fetchWithCache(k2, "d2"); // GH-90000
        fetchWithCache(k3, "d3"); // GH-90000

        cache.invalidateByTenant("tenant-evict [GH-90000]");

        assertThat(cache.contains(k1)).isFalse(); // GH-90000
        assertThat(cache.contains(k2)).isFalse(); // GH-90000
        assertThat(cache.contains(k3)).isTrue(); // GH-90000
    }

    // ── Cache refresh ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("stale entry is refreshed on next access [GH-90000]")
    void staleEntriesAreRefreshedOnAccess() { // GH-90000
        // Expire TTL of 0 means everything is immediately stale
        ReportCache shortTtlCache = new ReportCache(Duration.ZERO); // GH-90000
        AtomicInteger refreshCount = new AtomicInteger(0); // GH-90000

        CacheKey key = new CacheKey("REFRESH_TEST", "tenant-r", "w-r"); // GH-90000
        shortTtlCache.getOrLoad(key, () -> { // GH-90000
            refreshCount.incrementAndGet(); // GH-90000
            return "fresh-" + refreshCount.get(); // GH-90000
        });
        shortTtlCache.getOrLoad(key, () -> { // GH-90000
            refreshCount.incrementAndGet(); // GH-90000
            return "fresh-" + refreshCount.get(); // GH-90000
        });

        // Both calls should have gone to the backend since TTL=0 makes entries instantly stale
        assertThat(refreshCount.get()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("fresh entry within TTL is not refreshed [GH-90000]")
    void freshEntryWithinTtlIsNotRefreshed() { // GH-90000
        AtomicInteger callCount = new AtomicInteger(0); // GH-90000
        CacheKey key = new CacheKey("FRESH_TEST", "tenant-f", "w-f"); // GH-90000

        cache.getOrLoad(key, () -> { callCount.incrementAndGet(); return "data"; }); // GH-90000
        cache.getOrLoad(key, () -> { callCount.incrementAndGet(); return "data"; }); // GH-90000

        assertThat(callCount.get()).isEqualTo(1); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fetchWithCache(CacheKey key, String backendData) { // GH-90000
        return cache.getOrLoad(key, () -> { // GH-90000
            backendCallCount.incrementAndGet(); // GH-90000
            return backendData;
        });
    }

    // ── Report cache implementation (for tests) ─────────────────────────────── // GH-90000

    static class ReportCache {
        private final ConcurrentHashMap<CacheKey, CacheEntry> store = new ConcurrentHashMap<>(); // GH-90000
        private final Duration defaultTtl;
        private final AtomicInteger hits = new AtomicInteger(0); // GH-90000
        private final AtomicInteger misses = new AtomicInteger(0); // GH-90000

        ReportCache(Duration defaultTtl) { // GH-90000
            this.defaultTtl = defaultTtl;
        }

        String getOrLoad(CacheKey key, java.util.function.Supplier<String> loader) { // GH-90000
            CacheEntry existing = store.get(key); // GH-90000
            if (existing != null && !existing.isStale()) { // GH-90000
                hits.incrementAndGet(); // GH-90000
                return existing.reportData(); // GH-90000
            }
            misses.incrementAndGet(); // GH-90000
            String data = loader.get(); // GH-90000
            store.put(key, new CacheEntry(data, Instant.now(), defaultTtl)); // GH-90000
            return data;
        }

        void invalidate(CacheKey key) { store.remove(key); } // GH-90000

        void invalidateByTenant(String tenantId) { // GH-90000
            store.keySet().removeIf(k -> k.tenantId().equals(tenantId)); // GH-90000
        }

        boolean contains(CacheKey key) { // GH-90000
            CacheEntry e = store.get(key); // GH-90000
            return e != null && !e.isStale(); // GH-90000
        }

        int size() { return store.size(); } // GH-90000
        int hitCount() { return hits.get(); } // GH-90000
        int missCount() { return misses.get(); } // GH-90000
    }
}
