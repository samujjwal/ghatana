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
        record TimedEntry(String value, long expiresAtMs) {} 

        private final Map<String, TimedEntry> store = new HashMap<>(); 
        private long nowMs;

        TimedInMemoryCacheBackend(long nowMs) { 
            this.nowMs = nowMs;
        }

        void advanceTimeMs(long ms) { 
            nowMs += ms;
        }

        @Override
        public String getValue(String key) { 
            TimedEntry entry = store.get(key); 
            if (entry == null) return null; 
            if (entry.expiresAtMs() > 0 && nowMs >= entry.expiresAtMs()) { 
                store.remove(key); 
                return null;
            }
            return entry.value(); 
        }

        @Override
        public void setValue(String key, String value, long ttlSeconds) { 
            long expiry = ttlSeconds > 0 ? nowMs + ttlSeconds * 1000 : 0;
            store.put(key, new TimedEntry(value, expiry)); 
        }

        @Override public void deleteKey(String key) { store.remove(key); } 
        @Override public int deletePattern(String pattern) { return 0; } 
        @Override public long getKeyCount(String pattern) { return store.size(); } 
        @Override public long getCacheSize(String pattern) { return store.size(); } 
        @Override public <T> String serialize(T value) { return value == null ? null : value.toString(); } 
        @SuppressWarnings("unchecked")
        @Override public <T> T deserialize(String value, Class<T> type) { return (T) value; } 
    }

    // ── Time-based eviction ───────────────────────────────────────────────────

    @Nested
    @DisplayName("time-based eviction")
    class TimeBasedEviction {

        @Test
        @DisplayName("key is accessible before TTL expiry")
        void keyIsAccessible_beforeTtlExpiry() { 
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); 
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); 

            cache.put("session:a", "active", 60L);  // TTL=60s 
            backend.advanceTimeMs(30_000L);          // advance 30s 

            Optional<String> result = cache.get("session:a", String.class); 

            assertThat(result).isPresent(); 
            assertThat(result.get()).isEqualTo("active");
        }

        @Test
        @DisplayName("key is evicted after TTL expiry")
        void keyIsEvicted_afterTtlExpiry() { 
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); 
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); 

            cache.put("session:b", "active", 10L);  // TTL=10s 
            backend.advanceTimeMs(11_000L);          // advance 11s 

            Optional<String> result = cache.get("session:b", String.class); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("key expires exactly at TTL boundary")
        void keyExpires_exactlyAtTtlBoundary() { 
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); 
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); 

            cache.put("session:c", "active", 5L);  // TTL=5s = 5000ms 
            backend.advanceTimeMs(5001L);            // 1ms past expiry 

            Optional<String> result = cache.get("session:c", String.class); 

            assertThat(result).isEmpty(); 
        }
    }

    // ── No-TTL keys ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("no-TTL keys persist indefinitely")
    class NoTtlKeys {

        @Test
        @DisplayName("key with TTL=0 never expires")
        void keyWithTtl0_neverExpires() { 
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); 
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); 

            cache.put("permanent", "forever", 0L);   // TTL=0 → no expiry 
            backend.advanceTimeMs(Long.MAX_VALUE / 2); 

            Optional<String> result = cache.get("permanent", String.class); 

            assertThat(result).isPresent(); 
        }
    }

    // ── Different TTLs ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("different TTLs for different keys")
    class DifferentTtls {

        @Test
        @DisplayName("short-lived key expires while long-lived key stays")
        void shortLivedKey_expires_whileLongLivedKeyStays() { 
            TimedInMemoryCacheBackend backend = new TimedInMemoryCacheBackend(0L); 
            DistributedCacheService cache = new DistributedCacheService(backend, "tenant"); 

            cache.put("short", "ephemeral", 5L);    // 5s 
            cache.put("long",  "durable",   300L);  // 300s 

            backend.advanceTimeMs(10_000L);  // advance 10s 

            assertThat(cache.get("short", String.class)).isEmpty(); 
            assertThat(cache.get("long", String.class)).isPresent(); 
        }
    }
}
