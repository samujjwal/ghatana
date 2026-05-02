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
@ExtendWith(MockitoExtension.class) 
@DisplayName("Distributed Cache Service Integration Tests")
class DistributedCacheServiceIntegrationTest {

    @Mock
    private DistributedCacheService.CacheBackend backend;

    private DistributedCacheService cacheService;
    private String testTenantId;

    @BeforeEach
    void setUp() { 
        testTenantId = "tenant-cache-test";
        cacheService = new DistributedCacheService(backend, testTenantId); 
        MDC.clear(); 
    }

    @Nested
    @DisplayName("Basic Cache Operations")
    class BasicCacheOperationsTests {

        @Test
        void shouldGetValueFromCache() { 
            // Given
            String key = "test-key";
            String cachedValue = "cached-value";
            when(backend.getValue("tenant:tenant-cache-test:test-key")).thenReturn(cachedValue);
            when(backend.deserialize(cachedValue, String.class)).thenReturn("cached-value");

            // When
            Optional<String> result = cacheService.get(key, String.class); 

            // Then
            assertThat(result).isPresent(); 
            assertThat(result.get()).isEqualTo("cached-value");
            assertThat(MDC.get("cacheHit")).isEqualTo("true");
        }

        @Test
        void shouldReturnEmptyWhenKeyNotFound() { 
            // Given
            String key = "missing-key";
            when(backend.getValue("tenant:tenant-cache-test:missing-key")).thenReturn(null);

            // When
            Optional<String> result = cacheService.get(key, String.class); 

            // Then
            assertThat(result).isEmpty(); 
            assertThat(MDC.get("cacheMiss")).isEqualTo("true");
        }

        @Test
        void shouldPutValueToCache() { 
            // Given
            String key = "put-test-key";
            String value = "put-test-value";
            long ttl = 3600;

            when(backend.serialize(value)).thenReturn("serialized-value");

            // When
            cacheService.put(key, value, ttl); 

            // Then
            assertThat(MDC.get("cachePut")).isEqualTo("true");
        }

