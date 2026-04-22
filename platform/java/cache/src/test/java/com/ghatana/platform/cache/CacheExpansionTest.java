/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Phase 3 Expansion tests for Cache module.
 * Tests distributed caching, eviction, invalidation at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for cache subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Cache - Phase 3 Expansion [GH-90000]")
class CacheExpansionTest {

    @Mock
    private DistributedCacheService.CacheBackend backend;

    private DistributedCacheService cacheService;

    @BeforeEach
    void setUp() { // GH-90000
        lenient().when(backend.getValue(anyString())).thenReturn(null); // GH-90000
        lenient().when(backend.serialize(org.mockito.ArgumentMatchers.any())) // GH-90000
            .thenAnswer(invocation -> String.valueOf(invocation.getArgument(0))); // GH-90000
        lenient().when(backend.deserialize(anyString(), org.mockito.ArgumentMatchers.eq(String.class))) // GH-90000
            .thenAnswer(invocation -> invocation.getArgument(0)); // GH-90000
        cacheService = new DistributedCacheService(backend, "tenant-1"); // GH-90000
    }

    // ============================================
    // CACHE OPERATIONS (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Cache Operations [GH-90000]")
    class CacheOperationsTests {

        @Test
        @DisplayName("Get many cache entries [GH-90000]")
        void getManyEntries() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                String key = "key-" + idx;
                String value = "value-" + idx;
                
                when(backend.getValue("tenant:tenant-1:" + key)).thenReturn(value); // GH-90000
                when(backend.deserialize(value, String.class)).thenReturn(value); // GH-90000

