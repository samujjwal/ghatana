package com.ghatana.platform.cache.ttl;

import com.ghatana.platform.cache.DistributedCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TTL expiration tests — validates time-based key eviction, TTL precision,
 * zero-TTL semantics, and behavior with maximum TTL values.
 *
 * @doc.type class
 * @doc.purpose Tests for cache TTL expiration behavior and time-based eviction
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("TTL Expiration Tests")
class TtlExpirationTest {

    // ── Time-aware in-memory backend ──────────────────────────────────────────

    static class TimedInMemoryCacheBackend implements DistributedCacheService.CacheBackend {
        record TimedEntry(String value, long expiresAtMs) {} // GH-90000

        private final Map<String, TimedEntry> store = new HashMap<>(); // GH-90000
        private long nowMs;

        TimedInMemoryCacheBackend(long nowMs) { // GH-90000
            this.nowMs = nowMs;
        }

        void advanceTimeMs(long ms) { // GH-90000
            nowMs += ms;
        }

        @Override
        public String getValue(String key) { // GH-90000
            TimedEntry entry = store.get(key); // GH-90000
            if (entry == null) return null; // GH-90000
            if (entry.expiresAtMs() > 0 && nowMs >= entry.expiresAtMs()) { // GH-90000
                store.remove(key); // GH-90000
                return null;
            }
            return entry.value(); // GH-90000
        }

        @Override
        public void setValue(String key, String value, long ttlSeconds) { // GH-90000
            long expiry = ttlSeconds > 0 ? nowMs + ttlSeconds * 1000 : 0;
            store.put(key, new TimedEntry(value, expiry)); // GH-90000
        }

        @Override public void deleteKey(String key) { store.remove(key); } // GH-90000
        @Override public int deletePattern(String pattern) { return 0; } // GH-90000
        @Override public long getKeyCount(String pattern) { return store.size(); } // GH-90000
        @Override public long getCacheSize(String pattern) { return store.size(); } // GH-90000
        @Override public <T> String serialize(T value) { return value == null ? null : value.toString(); } // GH-90000
        @SuppressWarnings("unchecked")
        @Override public <T> T deserialize(String value, Class<T> type) { return (T) value; } // GH-90000
    }

    // ── Time-based eviction ───────────────────────────────────────────────────

    @Nested
    @DisplayName("time-based eviction")
    class TimeBasedEviction {

        @Test
        @DisplayName("key is accessible before TTL expiry")
        void keyIsAccessible_beforeTtlExpiry() { // GH-90000
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); // GH-90000
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); // GH-90000

            cache.put("session:a", "active", 60L);  // TTL=60s // GH-90000
            backend.advanceTimeMs(30_000L);          // advance 30s // GH-90000

            Optional<String> result = cache.get("session:a", String.class); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get()).isEqualTo("active");
        }

        @Test
        @DisplayName("key is evicted after TTL expiry")
        void keyIsEvicted_afterTtlExpiry() { // GH-90000
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); // GH-90000
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); // GH-90000

            cache.put("session:b", "active", 10L);  // TTL=10s // GH-90000
            backend.advanceTimeMs(11_000L);          // advance 11s // GH-90000

            Optional<String> result = cache.get("session:b", String.class); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("key expires exactly at TTL boundary")
        void keyExpires_exactlyAtTtlBoundary() { // GH-90000
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); // GH-90000
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); // GH-90000

            cache.put("session:c", "active", 5L);  // TTL=5s = 5000ms // GH-90000
            backend.advanceTimeMs(5001L);            // 1ms past expiry // GH-90000

            Optional<String> result = cache.get("session:c", String.class); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ── No-TTL keys ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("no-TTL keys persist indefinitely")
    class NoTtlKeys {

        @Test
        @DisplayName("key with TTL=0 never expires")
        void keyWithTtl0_neverExpires() { // GH-90000
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); // GH-90000
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); // GH-90000

            cache.put("permanent", "forever", 0L);   // TTL=0 → no expiry // GH-90000
            backend.advanceTimeMs(Long.MAX_VALUE / 2); // GH-90000

            Optional<String> result = cache.get("permanent", String.class); // GH-90000

            assertThat(result).isPresent(); // GH-90000
        }
    }

    // ── Different TTLs ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("different TTLs for different keys")
    class DifferentTtls {

        @Test
        @DisplayName("short-lived key expires while long-lived key stays")
        void shortLivedKey_expires_whileLongLivedKeyStays() { // GH-90000
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); // GH-90000
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); // GH-90000

            cache.put("short", "ephemeral", 5L);    // 5s // GH-90000
            cache.put("long",  "durable",   300L);  // 300s // GH-90000

            backend.advanceTimeMs(10_000L);  // advance 10s // GH-90000

            assertThat(cache.get("short", String.class)).isEmpty(); // GH-90000
            assertThat(cache.get("long", String.class)).isPresent(); // GH-90000
        }
    }
}
