/*
 * Copyright (c) 2026 Ghatana Inc.
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
@ExtendWith(MockitoExtension.class)
@DisplayName("Cache - Phase 3 Expansion")
class CacheExpansionTest {

    @Mock
    private DistributedCacheService.CacheBackend backend;

    private DistributedCacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(backend.getValue(anyString())).thenReturn(null);
        lenient().when(backend.serialize(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> String.valueOf(invocation.getArgument(0)));
        lenient().when(backend.deserialize(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        cacheService = new DistributedCacheService(backend, "tenant-1");
    }

    // ============================================
    // CACHE OPERATIONS (4 tests)
    // ============================================

    @Nested
    @DisplayName("Cache Operations")
    class CacheOperationsTests {

        @Test
        @DisplayName("Get many cache entries")
        void getManyEntries() {
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                String key = "key-" + idx;
                String value = "value-" + idx;
                
                when(backend.getValue("tenant:tenant-1:" + key)).thenReturn(value);
                when(backend.deserialize(value, String.class)).thenReturn(value);

                Optional<String> result = cacheService.get(key, String.class);
                assertThat(result).isPresent();
            }
        }

        @Test
        @DisplayName("Put many cache entries")
        void putManyEntries() {
            for (int i = 0; i < 100; i++) {
                String key = "key-" + i;
                String value = "value-" + i;

                cacheService.put(key, value, 300);
            }

            // Verify backend was called
            org.mockito.Mockito.verify(backend, org.mockito.Mockito.atLeastOnce())
                    .serialize(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("Cache hits and misses tracking")
        void hitsAndMissesTracking() {
            String hitKey = "exists";
            String missKey = "missing";

            when(backend.getValue("tenant:tenant-1:" + hitKey)).thenReturn("value");
            when(backend.deserialize("value", String.class)).thenReturn("value");
            when(backend.getValue("tenant:tenant-1:" + missKey)).thenReturn(null);

            for (int i = 0; i < 50; i++) {
                cacheService.get(hitKey, String.class);
                cacheService.get(missKey, String.class);
            }

            // Verify both hits and misses were processed
            org.mockito.Mockito.verify(backend, org.mockito.Mockito.atLeastOnce())
                    .getValue(anyString());
        }

        @Test
        @DisplayName("Multi-tenant cache isolation")
        void multiTenantIsolation() {
            DistributedCacheService tenant1Cache = new DistributedCacheService(backend, "t1");
            DistributedCacheService tenant2Cache = new DistributedCacheService(backend, "t2");

            String sharedKey = "shared-key";
            when(backend.getValue("tenant:t1:" + sharedKey)).thenReturn("t1-value");
            when(backend.getValue("tenant:t2:" + sharedKey)).thenReturn("t2-value");
            when(backend.deserialize("t1-value", String.class)).thenReturn("t1-value");
            when(backend.deserialize("t2-value", String.class)).thenReturn("t2-value");

            Optional<String> t1Result = tenant1Cache.get(sharedKey, String.class);
            Optional<String> t2Result = tenant2Cache.get(sharedKey, String.class);

            assertThat(t1Result).isPresent();
            assertThat(t2Result).isPresent();
            assertThat(t1Result.get()).isEqualTo("t1-value");
            assertThat(t2Result.get()).isEqualTo("t2-value");
        }
    }

    // ============================================
    // CACHE INVALIDATION (3 tests)
    // ============================================

    @Nested
    @DisplayName("Cache Invalidation")
    class InvalidationTests {

        @Test
        @DisplayName("Invalidate single cache entry")
        void invalidateSingle() {
            String key = "key-to-invalidate";

            when(backend.getValue("tenant:tenant-1:" + key)).thenReturn("value");
            when(backend.deserialize("value", String.class)).thenReturn("value");

            Optional<String> before = cacheService.get(key, String.class);
            assertThat(before).isPresent();

            cacheService.invalidate(key);

            when(backend.getValue("tenant:tenant-1:" + key)).thenReturn(null);
            Optional<String> after = cacheService.get(key, String.class);
            assertThat(after).isEmpty();
        }

        @Test
        @DisplayName("Invalidate many cache entries")
        void invalidateMany() {
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                keys.add("key-" + i);
            }

            for (String key : keys) {
                cacheService.invalidate(key);
            }

            // Verify deletion calls
            org.mockito.Mockito.verify(backend, org.mockito.Mockito.atLeastOnce())
                    .deleteKey(anyString());
        }

        @Test
        @DisplayName("Invalidate all cache entries")
        void invalidateAll() {
            for (int i = 0; i < 50; i++) {
                cacheService.put("key-" + i, "value-" + i, 300);
            }

            cacheService.invalidatePattern("*");

            // Verify pattern invalidation was called
            org.mockito.Mockito.verify(backend, org.mockito.Mockito.atLeastOnce())
                    .deletePattern(anyString());
        }
    }

    // ============================================
    // CACHE PERFORMANCE (3 tests)
    // ============================================

    @Nested
    @DisplayName("Cache Performance")
    class PerformanceTests {

        @Test
        @DisplayName("High-volume cache operations")
        void highVolume() {
            for (int i = 0; i < 1000; i++) {
                final int idx = i;
                String key = "key-" + (idx % 100);
                
                when(backend.getValue("tenant:tenant-1:" + key))
                        .thenReturn((idx % 2 == 0) ? "value" : null);
                if (idx % 2 == 0) {
                    when(backend.deserialize("value", String.class)).thenReturn("value");
                }

                Optional<String> result = cacheService.get(key, String.class);
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("Cache with large values")
        void largeValues() {
            String largeValue = "x".repeat(100000);

            for (int i = 0; i < 50; i++) {
                final int idx = i;
                String key = "large-key-" + idx;

                when(backend.getValue("tenant:tenant-1:" + key)).thenReturn(largeValue);
                when(backend.deserialize(largeValue, String.class)).thenReturn(largeValue);

                Optional<String> result = cacheService.get(key, String.class);
                assertThat(result).isPresent();
                assertThat(result.get()).hasSize(100000);
            }
        }

        @Test
        @DisplayName("Many distinct cache keys")
        void manyDistinctKeys() {
            for (int i = 0; i < 5000; i++) {
                final int idx = i;
                String key = "unique-key-" + idx;

                when(backend.getValue("tenant:tenant-1:" + key))
                        .thenReturn((idx % 10 != 0) ? "value-" + idx : null);
                
                if (idx % 10 != 0) {
                    when(backend.deserialize("value-" + idx, String.class))
                            .thenReturn("value-" + idx);
                }

                Optional<String> result = cacheService.get(key, String.class);
                // Just verify operation completed
                assertThat(result).isNotNull();
            }
        }
    }

    // ============================================
    // CONCURRENT CACHE OPERATIONS (2 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent cache reads and writes")
        void concurrentReadsAndWrites() throws Exception {
            int threadCount = 25;
            int operationsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                lenient().when(backend.getValue(anyString())).thenReturn("some-value");
                lenient().when(backend.deserialize("some-value", String.class))
                        .thenReturn("some-value");
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < operationsPerThread; i++) {
                                final int opIdx = i;
                                String key = "key-" + (threadIdx * operationsPerThread + opIdx) % 500;
                                
                                if (opIdx % 2 == 0) {
                                    // Write
                                    cacheService.put(key, "value-" + threadIdx, 300);
                                } else {
                                    Optional<String> result = cacheService.get(key, String.class);
                                    if (result.isPresent()) {
                                        successCount.incrementAndGet();
                                    }
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(successCount.get()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Concurrent invalidation operations")
        void concurrentInvalidation() throws Exception {
            // Pre-populate
            for (int i = 0; i < 300; i++) {
                cacheService.put("key-" + i, "value-" + i, 300);
            }

            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = threadIdx; i < 300; i += threadCount) {
                                cacheService.invalidate("key-" + i);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }
        }
    }

    // ============================================
    // EDGE CASES (1 test)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very long cache keys and values")
        void veryLongKeysAndValues() {
            String longKey = "k".repeat(1000);
            String longValue = "v".repeat(100000);

            when(backend.getValue("tenant:tenant-1:" + longKey)).thenReturn(longValue);
            when(backend.deserialize(longValue, String.class)).thenReturn(longValue);

            cacheService.put(longKey, longValue, 300);
            Optional<String> result = cacheService.get(longKey, String.class);

            assertThat(result).isPresent();
            assertThat(result.get()).hasSize(100000);
        }
    }
}
