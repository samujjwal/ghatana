/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Report Cache Consistency Tests")
class ReportCacheConsistencyTest extends EventloopTestBase {

    // ── Cache model ───────────────────────────────────────────────────────────

    record CacheKey(String reportType, String tenantId, String windowHash) {} 

    record CacheEntry(String reportData, Instant cachedAt, Duration ttl) { 
        boolean isStale() { return Instant.now().isAfter(cachedAt.plus(ttl)); } 
    }

    private ReportCache cache;
    private AtomicInteger backendCallCount;

    @BeforeEach
    void setUp() { 
        cache = new ReportCache(Duration.ofMinutes(5)); 
        backendCallCount = new AtomicInteger(0); 
    }

    // ── Cache hit ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cache returns stored entry on second request without calling backend")
    void cacheReturnsCachedEntryOnHit() { 
        CacheKey key = new CacheKey("EVENT_TREND", "tenant-cache", "window-2026-03"); 
        String firstResult = fetchWithCache(key, "report-data-v1"); 
        String secondResult = fetchWithCache(key, "report-data-v2"); // different "backend" data 

        // Second call must hit the cache and return the first result
        assertThat(secondResult).isEqualTo("report-data-v1");
        assertThat(backendCallCount.get()).isEqualTo(1); 
    }

    @Test
    @DisplayName("cache tracks hit count correctly")
    void cacheTracksHitCount() { 
        CacheKey key = new CacheKey("MODEL_PERF", "tenant-x", "w1"); 
        fetchWithCache(key, "data-a"); 
        fetchWithCache(key, "ignored"); 
        fetchWithCache(key, "ignored"); 

        assertThat(cache.hitCount()).isEqualTo(2); 
        assertThat(cache.missCount()).isEqualTo(1); 
    }

    // ── Cache miss ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cache miss occurs for a key not yet stored")
    void cacheMissOnFirstRequest() { 
        CacheKey key = new CacheKey("COLLECTION_SUMMARY", "tenant-miss", "w-new"); 
        fetchWithCache(key, "miss-data"); 

        assertThat(cache.missCount()).isEqualTo(1); 
        assertThat(cache.hitCount()).isEqualTo(0); 
    }

    @Test
    @DisplayName("different cache keys produce independent entries")
    void differentKeysProduceIndependentEntries() { 
        CacheKey key1 = new CacheKey("PIPELINE_HEALTH", "tenant-A", "w1"); 
        CacheKey key2 = new CacheKey("PIPELINE_HEALTH", "tenant-B", "w1"); 

        fetchWithCache(key1, "data-for-A"); 
        fetchWithCache(key2, "data-for-B"); 

        // Each key should have had one miss
        assertThat(backendCallCount.get()).isEqualTo(2); 
    }

    // ── Cache invalidation ────────────────────────────────────────────────────

    @Test
    @DisplayName("invalidate removes the entry and forces a backend call on next request")
    void invalidateRemovesEntryAndForcesNextBackendCall() { 
        CacheKey key = new CacheKey("ANALYTICS", "tenant-inv", "w-inv"); 
        fetchWithCache(key, "v1"); 

        cache.invalidate(key); 
        fetchWithCache(key, "v2"); 

        // After invalidation, the second fetch must be a cache miss
        assertThat(backendCallCount.get()).isEqualTo(2); 
    }

    @Test
    @DisplayName("invalidating a non-existent key is a no-op")
    void invalidatingNonExistentKeyIsNoOp() { 
        CacheKey nonExistent = new CacheKey("MISSING", "tenant-ghost", "w-ghost"); 
        cache.invalidate(nonExistent); // must not throw 
        assertThat(cache.size()).isEqualTo(0); 
    }

    @Test
    @DisplayName("invalidateByTenant removes all cache entries for that tenant")
    void invalidateByTenantRemovesAllEntriesForTenant() { 
        CacheKey k1 = new CacheKey("T1", "tenant-evict", "w1"); 
        CacheKey k2 = new CacheKey("T2", "tenant-evict", "w2"); 
        CacheKey k3 = new CacheKey("T3", "tenant-keep", "w3"); 

        fetchWithCache(k1, "d1"); 
        fetchWithCache(k2, "d2"); 
        fetchWithCache(k3, "d3"); 

        cache.invalidateByTenant("tenant-evict");

        assertThat(cache.contains(k1)).isFalse(); 
        assertThat(cache.contains(k2)).isFalse(); 
        assertThat(cache.contains(k3)).isTrue(); 
    }

    // ── Cache refresh ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("stale entry is refreshed on next access")
    void staleEntriesAreRefreshedOnAccess() { 
        // Use a very small TTL to ensure entries become stale quickly
        ReportCache shortTtlCache = new ReportCache(Duration.ofMillis(1)); 
        AtomicInteger refreshCount = new AtomicInteger(0); 

        CacheKey key = new CacheKey("REFRESH_TEST", "tenant-r", "w-r"); 
        shortTtlCache.getOrLoad(key, () -> { 
            refreshCount.incrementAndGet(); 
            return "fresh-" + refreshCount.get(); 
        });
        // Small delay to ensure TTL expires
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        shortTtlCache.getOrLoad(key, () -> { 
            refreshCount.incrementAndGet(); 
            return "fresh-" + refreshCount.get(); 
        });

        // Both calls should have gone to the backend since TTL expired
        assertThat(refreshCount.get()).isEqualTo(2); 
    }

    @Test
    @DisplayName("fresh entry within TTL is not refreshed")
    void freshEntryWithinTtlIsNotRefreshed() { 
        AtomicInteger callCount = new AtomicInteger(0); 
        CacheKey key = new CacheKey("FRESH_TEST", "tenant-f", "w-f"); 

        cache.getOrLoad(key, () -> { callCount.incrementAndGet(); return "data"; }); 
        cache.getOrLoad(key, () -> { callCount.incrementAndGet(); return "data"; }); 

        assertThat(callCount.get()).isEqualTo(1); 
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fetchWithCache(CacheKey key, String backendData) { 
        return cache.getOrLoad(key, () -> { 
            backendCallCount.incrementAndGet(); 
            return backendData;
        });
    }

    // ── Report cache implementation (for tests) ─────────────────────────────── 

    static class ReportCache {
        private final ConcurrentHashMap<CacheKey, CacheEntry> store = new ConcurrentHashMap<>(); 
        private final Duration defaultTtl;
        private final AtomicInteger hits = new AtomicInteger(0); 
        private final AtomicInteger misses = new AtomicInteger(0); 

        ReportCache(Duration defaultTtl) { 
            this.defaultTtl = defaultTtl;
        }

        String getOrLoad(CacheKey key, java.util.function.Supplier<String> loader) { 
            CacheEntry existing = store.get(key); 
            if (existing != null && !existing.isStale()) { 
                hits.incrementAndGet(); 
                return existing.reportData(); 
            }
            misses.incrementAndGet(); 
            String data = loader.get(); 
            store.put(key, new CacheEntry(data, Instant.now(), defaultTtl)); 
            return data;
        }

        void invalidate(CacheKey key) { store.remove(key); } 

        void invalidateByTenant(String tenantId) { 
            store.keySet().removeIf(k -> k.tenantId().equals(tenantId)); 
        }

        boolean contains(CacheKey key) { 
            CacheEntry e = store.get(key); 
            return e != null && !e.isStale(); 
        }

        int size() { return store.size(); } 
        int hitCount() { return hits.get(); } 
        int missCount() { return misses.get(); } 
    }
}