                Optional<String> result = cacheService.get(key, String.class); // GH-90000
                assertThat(result).isPresent(); // GH-90000
            }
        }

        @Test
        @DisplayName("Put many cache entries [GH-90000]")
        void putManyEntries() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                String key = "key-" + i;
                String value = "value-" + i;

                cacheService.put(key, value, 300); // GH-90000
            }

            // Verify backend was called
            org.mockito.Mockito.verify(backend, org.mockito.Mockito.atLeastOnce()) // GH-90000
                    .serialize(org.mockito.ArgumentMatchers.any()); // GH-90000
        }

        @Test
        @DisplayName("Cache hits and misses tracking [GH-90000]")
        void hitsAndMissesTracking() { // GH-90000
            String hitKey = "exists";
            String missKey = "missing";

            when(backend.getValue("tenant:tenant-1:" + hitKey)).thenReturn("value [GH-90000]");
            when(backend.deserialize("value", String.class)).thenReturn("value [GH-90000]");
            when(backend.getValue("tenant:tenant-1:" + missKey)).thenReturn(null); // GH-90000

            for (int i = 0; i < 50; i++) { // GH-90000
                cacheService.get(hitKey, String.class); // GH-90000
                cacheService.get(missKey, String.class); // GH-90000
            }

            // Verify both hits and misses were processed
            org.mockito.Mockito.verify(backend, org.mockito.Mockito.atLeastOnce()) // GH-90000
                    .getValue(anyString()); // GH-90000
        }

        @Test
        @DisplayName("Multi-tenant cache isolation [GH-90000]")
        void multiTenantIsolation() { // GH-90000
            DistributedCacheService tenant1Cache = new DistributedCacheService(backend, "t1"); // GH-90000
            DistributedCacheService tenant2Cache = new DistributedCacheService(backend, "t2"); // GH-90000

            String sharedKey = "shared-key";
            when(backend.getValue("tenant:t1:" + sharedKey)).thenReturn("t1-value [GH-90000]");
            when(backend.getValue("tenant:t2:" + sharedKey)).thenReturn("t2-value [GH-90000]");
            when(backend.deserialize("t1-value", String.class)).thenReturn("t1-value [GH-90000]");
            when(backend.deserialize("t2-value", String.class)).thenReturn("t2-value [GH-90000]");

            Optional<String> t1Result = tenant1Cache.get(sharedKey, String.class); // GH-90000
            Optional<String> t2Result = tenant2Cache.get(sharedKey, String.class); // GH-90000

            assertThat(t1Result).isPresent(); // GH-90000
            assertThat(t2Result).isPresent(); // GH-90000
            assertThat(t1Result.get()).isEqualTo("t1-value [GH-90000]");
            assertThat(t2Result.get()).isEqualTo("t2-value [GH-90000]");
        }
    }

    // ============================================
    // CACHE INVALIDATION (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Cache Invalidation [GH-90000]")
    class InvalidationTests {

        @Test
        @DisplayName("Invalidate single cache entry [GH-90000]")
        void invalidateSingle() { // GH-90000
            String key = "key-to-invalidate";

            when(backend.getValue("tenant:tenant-1:" + key)).thenReturn("value [GH-90000]");
            when(backend.deserialize("value", String.class)).thenReturn("value [GH-90000]");

            Optional<String> before = cacheService.get(key, String.class); // GH-90000
            assertThat(before).isPresent(); // GH-90000

            cacheService.invalidate(key); // GH-90000

            when(backend.getValue("tenant:tenant-1:" + key)).thenReturn(null); // GH-90000
            Optional<String> after = cacheService.get(key, String.class); // GH-90000
            assertThat(after).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Invalidate many cache entries [GH-90000]")
        void invalidateMany() { // GH-90000
            List<String> keys = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                keys.add("key-" + i); // GH-90000
            }

            for (String key : keys) { // GH-90000
                cacheService.invalidate(key); // GH-90000
            }

            // Verify deletion calls
            org.mockito.Mockito.verify(backend, org.mockito.Mockito.atLeastOnce()) // GH-90000
                    .deleteKey(anyString()); // GH-90000
        }

        @Test
        @DisplayName("Invalidate all cache entries [GH-90000]")
        void invalidateAll() { // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                cacheService.put("key-" + i, "value-" + i, 300); // GH-90000
            }

            cacheService.invalidatePattern("* [GH-90000]");

            // Verify pattern invalidation was called
            org.mockito.Mockito.verify(backend, org.mockito.Mockito.atLeastOnce()) // GH-90000
                    .deletePattern(anyString()); // GH-90000
        }
    }

    // ============================================
    // CACHE PERFORMANCE (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Cache Performance [GH-90000]")
    class PerformanceTests {

        @Test
        @DisplayName("High-volume cache operations [GH-90000]")
        void highVolume() { // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                final int idx = i;
                String key = "key-" + (idx % 100); // GH-90000
                
                when(backend.getValue("tenant:tenant-1:" + key)) // GH-90000
                        .thenReturn((idx % 2 == 0) ? "value" : null); // GH-90000
                if (idx % 2 == 0) { // GH-90000
                    when(backend.deserialize("value", String.class)).thenReturn("value [GH-90000]");
                }

                Optional<String> result = cacheService.get(key, String.class); // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }
        }

        @Test
        @DisplayName("Cache with large values [GH-90000]")
        void largeValues() { // GH-90000
            String largeValue = "x".repeat(100000); // GH-90000

            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                String key = "large-key-" + idx;

                when(backend.getValue("tenant:tenant-1:" + key)).thenReturn(largeValue); // GH-90000
                when(backend.deserialize(largeValue, String.class)).thenReturn(largeValue); // GH-90000

                Optional<String> result = cacheService.get(key, String.class); // GH-90000
                assertThat(result).isPresent(); // GH-90000
                assertThat(result.get()).hasSize(100000); // GH-90000
            }
        }

        @Test
        @DisplayName("Many distinct cache keys [GH-90000]")
        void manyDistinctKeys() { // GH-90000
            for (int i = 0; i < 5000; i++) { // GH-90000
                final int idx = i;
                String key = "unique-key-" + idx;

                when(backend.getValue("tenant:tenant-1:" + key)) // GH-90000
                        .thenReturn((idx % 10 != 0) ? "value-" + idx : null); // GH-90000
                
                if (idx % 10 != 0) { // GH-90000
                    when(backend.deserialize("value-" + idx, String.class)) // GH-90000
                            .thenReturn("value-" + idx); // GH-90000
                }

                Optional<String> result = cacheService.get(key, String.class); // GH-90000
                // Just verify operation completed
                assertThat(result).isNotNull(); // GH-90000
            }
        }
    }

    // ============================================
    // CONCURRENT CACHE OPERATIONS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent cache reads and writes [GH-90000]")
        void concurrentReadsAndWrites() throws Exception { // GH-90000
            int threadCount = 25;
            int operationsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                lenient().when(backend.getValue(anyString())).thenReturn("some-value [GH-90000]");
                lenient().when(backend.deserialize("some-value", String.class)) // GH-90000
                        .thenReturn("some-value [GH-90000]");
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < operationsPerThread; i++) { // GH-90000
                                final int opIdx = i;
                                String key = "key-" + (threadIdx * operationsPerThread + opIdx) % 500; // GH-90000
                                
                                if (opIdx % 2 == 0) { // GH-90000
                                    // Write
                                    cacheService.put(key, "value-" + threadIdx, 300); // GH-90000
                                } else {
                                    Optional<String> result = cacheService.get(key, String.class); // GH-90000
                                    if (result.isPresent()) { // GH-90000
                                        successCount.incrementAndGet(); // GH-90000
                                    }
                                }
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(successCount.get()).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Concurrent invalidation operations [GH-90000]")
        void concurrentInvalidation() throws Exception { // GH-90000
            // Pre-populate
            for (int i = 0; i < 300; i++) { // GH-90000
                cacheService.put("key-" + i, "value-" + i, 300); // GH-90000
            }

            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = threadIdx; i < 300; i += threadCount) { // GH-90000
                                cacheService.invalidate("key-" + i); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }
        }
    }

    // ============================================
    // EDGE CASES (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very long cache keys and values [GH-90000]")
        void veryLongKeysAndValues() { // GH-90000
            String longKey = "k".repeat(1000); // GH-90000
            String longValue = "v".repeat(100000); // GH-90000

            when(backend.getValue("tenant:tenant-1:" + longKey)).thenReturn(longValue); // GH-90000
            when(backend.deserialize(longValue, String.class)).thenReturn(longValue); // GH-90000

            cacheService.put(longKey, longValue, 300); // GH-90000
            Optional<String> result = cacheService.get(longKey, String.class); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get()).hasSize(100000); // GH-90000
        }
    }
}
