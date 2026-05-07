/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        cache = new HttpRequestCache(Duration.ofSeconds(60), 100); 
    }

    @AfterEach
    void tearDown() { 
        cache.clear(); 
    }

    // ─── Cache operations ─────────────────────────────────────────────────────

    @Test
    @DisplayName("caches and retrieves response")
    void cachesAndRetrievesResponse() { 
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); 

        cache.put(key, response); 
        HttpResponse cached = cache.get(key); 

        assertThat(cached).isNotNull(); 
        assertThat(cached.getCode()).isEqualTo(200); 
    }

    @Test
    @DisplayName("expired entry is not returned")
    void expiredEntryIsNotReturned() { 
        HttpRequestCache shortCache = new HttpRequestCache(Duration.ofMillis(100), 100); 
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); 

        shortCache.put(key, response); 
        
        // Wait for expiration
        try {
            Thread.sleep(150); 
        } catch (InterruptedException e) { 
            Thread.currentThread().interrupt(); 
        }

        HttpResponse cached = shortCache.get(key); 
        assertThat(cached).isNull(); 
    }

    @Test
    @DisplayName("invalidates specific key")
    void invalidatesSpecificKey() { 
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); 

        cache.put(key, response); 
        cache.invalidate(key); 

        HttpResponse cached = cache.get(key); 
        assertThat(cached).isNull(); 
    }

    @Test
    @DisplayName("clear removes all entries")
    void clearRemovesAllEntries() { 
        cache.put("/api/data1", HttpResponse.ok200().build()); 
        cache.put("/api/data2", HttpResponse.ok200().build()); 

        cache.clear(); 

        assertThat(cache.getStats().currentSize()).isEqualTo(0); 
    }

    // ─── Cache statistics ─────────────────────────────────────────────────────

    @Test
    @DisplayName("tracks hit count")
    void tracksHitCount() { 
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); 

        cache.put(key, response); 
        cache.get(key); 
        cache.get(key); 

        assertThat(cache.getStats().hitCount()).isEqualTo(2); 
    }

    @Test
    @DisplayName("tracks miss count")
    void tracksMissCount() { 
        cache.get("/nonexistent");
        cache.get("/another");

        assertThat(cache.getStats().missCount()).isEqualTo(2); 
    }

    @Test
    @DisplayName("calculates hit rate correctly")
    void calculatesHitRateCorrectly() { 
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); 

        cache.put(key, response); 
        cache.get(key); // hit 
        cache.get("/nonexistent"); // miss

        double hitRate = cache.getStats().hitRate(); 
        assertThat(hitRate).isEqualTo(0.5); 
    }

    @Test
    @DisplayName("tracks eviction count")
    void tracksEvictionCount() { 
        HttpRequestCache shortCache = new HttpRequestCache(Duration.ofMillis(100), 2); 
        String key = "/api/data";
        HttpResponse response = HttpResponse.ok200().build(); 

        shortCache.put(key, response); 
        
        // Wait for expiration
        try {
            Thread.sleep(150); 
        } catch (InterruptedException e) { 
            Thread.currentThread().interrupt(); 
        }

        shortCache.get(key); // Triggers eviction 
        assertThat(shortCache.getStats().evictionCount()).isGreaterThan(0); 
    }

    @Test
    @DisplayName("reports current size correctly")
    void reportsCurrentSizeCorrectly() { 
        cache.put("/api/data1", HttpResponse.ok200().build()); 
        cache.put("/api/data2", HttpResponse.ok200().build()); 

        assertThat(cache.getStats().currentSize()).isEqualTo(2); 
    }

    @Test
    @DisplayName("calculates utilization correctly")
    void calculatesUtilizationCorrectly() { 
        HttpRequestCache smallCache = new HttpRequestCache(Duration.ofSeconds(60), 10); 
        smallCache.put("/api/data1", HttpResponse.ok200().build()); 
        smallCache.put("/api/data2", HttpResponse.ok200().build()); 

        double utilization = smallCache.getStats().utilization(); 
        assertThat(utilization).isEqualTo(0.2); 
    }

    // ─── Cache eviction ───────────────────────────────────────────────────────

    @Test
    @DisplayName("evicts oldest entry when at capacity")
    void evictsOldestEntryWhenAtCapacity() { 
        HttpRequestCache smallCache = new HttpRequestCache(Duration.ofSeconds(60), 2); 
        
        smallCache.put("/api/data1", HttpResponse.ok200().build()); 
        smallCache.put("/api/data2", HttpResponse.ok200().build()); 
        smallCache.put("/api/data3", HttpResponse.ok200().build()); // Should evict oldest 

        assertThat(smallCache.getStats().currentSize()).isEqualTo(2); 
        assertThat(smallCache.getStats().evictionCount()).isGreaterThan(0); 
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("handles null key gracefully in get")
    void handlesNullKeyInGet() { 
        HttpResponse cached = cache.get(null); 
        assertThat(cached).isNull(); 
    }

    @Test
    @DisplayName("handles null key gracefully in put")
    void handlesNullKeyInPut() { 
        cache.put(null, HttpResponse.ok200().build()); 
        assertThat(cache.getStats().currentSize()).isEqualTo(0); 
    }

    @Test
    @DisplayName("handles null response gracefully in put")
    void handlesNullResponseInPut() { 
        cache.put("/api/data", null); 
        assertThat(cache.getStats().currentSize()).isEqualTo(0); 
    }
}