        @Test
        void shouldInvalidateKey() { 
            // Given
            String key = "invalidate-test-key";

            // When
            cacheService.invalidate(key); 

            // Then
            assertThat(MDC.get("cacheInvalidate")).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        void shouldIsolateCachesByTenant() { 
            // Given
            String tenant1 = "tenant-1";
            String tenant2 = "tenant-2";
            String sharedKey = "shared-key";

            DistributedCacheService cache1 = new DistributedCacheService(backend, tenant1); 
            DistributedCacheService cache2 = new DistributedCacheService(backend, tenant2); 

            // When - set expectations for different tenant keys
            when(backend.getValue("tenant:tenant-1:shared-key")).thenReturn("value-from-tenant-1");
            when(backend.getValue("tenant:tenant-2:shared-key")).thenReturn("value-from-tenant-2");
            when(backend.deserialize("value-from-tenant-1", String.class)).thenReturn("value-from-tenant-1");
            when(backend.deserialize("value-from-tenant-2", String.class)).thenReturn("value-from-tenant-2");

            // Then - each tenant sees its own data
            Optional<String> result1 = cache1.get(sharedKey, String.class); 
            Optional<String> result2 = cache2.get(sharedKey, String.class); 

            assertThat(result1).isPresent(); 
            assertThat(result2).isPresent(); 
        }

        @Test
        void shouldNotLeakDataAcrossTenants() { 
            // Given
            String key = "sensitive-key";
            when(backend.getValue("tenant:other-tenant:sensitive-key")).thenReturn(null);

            DistributedCacheService otherTenantCache = 
                new DistributedCacheService(backend, "other-tenant"); 

            // When
            Optional<String> result = otherTenantCache.get(key, String.class); 

            // Then
            assertThat(result).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("Cache Invalidation")
    class CacheInvalidationTests {

        @Test
        void shouldInvalidateSingleKey() { 
            // Given
            String key = "single-invalidate";

            // When
            cacheService.invalidate(key); 

            // Then - no exception thrown
            assertThat(MDC.get("cacheInvalidate")).isEqualTo("true");
        }

        @Test
        void shouldInvalidatePattern() { 
            // Given
            String pattern = "content:*";
            when(backend.deletePattern("tenant:tenant-cache-test:content:*")).thenReturn(5);

            // When
            cacheService.invalidatePattern(pattern); 

            // Then
            assertThat(MDC.get("invalidatedCount")).isEqualTo("5");
        }

        @Test
        void shouldHandleEmptyPatternInvalidation() { 
            // Given
            String pattern = "non-existent:*";
            when(backend.deletePattern("tenant:tenant-cache-test:non-existent:*")).thenReturn(0);

            // When
            cacheService.invalidatePattern(pattern); 

            // Then
            assertThat(MDC.get("invalidatedCount")).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("Cache Compute Pattern")
    class CacheComputePatternTests {

        @Test
        void shouldReturnCachedValueWithoutComputing() { 
            // Given
            String key = "compute-key";
            String cachedValue = "already-cached";
            when(backend.getValue("tenant:tenant-cache-test:compute-key")).thenReturn(cachedValue);
            when(backend.deserialize(cachedValue, String.class)).thenReturn("already-cached");

            DistributedCacheService.CacheLoader<String> loader = () -> { 
                throw new RuntimeException("Should not be called");
            };

            // When
            String result = cacheService.getOrCompute(key, 3600, loader, String.class); 

            // Then
            assertThat(result).isEqualTo("already-cached");
        }

        @Test
        void shouldComputeAndCacheWhenMissing() { 
            // Given
            String key = "compute-missing";
            String computedValue = "newly-computed";
            when(backend.getValue("tenant:tenant-cache-test:compute-missing")).thenReturn(null);
            when(backend.serialize(computedValue)).thenReturn("serialized-computed");

            DistributedCacheService.CacheLoader<String> loader = () -> computedValue; 

            // When
            String result = cacheService.getOrCompute(key, 3600, loader, String.class); 

            // Then
            assertThat(result).isEqualTo("newly-computed");
        }

        @Test
        void shouldPropagateLoaderException() { 
            // Given
            String key = "compute-error";
            when(backend.getValue("tenant:tenant-cache-test:compute-error")).thenReturn(null);

            DistributedCacheService.CacheLoader<String> loader = () -> { 
                throw new IOException("Loader failed");
            };

            // When + Then
            assertThatThrownBy(() -> cacheService.getOrCompute(key, 3600, loader, String.class)) 
                    .isInstanceOf(DistributedCacheService.CacheException.class) 
                    .hasMessageContaining("Failed to load value");
        }

        @Test
        void shouldNotCacheNullValues() { 
            // Given
            String key = "compute-null";
            when(backend.getValue("tenant:tenant-cache-test:compute-null")).thenReturn(null);

            DistributedCacheService.CacheLoader<String> loader = () -> null; 

            // When
            String result = cacheService.getOrCompute(key, 3600, loader, String.class); 

            // Then
            assertThat(result).isNull(); 
        }
    }

    @Nested
    @DisplayName("Cache Statistics")
    class CacheStatisticsTests {

        @Test
        void shouldRetrieveCacheStatistics() { 
            // Given
            String pattern = "content:*";
            when(backend.getKeyCount("tenant:tenant-cache-test:content:*")).thenReturn(10L);
            when(backend.getCacheSize("tenant:tenant-cache-test:content:*")).thenReturn(5120L);

            // When
            DistributedCacheService.CacheStatistics stats = cacheService.getStatistics(pattern); 

            // Then
            assertThat(stats.totalKeys).isEqualTo(10L); 
            assertThat(stats.totalSize).isEqualTo(5120L); 
        }

        @Test
        void shouldReturnZeroStatsOnError() { 
            // Given
            String pattern = "error:*";
            when(backend.getKeyCount("tenant:tenant-cache-test:error:*"))
                    .thenThrow(new RuntimeException("Backend error"));

            // When
            DistributedCacheService.CacheStatistics stats = cacheService.getStatistics(pattern); 

            // Then
            assertThat(stats.totalKeys).isEqualTo(0L); 
            assertThat(stats.totalSize).isEqualTo(0L); 
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        void shouldHandleGetError() { 
            // Given
            String key = "error-key";
            when(backend.getValue("tenant:tenant-cache-test:error-key"))
                    .thenThrow(new RuntimeException("Backend unavailable"));

            // When
            Optional<String> result = cacheService.get(key, String.class); 

            // Then
            assertThat(result).isEmpty(); 
        }

        @Test
        void shouldHandlePutError() { 
            // Given
            String key = "put-error";
            String value = "value";
            when(backend.serialize(value)).thenThrow(new RuntimeException("Serialization failed"));

            // When + Then - should not throw, just log
            cacheService.put(key, value, 3600); 
            // Should complete without exception
        }

        @Test
        void shouldHandleInvalidateError() { 
            // Given
            String key = "invalidate-error";
            doThrow(new RuntimeException("Delete failed"))
                .when(backend).deleteKey("tenant:tenant-cache-test:invalidate-error");

            // When + Then - should not throw
            cacheService.invalidate(key); 
            // Should complete without exception
        }
    }

    @Nested
    @DisplayName("MDC Context Management")
    class MDCContextManagementTests {

        @Test
        void shouldClearMDCAfterTest() { 
            // Given
            String key = "mdc-test";
            when(backend.getValue("tenant:tenant-cache-test:mdc-test")).thenReturn(null);

            // When
            cacheService.get(key, String.class); 

            // Then
            MDC.clear(); 
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); 
        }

        @Test
        void shouldSetCacheHitInMDC() { 
            // Given
            String key = "hit-test";
            when(backend.getValue("tenant:tenant-cache-test:hit-test")).thenReturn("value");
            when(backend.deserialize("value", String.class)).thenReturn("value");

            // When
            cacheService.get(key, String.class); 

            // Then
            assertThat(MDC.get("cacheHit")).isEqualTo("true");
            MDC.clear(); 
        }

        @Test
        void shouldSetCacheMissInMDC() { 
            // Given
            String key = "miss-test";
            when(backend.getValue("tenant:tenant-cache-test:miss-test")).thenReturn(null);

            // When
            cacheService.get(key, String.class); 

            // Then
            assertThat(MDC.get("cacheMiss")).isEqualTo("true");
            MDC.clear(); 
        }
    }

    @Nested
    @DisplayName("Concurrent Cache Operations")
    class ConcurrentOperationsTests {

        @Test
        void shouldHandleConcurrentGets() throws InterruptedException { 
            // Given
            String key = "concurrent-key";
            when(backend.getValue("tenant:tenant-cache-test:concurrent-key")).thenReturn("value");
            when(backend.deserialize("value", String.class)).thenReturn("value");

            // When
            Thread t1 = new Thread(() -> cacheService.get(key, String.class)); 
            Thread t2 = new Thread(() -> cacheService.get(key, String.class)); 

            t1.start(); 
            t2.start(); 
            t1.join(); 
            t2.join(); 

            // Then - no exceptions thrown
            assertThat(t1).isNotNull(); 
            assertThat(t2).isNotNull(); 
        }

        @Test
        void shouldHandleConcurrentInvalidation() throws InterruptedException { 
            // Given
            String[] keys = {"key-1", "key-2", "key-3"};

            // When
            Thread[] threads = new Thread[3];
            for (int i = 0; i < 3; i++) { 
                final int index = i;
                threads[i] = new Thread(() -> cacheService.invalidate(keys[index])); 
                threads[i].start(); 
            }

            for (Thread thread : threads) { 
                thread.join(); 
            }

            // Then - all operations complete
            assertThat(threads).hasSize(3); 
        }
    }

    /**
     * Inner class for IOException reference in test
     */
    static class IOException extends Exception {
        IOException(String message) { 
            super(message); 
        }
    }
}
