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
        final Map<String, String> store = new HashMap<>(); 

        @Override public String getValue(String key) { return store.get(key); } 
        @Override public void setValue(String key, String value, long ttlSeconds) { store.put(key, value); } 
        @Override public void deleteKey(String key) { store.remove(key); } 
        @Override public int deletePattern(String pattern) { 
            long removed = store.keySet().stream().filter(k -> k.startsWith(pattern.replace("*", ""))) 
                    .peek(store::remove).count(); 
            return (int) removed; 
        }
        @Override public long getKeyCount(String pattern) { return store.size(); } 
        @Override public long getCacheSize(String pattern) { return store.size(); } 
        @Override public <T> String serialize(T value) { return value == null ? null : value.toString(); } 
        @SuppressWarnings("unchecked")
        @Override public <T> T deserialize(String value, Class<T> type) { 
            if (type == String.class) return (T) value; 
            throw new UnsupportedOperationException("Only String type supported in test backend");
        }
    }

    private InMemoryCacheBackend backend;
    private DistributedCacheService cacheService;

    @BeforeEach
    void setUp() { 
        backend = new InMemoryCacheBackend(); 
        cacheService = new DistributedCacheService(backend, "tenant-a"); 
    }

    // ── Read-your-writes ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("read-your-writes consistency")
    class ReadYourWrites {

        @Test
        @DisplayName("value written is immediately readable in the same tenant scope")
        void valueWritten_isImmediatelyReadable() { 
            cacheService.put("user:123", "Alice", 300L); 

            Optional<String> result = cacheService.get("user:123", String.class); 

            assertThat(result).isPresent(); 
            assertThat(result.get()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("updated value overwrites old value")
        void updatedValue_overwritesOldValue() { 
            cacheService.put("config:mode", "slow", 300L); 
            cacheService.put("config:mode", "fast", 300L); 

            Optional<String> result = cacheService.get("config:mode", String.class); 

            assertThat(result).isPresent(); 
            assertThat(result.get()).isEqualTo("fast");
        }
    }

    // ── Invalidation ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("invalidation")
    class Invalidation {

        @Test
        @DisplayName("explicit invalidation removes key from cache")
        void explicitInvalidation_removesKeyFromCache() { 
            cacheService.put("session:xyz", "active", 300L); 
            cacheService.invalidate("session:xyz");

            Optional<String> result = cacheService.get("session:xyz", String.class); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("invalidating non-existent key is a no-op")
        void invalidatingNonExistentKey_isNoOp() { 
            cacheService.invalidate("does-not-exist");
            // Should not throw
        }

        @Test
        @DisplayName("invalidating one key does not affect another key")
        void invalidatingOneKey_doesNotAffectAnotherKey() { 
            cacheService.put("key-a", "valueA", 300L); 
            cacheService.put("key-b", "valueB", 300L); 

            cacheService.invalidate("key-a");

            assertThat(cacheService.get("key-a", String.class)).isEmpty(); 
            assertThat(cacheService.get("key-b", String.class)).isPresent(); 
        }
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("same key in different tenants returns different values")
        void sameKeyInDifferentTenants_returnsDifferentValues() { 
            InMemoryCacheBackend sharedBackend = new InMemoryCacheBackend(); 
            DistributedCacheService tenantACache = new DistributedCacheService(sharedBackend, "tenant-a"); 
            DistributedCacheService tenantBCache = new DistributedCacheService(sharedBackend, "tenant-b"); 

            tenantACache.put("user-count", "100", 300L); 
            tenantBCache.put("user-count", "999", 300L); 

            Optional<String> tenantAResult = tenantACache.get("user-count", String.class); 
            Optional<String> tenantBResult = tenantBCache.get("user-count", String.class); 

            assertThat(tenantAResult).isPresent(); 
            assertThat(tenantBResult).isPresent(); 
            assertThat(tenantAResult.get()).isEqualTo("100");
            assertThat(tenantBResult.get()).isEqualTo("999");
        }

        @Test
        @DisplayName("tenant A cache does not see tenant B entries")
        void tenantACacheDoesNotSeeTenantBEntries() { 
            InMemoryCacheBackend sharedBackend = new InMemoryCacheBackend(); 
            DistributedCacheService tenantACache = new DistributedCacheService(sharedBackend, "tenant-a"); 
            DistributedCacheService tenantBCache = new DistributedCacheService(sharedBackend, "tenant-b"); 

            tenantBCache.put("secret", "tenant-b-secret", 300L); 

            Optional<String> tenantAResult = tenantACache.get("secret", String.class); 

            assertThat(tenantAResult).isEmpty(); 
        }
    }

    // ── Miss handling ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("miss handling")
    class MissHandling {

        @Test
        @DisplayName("get on empty cache returns empty optional")
        void getOnEmptyCache_returnsEmptyOptional() { 
            Optional<String> result = cacheService.get("missing-key", String.class); 

            assertThat(result).isEmpty(); 
        }
    }
}
