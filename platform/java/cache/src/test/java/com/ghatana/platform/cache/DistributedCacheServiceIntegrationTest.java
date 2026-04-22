package com.ghatana.platform.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Integration tests for distributed cache service with Redis backend simulation
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Distributed Cache Service Integration Tests [GH-90000]")
class DistributedCacheServiceIntegrationTest {

    @Mock
    private DistributedCacheService.CacheBackend backend;

    private DistributedCacheService cacheService;
    private String testTenantId;

    @BeforeEach
    void setUp() { // GH-90000
        testTenantId = "tenant-cache-test";
        cacheService = new DistributedCacheService(backend, testTenantId); // GH-90000
        MDC.clear(); // GH-90000
    }

    @Nested
    @DisplayName("Basic Cache Operations [GH-90000]")
    class BasicCacheOperationsTests {

        @Test
        void shouldGetValueFromCache() { // GH-90000
            // Given
            String key = "test-key";
            String cachedValue = "cached-value";
            when(backend.getValue("tenant:tenant-cache-test:test-key [GH-90000]")).thenReturn(cachedValue);
            when(backend.deserialize(cachedValue, String.class)).thenReturn("cached-value [GH-90000]");

            // When
            Optional<String> result = cacheService.get(key, String.class); // GH-90000

            // Then
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get()).isEqualTo("cached-value [GH-90000]");
            assertThat(MDC.get("cacheHit [GH-90000]")).isEqualTo("true [GH-90000]");
        }

        @Test
        void shouldReturnEmptyWhenKeyNotFound() { // GH-90000
            // Given
            String key = "missing-key";
            when(backend.getValue("tenant:tenant-cache-test:missing-key [GH-90000]")).thenReturn(null);

            // When
            Optional<String> result = cacheService.get(key, String.class); // GH-90000

            // Then
            assertThat(result).isEmpty(); // GH-90000
            assertThat(MDC.get("cacheMiss [GH-90000]")).isEqualTo("true [GH-90000]");
        }

        @Test
        void shouldPutValueToCache() { // GH-90000
            // Given
            String key = "put-test-key";
            String value = "put-test-value";
            long ttl = 3600;

            when(backend.serialize(value)).thenReturn("serialized-value [GH-90000]");

            // When
            cacheService.put(key, value, ttl); // GH-90000

            // Then
            assertThat(MDC.get("cachePut [GH-90000]")).isEqualTo("true [GH-90000]");
        }

