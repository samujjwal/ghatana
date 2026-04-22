/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Unit tests for {@link PipelineExecutionCache} (AEP-006.1). // GH-90000
 */
@DisplayName("PipelineExecutionCache — AEP-006.1 [GH-90000]")
class PipelineExecutionCacheTest {

    private MutableClock clock;
    private PipelineExecutionCache cache;

    @BeforeEach
    void setUp() { // GH-90000
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z [GH-90000]"));
        cache = PipelineExecutionCache.builder() // GH-90000
                .ttl(Duration.ofMinutes(10)) // GH-90000
                .maxSize(100) // GH-90000
                .clock(clock) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("get returns empty on cache miss [GH-90000]")
    void getMissReturnsEmpty() { // GH-90000
        Optional<byte[]> result = cache.get("tenant1", "pipe1", "hash1"); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
        assertThat(cache.stats().misses()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("get returns cached value on hit [GH-90000]")
    void getCachedValue() { // GH-90000
        byte[] data = "result".getBytes(); // GH-90000
        cache.put("tenant1", "pipe1", "hash1", data); // GH-90000

        Optional<byte[]> result = cache.get("tenant1", "pipe1", "hash1"); // GH-90000
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get()).isEqualTo(data); // GH-90000
        assertThat(cache.stats().hits()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("get returns empty after TTL expires [GH-90000]")
    void getEmptyAfterTtl() { // GH-90000
        cache.put("tenant1", "pipe1", "hash1", "data".getBytes()); // GH-90000
        clock.advance(Duration.ofMinutes(11)); // GH-90000

        assertThat(cache.get("tenant1", "pipe1", "hash1")).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Different input hashes are cached independently [GH-90000]")
    void differentHashesAreIndependent() { // GH-90000
        cache.put("t", "p", "hash-a", "result-a".getBytes()); // GH-90000
        cache.put("t", "p", "hash-b", "result-b".getBytes()); // GH-90000

        assertThat(cache.get("t", "p", "hash-a")).isPresent(); // GH-90000
        assertThat(cache.get("t", "p", "hash-b")).isPresent(); // GH-90000
        assertThat(cache.get("t", "p", "hash-c")).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("invalidatePipeline removes all entries for that pipeline [GH-90000]")
    void invalidatePipelineRemovesEntries() { // GH-90000
        cache.put("t", "pipe-x", "h1", "r1".getBytes()); // GH-90000
        cache.put("t", "pipe-x", "h2", "r2".getBytes()); // GH-90000
        cache.put("t", "pipe-y", "h1", "r3".getBytes()); // GH-90000

        cache.invalidatePipeline("t", "pipe-x"); // GH-90000

        assertThat(cache.get("t", "pipe-x", "h1")).isEmpty(); // GH-90000
        assertThat(cache.get("t", "pipe-x", "h2")).isEmpty(); // GH-90000
        assertThat(cache.get("t", "pipe-y", "h1")).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("clear removes all entries [GH-90000]")
    void clearRemovesAll() { // GH-90000
        cache.put("t1", "p1", "h1", "d".getBytes()); // GH-90000
        cache.put("t2", "p2", "h2", "d".getBytes()); // GH-90000
        cache.clear(); // GH-90000

        assertThat(cache.stats().size()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects null result in put [GH-90000]")
    void putRejectsNullResult() { // GH-90000
        assertThatThrownBy(() -> cache.put("t", "p", "h", null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant start) { this.instant = start; } // GH-90000
        void advance(Duration d) { instant = instant.plus(d); } // GH-90000
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; } // GH-90000
        @Override public Clock withZone(java.time.ZoneId z) { return this; } // GH-90000
        @Override public Instant instant() { return instant; } // GH-90000
    }
}
