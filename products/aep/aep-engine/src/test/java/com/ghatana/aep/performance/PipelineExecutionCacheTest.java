/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PipelineExecutionCache} (AEP-006.1). 
 */
@DisplayName("PipelineExecutionCache — AEP-006.1")
class PipelineExecutionCacheTest {

    private MutableClock clock;
    private PipelineExecutionCache cache;

    @BeforeEach
    void setUp() { 
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        cache = PipelineExecutionCache.builder() 
                .ttl(Duration.ofMinutes(10)) 
                .maxSize(100) 
                .clock(clock) 
                .build(); 
    }

    @Test
    @DisplayName("get returns empty on cache miss")
    void getMissReturnsEmpty() { 
        Optional<byte[]> result = cache.get("tenant1", "pipe1", "hash1"); 
        assertThat(result).isEmpty(); 
        assertThat(cache.stats().misses()).isEqualTo(1); 
    }

    @Test
    @DisplayName("get returns cached value on hit")
    void getCachedValue() { 
        byte[] data = "result".getBytes(); 
        cache.put("tenant1", "pipe1", "hash1", data); 

        Optional<byte[]> result = cache.get("tenant1", "pipe1", "hash1"); 
        assertThat(result).isPresent(); 
        assertThat(result.get()).isEqualTo(data); 
        assertThat(cache.stats().hits()).isEqualTo(1); 
    }

    @Test
    @DisplayName("get returns empty after TTL expires")
    void getEmptyAfterTtl() { 
        cache.put("tenant1", "pipe1", "hash1", "data".getBytes()); 
        clock.advance(Duration.ofMinutes(11)); 

        assertThat(cache.get("tenant1", "pipe1", "hash1")).isEmpty(); 
    }

    @Test
    @DisplayName("Different input hashes are cached independently")
    void differentHashesAreIndependent() { 
        cache.put("t", "p", "hash-a", "result-a".getBytes()); 
        cache.put("t", "p", "hash-b", "result-b".getBytes()); 

        assertThat(cache.get("t", "p", "hash-a")).isPresent(); 
        assertThat(cache.get("t", "p", "hash-b")).isPresent(); 
        assertThat(cache.get("t", "p", "hash-c")).isEmpty(); 
    }

    @Test
    @DisplayName("invalidatePipeline removes all entries for that pipeline")
    void invalidatePipelineRemovesEntries() { 
        cache.put("t", "pipe-x", "h1", "r1".getBytes()); 
        cache.put("t", "pipe-x", "h2", "r2".getBytes()); 
        cache.put("t", "pipe-y", "h1", "r3".getBytes()); 

        cache.invalidatePipeline("t", "pipe-x"); 

        assertThat(cache.get("t", "pipe-x", "h1")).isEmpty(); 
        assertThat(cache.get("t", "pipe-x", "h2")).isEmpty(); 
        assertThat(cache.get("t", "pipe-y", "h1")).isPresent(); 
    }

    @Test
    @DisplayName("clear removes all entries")
    void clearRemovesAll() { 
        cache.put("t1", "p1", "h1", "d".getBytes()); 
        cache.put("t2", "p2", "h2", "d".getBytes()); 
        cache.clear(); 

        assertThat(cache.stats().size()).isEqualTo(0); 
    }

    @Test
    @DisplayName("Builder rejects null result in put")
    void putRejectsNullResult() { 
        assertThatThrownBy(() -> cache.put("t", "p", "h", null)) 
                .isInstanceOf(NullPointerException.class); 
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant start) { this.instant = start; } 
        void advance(Duration d) { instant = instant.plus(d); } 
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; } 
        @Override public Clock withZone(java.time.ZoneId z) { return this; } 
        @Override public Instant instant() { return instant; } 
    }
}
