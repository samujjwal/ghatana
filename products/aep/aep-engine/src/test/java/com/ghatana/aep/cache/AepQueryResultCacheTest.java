/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepQueryResultCache} (AEP-004.2).
 */
@DisplayName("AepQueryResultCache — AEP-004.2")
class AepQueryResultCacheTest {

    private static final Duration TTL_30S = Duration.ofSeconds(30);

    private Instant now;
    private MutableClock clock;
    private AepQueryResultCache<String> cache;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-01-01T00:00:00Z");
        clock = new MutableClock(now);
        cache = AepQueryResultCache.<String>builder()
                .ttl(TTL_30S)
                .maxSize(100)
                .clock(clock)
                .build();
    }

    @Test
    @DisplayName("Cache miss loads value via loader and stores it")
    void cacheMissLoadsAndStores() {
        AtomicInteger calls = new AtomicInteger(0);
        String result = cache.get("key1", () -> {
            calls.incrementAndGet();
            return "value1";
        });

        assertThat(result).isEqualTo("value1");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
        assertThat(cache.stats().hits()).isEqualTo(0);
    }

    @Test
    @DisplayName("Subsequent calls within TTL return cached value without invoking loader")
    void cacheHitSkipsLoader() {
        AtomicInteger calls = new AtomicInteger(0);
        cache.get("key2", () -> { calls.incrementAndGet(); return "v2"; });
        cache.get("key2", () -> { calls.incrementAndGet(); return "v2-should-not-be-called"; });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    @DisplayName("Expired entry triggers a fresh load")
    void expiredEntryReloads() {
        AtomicInteger calls = new AtomicInteger(0);
        cache.get("key3", () -> { calls.incrementAndGet(); return "first"; });

        // Advance clock past TTL
        clock.advance(Duration.ofSeconds(31));
        String result = cache.get("key3", () -> { calls.incrementAndGet(); return "refreshed"; });

        assertThat(result).isEqualTo("refreshed");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("peek returns empty for unknown keys")
    void peekEmptyForUnknownKey() {
        assertThat(cache.peek("no-such-key")).isEmpty();
    }

    @Test
    @DisplayName("peek returns value for live entry")
    void peekLiveEntry() {
        cache.put("k", "hello");
        Optional<String> result = cache.peek("k");
        assertThat(result).contains("hello");
    }

    @Test
    @DisplayName("peek returns empty after TTL expires")
    void peekExpiredEntry() {
        cache.put("k", "hello");
        clock.advance(Duration.ofSeconds(31));
        assertThat(cache.peek("k")).isEmpty();
    }

    @Test
    @DisplayName("invalidate removes single entry")
    void invalidateSingleEntry() {
        cache.put("to-remove", "value");
        cache.invalidate("to-remove");
        assertThat(cache.peek("to-remove")).isEmpty();
    }

    @Test
    @DisplayName("invalidateByPrefix removes matching entries")
    void invalidateByPrefix() {
        cache.put("tenant:acme:config", "c1");
        cache.put("tenant:acme:patterns", "c2");
        cache.put("tenant:other:config", "c3");

        int removed = cache.invalidateByPrefix("tenant:acme:");
        assertThat(removed).isEqualTo(2);
        assertThat(cache.peek("tenant:acme:config")).isEmpty();
        assertThat(cache.peek("tenant:acme:patterns")).isEmpty();
        assertThat(cache.peek("tenant:other:config")).isPresent();
    }

    @Test
    @DisplayName("clear removes all entries")
    void clearAllEntries() {
        cache.put("a", "1");
        cache.put("b", "2");
        cache.clear();
        assertThat(cache.stats().size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Hit rate meets >80% target when cache is warm")
    void hitRateMeetsTarget() {
        // Pre-populate
        for (int i = 0; i < 10; i++) {
            cache.put("key-" + i, "v-" + i);
        }
        // Produce 80+ hits
        for (int i = 0; i < 80; i++) {
            cache.get("key-" + (i % 10), () -> "v");
        }
        // Produce some misses
        for (int i = 100; i < 110; i++) {
            cache.get("key-" + i, () -> "miss");
        }

        AepQueryResultCache.Stats stats = cache.stats();
        assertThat(stats.hitRate()).isGreaterThanOrEqualTo(0.80);
        assertThat(stats.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("Null key throws NullPointerException")
    void nullKeyThrows() {
        assertThatThrownBy(() -> cache.get(null, () -> "v"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Null loader throws NullPointerException")
    void nullLoaderThrows() {
        assertThatThrownBy(() -> cache.get("k", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Builder rejects non-positive TTL")
    void builderRejectsZeroTtl() {
        assertThatThrownBy(() -> AepQueryResultCache.builder().ttl(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects non-positive maxSize")
    void builderRejectsZeroMaxSize() {
        assertThatThrownBy(() -> AepQueryResultCache.builder().maxSize(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Helper: controllable clock ────────────────────────────────────────────

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) { this.instant = start; }

        void advance(Duration d) { instant = instant.plus(d); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}

