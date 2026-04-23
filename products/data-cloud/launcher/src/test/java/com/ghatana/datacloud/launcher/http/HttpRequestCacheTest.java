/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpResponse;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for HTTP request caching service.
 *
 * @doc.type    class
 * @doc.purpose Tests for HTTP request caching including TTL, statistics, and eviction
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("HttpRequestCacheTest")
@Tag("launcher")
class HttpRequestCacheTest {

    private HttpRequestCache cache;

    @BeforeEach
    void setUp() { // GH-90000
        cache = new HttpRequestCache(Duration.ofSeconds(60), 100); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        cache.clear(); // GH-90000
    }

    // ─── Cache operations ─────────────────────────────────────────────────────

    @Test
    @DisplayName("caches and retrieves response")
    void cachesAndRetrievesResponse() { // GH-90000
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000

        cache.put(key, response); // GH-90000
        HttpResponse cached = cache.get(key); // GH-90000

        assertThat(cached).isNotNull(); // GH-90000
        assertThat(cached.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("expired entry is not returned")
    void expiredEntryIsNotReturned() { // GH-90000
        HttpRequestCache shortCache = new HttpRequestCache(Duration.ofMillis(100), 100); // GH-90000
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000

        shortCache.put(key, response); // GH-90000
        
        // Wait for expiration
        try {
            Thread.sleep(150); // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }

        HttpResponse cached = shortCache.get(key); // GH-90000
        assertThat(cached).isNull(); // GH-90000
    }

    @Test
    @DisplayName("invalidates specific key")
    void invalidatesSpecificKey() { // GH-90000
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000

        cache.put(key, response); // GH-90000
        cache.invalidate(key); // GH-90000

        HttpResponse cached = cache.get(key); // GH-90000
        assertThat(cached).isNull(); // GH-90000
    }

    @Test
    @DisplayName("clear removes all entries")
    void clearRemovesAllEntries() { // GH-90000
        cache.put("/api/data1", HttpResponse.ok200().build()); // GH-90000
        cache.put("/api/data2", HttpResponse.ok200().build()); // GH-90000

        cache.clear(); // GH-90000

        assertThat(cache.getStats().currentSize()).isEqualTo(0); // GH-90000
    }

    // ─── Cache statistics ─────────────────────────────────────────────────────

    @Test
    @DisplayName("tracks hit count")
    void tracksHitCount() { // GH-90000
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000

        cache.put(key, response); // GH-90000
        cache.get(key); // GH-90000
        cache.get(key); // GH-90000

        assertThat(cache.getStats().hitCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("tracks miss count")
    void tracksMissCount() { // GH-90000
        cache.get("/nonexistent");
        cache.get("/another");

        assertThat(cache.getStats().missCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("calculates hit rate correctly")
    void calculatesHitRateCorrectly() { // GH-90000
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000

        cache.put(key, response); // GH-90000
        cache.get(key); // hit // GH-90000
        cache.get("/nonexistent"); // miss

        double hitRate = cache.getStats().hitRate(); // GH-90000
        assertThat(hitRate).isEqualTo(0.5); // GH-90000
    }

    @Test
    @DisplayName("tracks eviction count")
    void tracksEvictionCount() { // GH-90000
        HttpRequestCache shortCache = new HttpRequestCache(Duration.ofMillis(100), 2); // GH-90000
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000

        shortCache.put(key, response); // GH-90000
        
        // Wait for expiration
        try {
            Thread.sleep(150); // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }

        shortCache.get(key); // Triggers eviction // GH-90000
        assertThat(shortCache.getStats().evictionCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("reports current size correctly")
    void reportsCurrentSizeCorrectly() { // GH-90000
        cache.put("/api/data1", HttpResponse.ok200().build()); // GH-90000
        cache.put("/api/data2", HttpResponse.ok200().build()); // GH-90000

        assertThat(cache.getStats().currentSize()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("calculates utilization correctly")
    void calculatesUtilizationCorrectly() { // GH-90000
        HttpRequestCache smallCache = new HttpRequestCache(Duration.ofSeconds(60), 10); // GH-90000
        smallCache.put("/api/data1", HttpResponse.ok200().build()); // GH-90000
        smallCache.put("/api/data2", HttpResponse.ok200().build()); // GH-90000

        double utilization = smallCache.getStats().utilization(); // GH-90000
        assertThat(utilization).isEqualTo(0.2); // GH-90000
    }

    // ─── Cache eviction ───────────────────────────────────────────────────────

    @Test
    @DisplayName("evicts oldest entry when at capacity")
    void evictsOldestEntryWhenAtCapacity() { // GH-90000
        HttpRequestCache smallCache = new HttpRequestCache(Duration.ofSeconds(60), 2); // GH-90000
        
        smallCache.put("/api/data1", HttpResponse.ok200().build()); // GH-90000
        smallCache.put("/api/data2", HttpResponse.ok200().build()); // GH-90000
        smallCache.put("/api/data3", HttpResponse.ok200().build()); // Should evict oldest // GH-90000

        assertThat(smallCache.getStats().currentSize()).isEqualTo(2); // GH-90000
        assertThat(smallCache.getStats().evictionCount()).isGreaterThan(0); // GH-90000
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("handles null key gracefully in get")
    void handlesNullKeyInGet() { // GH-90000
        HttpResponse cached = cache.get(null); // GH-90000
        assertThat(cached).isNull(); // GH-90000
    }

    @Test
    @DisplayName("handles null key gracefully in put")
    void handlesNullKeyInPut() { // GH-90000
        cache.put(null, HttpResponse.ok200().build()); // GH-90000
        assertThat(cache.getStats().currentSize()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("handles null response gracefully in put")
    void handlesNullResponseInPut() { // GH-90000
        cache.put("/api/data", null); // GH-90000
        assertThat(cache.getStats().currentSize()).isEqualTo(0); // GH-90000
    }
}