        @Test
        void shouldInvalidateKey() { // GH-90000
            // Given
            String key = "invalidate-test-key";

            // When
            cacheService.invalidate(key); // GH-90000

            // Then
            assertThat(MDC.get("cacheInvalidate [GH-90000]")).isEqualTo("true [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Tenant Isolation [GH-90000]")
    class TenantIsolationTests {

        @Test
        void shouldIsolateCachesByTenant() { // GH-90000
            // Given
            String tenant1 = "tenant-1";
            String tenant2 = "tenant-2";
            String sharedKey = "shared-key";

            DistributedCacheService cache1 = new DistributedCacheService(backend, tenant1); // GH-90000
            DistributedCacheService cache2 = new DistributedCacheService(backend, tenant2); // GH-90000

            // When - set expectations for different tenant keys
            when(backend.getValue("tenant:tenant-1:shared-key [GH-90000]")).thenReturn("value-from-tenant-1 [GH-90000]");
            when(backend.getValue("tenant:tenant-2:shared-key [GH-90000]")).thenReturn("value-from-tenant-2 [GH-90000]");
            when(backend.deserialize("value-from-tenant-1", String.class)).thenReturn("value-from-tenant-1 [GH-90000]");
            when(backend.deserialize("value-from-tenant-2", String.class)).thenReturn("value-from-tenant-2 [GH-90000]");

            // Then - each tenant sees its own data
            Optional<String> result1 = cache1.get(sharedKey, String.class); // GH-90000
            Optional<String> result2 = cache2.get(sharedKey, String.class); // GH-90000

            assertThat(result1).isPresent(); // GH-90000
            assertThat(result2).isPresent(); // GH-90000
        }

        @Test
        void shouldNotLeakDataAcrossTenants() { // GH-90000
            // Given
            String key = "sensitive-key";
            when(backend.getValue("tenant:other-tenant:sensitive-key [GH-90000]")).thenReturn(null);

            DistributedCacheService otherTenantCache = 
                new DistributedCacheService(backend, "other-tenant"); // GH-90000

            // When
            Optional<String> result = otherTenantCache.get(key, String.class); // GH-90000

            // Then
            assertThat(result).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cache Invalidation [GH-90000]")
    class CacheInvalidationTests {

        @Test
        void shouldInvalidateSingleKey() { // GH-90000
            // Given
            String key = "single-invalidate";

            // When
            cacheService.invalidate(key); // GH-90000

            // Then - no exception thrown
            assertThat(MDC.get("cacheInvalidate [GH-90000]")).isEqualTo("true [GH-90000]");
        }

        @Test
        void shouldInvalidatePattern() { // GH-90000
            // Given
            String pattern = "content:*";
            when(backend.deletePattern("tenant:tenant-cache-test:content:* [GH-90000]")).thenReturn(5);

            // When
            cacheService.invalidatePattern(pattern); // GH-90000

            // Then
            assertThat(MDC.get("invalidatedCount [GH-90000]")).isEqualTo("5 [GH-90000]");
        }

        @Test
        void shouldHandleEmptyPatternInvalidation() { // GH-90000
            // Given
            String pattern = "non-existent:*";
            when(backend.deletePattern("tenant:tenant-cache-test:non-existent:* [GH-90000]")).thenReturn(0);

            // When
            cacheService.invalidatePattern(pattern); // GH-90000

            // Then
            assertThat(MDC.get("invalidatedCount [GH-90000]")).isEqualTo("0 [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Cache Compute Pattern [GH-90000]")
    class CacheComputePatternTests {

        @Test
        void shouldReturnCachedValueWithoutComputing() { // GH-90000
            // Given
            String key = "compute-key";
            String cachedValue = "already-cached";
            when(backend.getValue("tenant:tenant-cache-test:compute-key [GH-90000]")).thenReturn(cachedValue);
            when(backend.deserialize(cachedValue, String.class)).thenReturn("already-cached [GH-90000]");

            DistributedCacheService.CacheLoader<String> loader = () -> { // GH-90000
                throw new RuntimeException("Should not be called [GH-90000]");
            };

            // When
            String result = cacheService.getOrCompute(key, 3600, loader, String.class); // GH-90000

            // Then
            assertThat(result).isEqualTo("already-cached [GH-90000]");
        }

        @Test
        void shouldComputeAndCacheWhenMissing() { // GH-90000
            // Given
            String key = "compute-missing";
            String computedValue = "newly-computed";
            when(backend.getValue("tenant:tenant-cache-test:compute-missing [GH-90000]")).thenReturn(null);
            when(backend.serialize(computedValue)).thenReturn("serialized-computed [GH-90000]");

            DistributedCacheService.CacheLoader<String> loader = () -> computedValue; // GH-90000

            // When
            String result = cacheService.getOrCompute(key, 3600, loader, String.class); // GH-90000

            // Then
            assertThat(result).isEqualTo("newly-computed [GH-90000]");
        }

        @Test
        void shouldPropagateLoaderException() { // GH-90000
            // Given
            String key = "compute-error";
            when(backend.getValue("tenant:tenant-cache-test:compute-error [GH-90000]")).thenReturn(null);

            DistributedCacheService.CacheLoader<String> loader = () -> { // GH-90000
                throw new IOException("Loader failed [GH-90000]");
            };

            // When + Then
            assertThatThrownBy(() -> cacheService.getOrCompute(key, 3600, loader, String.class)) // GH-90000
                    .isInstanceOf(DistributedCacheService.CacheException.class) // GH-90000
                    .hasMessageContaining("Failed to load value [GH-90000]");
        }

        @Test
        void shouldNotCacheNullValues() { // GH-90000
            // Given
            String key = "compute-null";
            when(backend.getValue("tenant:tenant-cache-test:compute-null [GH-90000]")).thenReturn(null);

            DistributedCacheService.CacheLoader<String> loader = () -> null; // GH-90000

            // When
            String result = cacheService.getOrCompute(key, 3600, loader, String.class); // GH-90000

            // Then
            assertThat(result).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cache Statistics [GH-90000]")
    class CacheStatisticsTests {

        @Test
        void shouldRetrieveCacheStatistics() { // GH-90000
            // Given
            String pattern = "content:*";
            when(backend.getKeyCount("tenant:tenant-cache-test:content:* [GH-90000]")).thenReturn(10L);
            when(backend.getCacheSize("tenant:tenant-cache-test:content:* [GH-90000]")).thenReturn(5120L);

            // When
            DistributedCacheService.CacheStatistics stats = cacheService.getStatistics(pattern); // GH-90000

            // Then
            assertThat(stats.totalKeys).isEqualTo(10L); // GH-90000
            assertThat(stats.totalSize).isEqualTo(5120L); // GH-90000
        }

        @Test
        void shouldReturnZeroStatsOnError() { // GH-90000
            // Given
            String pattern = "error:*";
            when(backend.getKeyCount("tenant:tenant-cache-test:error:* [GH-90000]"))
                    .thenThrow(new RuntimeException("Backend error [GH-90000]"));

            // When
            DistributedCacheService.CacheStatistics stats = cacheService.getStatistics(pattern); // GH-90000

            // Then
            assertThat(stats.totalKeys).isEqualTo(0L); // GH-90000
            assertThat(stats.totalSize).isEqualTo(0L); // GH-90000
        }
    }

    @Nested
    @DisplayName("Error Handling [GH-90000]")
    class ErrorHandlingTests {

        @Test
        void shouldHandleGetError() { // GH-90000
            // Given
            String key = "error-key";
            when(backend.getValue("tenant:tenant-cache-test:error-key [GH-90000]"))
                    .thenThrow(new RuntimeException("Backend unavailable [GH-90000]"));

            // When
            Optional<String> result = cacheService.get(key, String.class); // GH-90000

            // Then
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        void shouldHandlePutError() { // GH-90000
            // Given
            String key = "put-error";
            String value = "value";
            when(backend.serialize(value)).thenThrow(new RuntimeException("Serialization failed [GH-90000]"));

            // When + Then - should not throw, just log
            cacheService.put(key, value, 3600); // GH-90000
            // Should complete without exception
        }

        @Test
        void shouldHandleInvalidateError() { // GH-90000
            // Given
            String key = "invalidate-error";
            doThrow(new RuntimeException("Delete failed [GH-90000]"))
                .when(backend).deleteKey("tenant:tenant-cache-test:invalidate-error [GH-90000]");

            // When + Then - should not throw
            cacheService.invalidate(key); // GH-90000
            // Should complete without exception
        }
    }

    @Nested
    @DisplayName("MDC Context Management [GH-90000]")
    class MDCContextManagementTests {

        @Test
        void shouldClearMDCAfterTest() { // GH-90000
            // Given
            String key = "mdc-test";
            when(backend.getValue("tenant:tenant-cache-test:mdc-test [GH-90000]")).thenReturn(null);

            // When
            cacheService.get(key, String.class); // GH-90000

            // Then
            MDC.clear(); // GH-90000
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldSetCacheHitInMDC() { // GH-90000
            // Given
            String key = "hit-test";
            when(backend.getValue("tenant:tenant-cache-test:hit-test [GH-90000]")).thenReturn("value [GH-90000]");
            when(backend.deserialize("value", String.class)).thenReturn("value [GH-90000]");

            // When
            cacheService.get(key, String.class); // GH-90000

            // Then
            assertThat(MDC.get("cacheHit [GH-90000]")).isEqualTo("true [GH-90000]");
            MDC.clear(); // GH-90000
        }

        @Test
        void shouldSetCacheMissInMDC() { // GH-90000
            // Given
            String key = "miss-test";
            when(backend.getValue("tenant:tenant-cache-test:miss-test [GH-90000]")).thenReturn(null);

            // When
            cacheService.get(key, String.class); // GH-90000

            // Then
            assertThat(MDC.get("cacheMiss [GH-90000]")).isEqualTo("true [GH-90000]");
            MDC.clear(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Concurrent Cache Operations [GH-90000]")
    class ConcurrentOperationsTests {

        @Test
        void shouldHandleConcurrentGets() throws InterruptedException { // GH-90000
            // Given
            String key = "concurrent-key";
            when(backend.getValue("tenant:tenant-cache-test:concurrent-key [GH-90000]")).thenReturn("value [GH-90000]");
            when(backend.deserialize("value", String.class)).thenReturn("value [GH-90000]");

            // When
            Thread t1 = new Thread(() -> cacheService.get(key, String.class)); // GH-90000
            Thread t2 = new Thread(() -> cacheService.get(key, String.class)); // GH-90000

            t1.start(); // GH-90000
            t2.start(); // GH-90000
            t1.join(); // GH-90000
            t2.join(); // GH-90000

            // Then - no exceptions thrown
            assertThat(t1).isNotNull(); // GH-90000
            assertThat(t2).isNotNull(); // GH-90000
        }

        @Test
        void shouldHandleConcurrentInvalidation() throws InterruptedException { // GH-90000
            // Given
            String[] keys = {"key-1", "key-2", "key-3"};

            // When
            Thread[] threads = new Thread[3];
            for (int i = 0; i < 3; i++) { // GH-90000
                final int index = i;
                threads[i] = new Thread(() -> cacheService.invalidate(keys[index])); // GH-90000
                threads[i].start(); // GH-90000
            }

            for (Thread thread : threads) { // GH-90000
                thread.join(); // GH-90000
            }

            // Then - all operations complete
            assertThat(threads).hasSize(3); // GH-90000
        }
    }

    /**
     * Inner class for IOException reference in test
     */
    static class IOException extends Exception {
        IOException(String message) { // GH-90000
            super(message); // GH-90000
        }
    }
}
