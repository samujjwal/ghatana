package com.ghatana.platform.cache.consistency;

import com.ghatana.platform.cache.DistributedCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache consistency tests — validates read-your-writes, invalidation propagation,
 * tenant isolation, and stale-entry detection.
 *
 * @doc.type class
 * @doc.purpose Tests for cache consistency, invalidation, and tenant isolation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Cache Consistency Tests")
class CacheConsistencyTest {

    // ── In-memory BackEnd implementation ──────────────────────────────────────

    static class InMemoryCacheBackend implements DistributedCacheService.CacheBackend {
        final Map<String, String> store = new HashMap<>(); // GH-90000

        @Override public String getValue(String key) { return store.get(key); } // GH-90000
        @Override public void setValue(String key, String value, long ttlSeconds) { store.put(key, value); } // GH-90000
        @Override public void deleteKey(String key) { store.remove(key); } // GH-90000
        @Override public int deletePattern(String pattern) { // GH-90000
            long removed = store.keySet().stream().filter(k -> k.startsWith(pattern.replace("*", ""))) // GH-90000
                    .peek(store::remove).count(); // GH-90000
            return (int) removed; // GH-90000
        }
        @Override public long getKeyCount(String pattern) { return store.size(); } // GH-90000
        @Override public long getCacheSize(String pattern) { return store.size(); } // GH-90000
        @Override public <T> String serialize(T value) { return value == null ? null : value.toString(); } // GH-90000
        @SuppressWarnings("unchecked")
        @Override public <T> T deserialize(String value, Class<T> type) { // GH-90000
            if (type == String.class) return (T) value; // GH-90000
            throw new UnsupportedOperationException("Only String type supported in test backend");
        }
    }

    private InMemoryCacheBackend backend;
    private DistributedCacheService cacheService;

    @BeforeEach
    void setUp() { // GH-90000
        backend = new InMemoryCacheBackend(); // GH-90000
        cacheService = new DistributedCacheService(backend, "tenant-a"); // GH-90000
    }

    // ── Read-your-writes ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("read-your-writes consistency")
    class ReadYourWrites {

        @Test
        @DisplayName("value written is immediately readable in the same tenant scope")
        void valueWritten_isImmediatelyReadable() { // GH-90000
            cacheService.put("user:123", "Alice", 300L); // GH-90000

            Optional<String> result = cacheService.get("user:123", String.class); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("updated value overwrites old value")
        void updatedValue_overwritesOldValue() { // GH-90000
            cacheService.put("config:mode", "slow", 300L); // GH-90000
            cacheService.put("config:mode", "fast", 300L); // GH-90000

            Optional<String> result = cacheService.get("config:mode", String.class); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get()).isEqualTo("fast");
        }
    }

    // ── Invalidation ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("invalidation")
    class Invalidation {

        @Test
        @DisplayName("explicit invalidation removes key from cache")
        void explicitInvalidation_removesKeyFromCache() { // GH-90000
            cacheService.put("session:xyz", "active", 300L); // GH-90000
            cacheService.invalidate("session:xyz");

            Optional<String> result = cacheService.get("session:xyz", String.class); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("invalidating non-existent key is a no-op")
        void invalidatingNonExistentKey_isNoOp() { // GH-90000
            cacheService.invalidate("does-not-exist");
            // Should not throw
        }

        @Test
        @DisplayName("invalidating one key does not affect another key")
        void invalidatingOneKey_doesNotAffectAnotherKey() { // GH-90000
            cacheService.put("key-a", "valueA", 300L); // GH-90000
            cacheService.put("key-b", "valueB", 300L); // GH-90000

            cacheService.invalidate("key-a");

            assertThat(cacheService.get("key-a", String.class)).isEmpty(); // GH-90000
            assertThat(cacheService.get("key-b", String.class)).isPresent(); // GH-90000
        }
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("same key in different tenants returns different values")
        void sameKeyInDifferentTenants_returnsDifferentValues() { // GH-90000
            InMemoryCacheBackend sharedBackend = new InMemoryCacheBackend(); // GH-90000
            DistributedCacheService tenantACache = new DistributedCacheService(sharedBackend, "tenant-a"); // GH-90000
            DistributedCacheService tenantBCache = new DistributedCacheService(sharedBackend, "tenant-b"); // GH-90000

            tenantACache.put("user-count", "100", 300L); // GH-90000
            tenantBCache.put("user-count", "999", 300L); // GH-90000

            Optional<String> tenantAResult = tenantACache.get("user-count", String.class); // GH-90000
            Optional<String> tenantBResult = tenantBCache.get("user-count", String.class); // GH-90000

            assertThat(tenantAResult).isPresent(); // GH-90000
            assertThat(tenantBResult).isPresent(); // GH-90000
            assertThat(tenantAResult.get()).isEqualTo("100");
            assertThat(tenantBResult.get()).isEqualTo("999");
        }

        @Test
        @DisplayName("tenant A cache does not see tenant B entries")
        void tenantACacheDoesNotSeeTenantBEntries() { // GH-90000
            InMemoryCacheBackend sharedBackend = new InMemoryCacheBackend(); // GH-90000
            DistributedCacheService tenantACache = new DistributedCacheService(sharedBackend, "tenant-a"); // GH-90000
            DistributedCacheService tenantBCache = new DistributedCacheService(sharedBackend, "tenant-b"); // GH-90000

            tenantBCache.put("secret", "tenant-b-secret", 300L); // GH-90000

            Optional<String> tenantAResult = tenantACache.get("secret", String.class); // GH-90000

            assertThat(tenantAResult).isEmpty(); // GH-90000
        }
    }

    // ── Miss handling ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("miss handling")
    class MissHandling {

        @Test
        @DisplayName("get on empty cache returns empty optional")
        void getOnEmptyCache_returnsEmptyOptional() { // GH-90000
            Optional<String> result = cacheService.get("missing-key", String.class); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }
}
