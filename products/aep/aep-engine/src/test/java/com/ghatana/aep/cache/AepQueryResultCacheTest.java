/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Unit tests for {@link AepQueryResultCache} (AEP-004.2). // GH-90000
 */
@DisplayName("AepQueryResultCache — AEP-004.2 [GH-90000]")
class AepQueryResultCacheTest {

    private static final Duration TTL_30S = Duration.ofSeconds(30); // GH-90000

    private Instant now;
    private MutableClock clock;
    private AepQueryResultCache<String> cache;

    @BeforeEach
    void setUp() { // GH-90000
        now = Instant.parse("2026-01-01T00:00:00Z [GH-90000]");
        clock = new MutableClock(now); // GH-90000
        cache = AepQueryResultCache.<String>builder() // GH-90000
                .ttl(TTL_30S) // GH-90000
                .maxSize(100) // GH-90000
                .clock(clock) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("Cache miss loads value via loader and stores it [GH-90000]")
    void cacheMissLoadsAndStores() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        String result = cache.get("key1", () -> { // GH-90000
            calls.incrementAndGet(); // GH-90000
            return "value1";
        });

        assertThat(result).isEqualTo("value1 [GH-90000]");
        assertThat(calls.get()).isEqualTo(1); // GH-90000
        assertThat(cache.stats().misses()).isEqualTo(1); // GH-90000
        assertThat(cache.stats().hits()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Subsequent calls within TTL return cached value without invoking loader [GH-90000]")
    void cacheHitSkipsLoader() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        cache.get("key2", () -> { calls.incrementAndGet(); return "v2"; }); // GH-90000
        cache.get("key2", () -> { calls.incrementAndGet(); return "v2-should-not-be-called"; }); // GH-90000

        assertThat(calls.get()).isEqualTo(1); // GH-90000
        assertThat(cache.stats().hits()).isEqualTo(1); // GH-90000
        assertThat(cache.stats().misses()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Expired entry triggers a fresh load [GH-90000]")
    void expiredEntryReloads() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        cache.get("key3", () -> { calls.incrementAndGet(); return "first"; }); // GH-90000

        // Advance clock past TTL
        clock.advance(Duration.ofSeconds(31)); // GH-90000
        String result = cache.get("key3", () -> { calls.incrementAndGet(); return "refreshed"; }); // GH-90000

        assertThat(result).isEqualTo("refreshed [GH-90000]");
        assertThat(calls.get()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("peek returns empty for unknown keys [GH-90000]")
    void peekEmptyForUnknownKey() { // GH-90000
        assertThat(cache.peek("no-such-key [GH-90000]")).isEmpty();
    }

    @Test
    @DisplayName("peek returns value for live entry [GH-90000]")
    void peekLiveEntry() { // GH-90000
        cache.put("k", "hello"); // GH-90000
        Optional<String> result = cache.peek("k [GH-90000]");
        assertThat(result).contains("hello [GH-90000]");
    }

    @Test
    @DisplayName("peek returns empty after TTL expires [GH-90000]")
    void peekExpiredEntry() { // GH-90000
        cache.put("k", "hello"); // GH-90000
        clock.advance(Duration.ofSeconds(31)); // GH-90000
        assertThat(cache.peek("k [GH-90000]")).isEmpty();
    }

    @Test
    @DisplayName("invalidate removes single entry [GH-90000]")
    void invalidateSingleEntry() { // GH-90000
        cache.put("to-remove", "value"); // GH-90000
        cache.invalidate("to-remove [GH-90000]");
        assertThat(cache.peek("to-remove [GH-90000]")).isEmpty();
    }

    @Test
    @DisplayName("invalidateByPrefix removes matching entries [GH-90000]")
    void invalidateByPrefix() { // GH-90000
        cache.put("tenant:acme:config", "c1"); // GH-90000
        cache.put("tenant:acme:patterns", "c2"); // GH-90000
        cache.put("tenant:other:config", "c3"); // GH-90000

        int removed = cache.invalidateByPrefix("tenant:acme: [GH-90000]");
        assertThat(removed).isEqualTo(2); // GH-90000
        assertThat(cache.peek("tenant:acme:config [GH-90000]")).isEmpty();
        assertThat(cache.peek("tenant:acme:patterns [GH-90000]")).isEmpty();
        assertThat(cache.peek("tenant:other:config [GH-90000]")).isPresent();
    }

    @Test
    @DisplayName("clear removes all entries [GH-90000]")
    void clearAllEntries() { // GH-90000
        cache.put("a", "1"); // GH-90000
        cache.put("b", "2"); // GH-90000
        cache.clear(); // GH-90000
        assertThat(cache.stats().size()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Hit rate meets >80% target when cache is warm [GH-90000]")
    void hitRateMeetsTarget() { // GH-90000
        // Pre-populate
        for (int i = 0; i < 10; i++) { // GH-90000
            cache.put("key-" + i, "v-" + i); // GH-90000
        }
        // Produce 80+ hits
        for (int i = 0; i < 80; i++) { // GH-90000
            cache.get("key-" + (i % 10), () -> "v"); // GH-90000
        }
        // Produce some misses
        for (int i = 100; i < 110; i++) { // GH-90000
            cache.get("key-" + i, () -> "miss"); // GH-90000
        }

        AepQueryResultCache.Stats stats = cache.stats(); // GH-90000
        assertThat(stats.hitRate()).isGreaterThanOrEqualTo(0.80); // GH-90000
        assertThat(stats.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Null key throws NullPointerException [GH-90000]")
    void nullKeyThrows() { // GH-90000
        assertThatThrownBy(() -> cache.get(null, () -> "v")) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Null loader throws NullPointerException [GH-90000]")
    void nullLoaderThrows() { // GH-90000
        assertThatThrownBy(() -> cache.get("k", null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects non-positive TTL [GH-90000]")
    void builderRejectsZeroTtl() { // GH-90000
        assertThatThrownBy(() -> AepQueryResultCache.builder().ttl(Duration.ZERO).build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects non-positive maxSize [GH-90000]")
    void builderRejectsZeroMaxSize() { // GH-90000
        assertThatThrownBy(() -> AepQueryResultCache.builder().maxSize(0).build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ─── Helper: controllable clock ────────────────────────────────────────────

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) { this.instant = start; } // GH-90000

        void advance(Duration d) { instant = instant.plus(d); } // GH-90000

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; } // GH-90000
        @Override public Clock withZone(java.time.ZoneId zone) { return this; } // GH-90000
        @Override public Instant instant() { return instant; } // GH-90000
    }
}
