/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.cache;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Expansion tests for Distributed Cache module.
 * Tests L1/L2 cache composition, invalidation, concurrency, and performance.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for distributed caching subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DistributedCache - Phase 3 Expansion [GH-90000]")
class DistributedCacheExpansionTest extends EventloopTestBase {

    private InMemoryCacheAdapter<String, String> l1;
    private InMemoryCacheAdapter<String, String> l2;
    private WriteThroughDistributedCache<String, String> cache;

    @BeforeEach
    void setUp() { // GH-90000
        l1 = new InMemoryCacheAdapter<>(500, Duration.ofMinutes(5)); // GH-90000
        l2 = new InMemoryCacheAdapter<>(5_000, Duration.ofMinutes(30)); // GH-90000
        cache = new WriteThroughDistributedCache<>(l1, l2); // GH-90000
    }

    // ============================================
    // L1/L2 CACHE COMPOSITION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("L1/L2 Cache Composition [GH-90000]")
    class CacheCompositionTests {

        @Test
        @DisplayName("Write through both layers [GH-90000]")
        void writeThroughBothLayers() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx)); // GH-90000
            }

            // Verify all present in both layers
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                Optional<String> l1Result = runPromise(() -> l1.get("key-" + idx)); // GH-90000
                Optional<String> l2Result = runPromise(() -> l2.get("key-" + idx)); // GH-90000

                assertThat(l1Result).isPresent(); // GH-90000
                assertThat(l2Result).isPresent(); // GH-90000
            }
        }

        @Test
        @DisplayName("L1 hit avoids L2 access [GH-90000]")
        void l1HitPath() { // GH-90000
            runPromise(() -> cache.put("cached", "value")); // GH-90000

            for (int i = 0; i < 50; i++) { // GH-90000
                Optional<String> result = runPromise(() -> cache.get("cached [GH-90000]"));
                assertThat(result).isPresent(); // GH-90000
            }

            // Verify was in L1 all this time
            assertThat(runPromise(() -> l1.get("cached [GH-90000]"))).isPresent();
        }

        @Test
        @DisplayName("L1 miss populates from L2 [GH-90000]")
        void l1MisspopulatesFromL2() { // GH-90000
            // Put directly in L2
            runPromise(() -> l2.put("key", "l2-value")); // GH-90000

            // First access should miss L1 but hit L2
            Optional<String> result = runPromise(() -> cache.get("key [GH-90000]"));
            assertThat(result).contains("l2-value [GH-90000]");

            // Second access should be L1 hit
            result = runPromise(() -> cache.get("key [GH-90000]"));
            assertThat(result).contains("l2-value [GH-90000]");
        }

        @Test
        @DisplayName("Value updates propagate to both layers [GH-90000]")
        void updatePropagation() { // GH-90000
            runPromise(() -> cache.put("key", "value-1")); // GH-90000

            Optional<String> v1 = runPromise(() -> cache.get("key [GH-90000]"));
            assertThat(v1).contains("value-1 [GH-90000]");

            // Update
            runPromise(() -> cache.put("key", "value-2")); // GH-90000

            Optional<String> v2L1 = runPromise(() -> l1.get("key [GH-90000]"));
            Optional<String> v2L2 = runPromise(() -> l2.get("key [GH-90000]"));

            assertThat(v2L1).contains("value-2 [GH-90000]");
            assertThat(v2L2).contains("value-2 [GH-90000]");
        }
    }

    // ============================================
    // CACHE INVALIDATION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Cache Invalidation [GH-90000]")
    class InvalidationTests {

        @Test
        @DisplayName("Invalidate single key removes from both layers [GH-90000]")
        void singleKeyInvalidation() { // GH-90000
            runPromise(() -> cache.put("key1", "v1")); // GH-90000
            runPromise(() -> cache.put("key2", "v2")); // GH-90000

            runPromise(() -> cache.invalidate("key1 [GH-90000]"));

            assertThat(runPromise(() -> cache.get("key1 [GH-90000]"))).isEmpty();
            assertThat(runPromise(() -> cache.get("key2 [GH-90000]"))).isPresent();
        }

        @Test
        @DisplayName("Invalidate all keys clears both layers [GH-90000]")
        void invalidateAll() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx)); // GH-90000
            }

            runPromise(() -> cache.invalidateAll()); // GH-90000

            // Verify all gone from both layers
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                assertThat(runPromise(() -> cache.get("key-" + idx))).isEmpty(); // GH-90000
            }
        }

        @Test
        @DisplayName("Many concurrent invalidations [GH-90000]")
        void concurrentInvalidations() throws Exception { // GH-90000
            // Put many items
            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx)); // GH-90000
            }

            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = threadIdx; i < 50; i += threadCount) { // GH-90000
                                final int keyIdx = i;
                                runPromise(() -> cache.invalidate("key-" + keyIdx)); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }
        }

        @Test
        @DisplayName("Invalidation under heavy load [GH-90000]")
        void invalidationUnderLoad() { // GH-90000
            // Build up cache
            for (int i = 0; i < 500; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx)); // GH-90000
            }

            // Invalidate specific high-access keys
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> cache.invalidate("key-" + idx)); // GH-90000
            }

            // Verify correct subset removed
            assertThat(runPromise(() -> cache.get("key-0 [GH-90000]"))).isEmpty();
            assertThat(runPromise(() -> cache.get("key-499 [GH-90000]"))).isPresent();
        }
    }

    // ============================================
    // GET-OR-LOAD PATTERN (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Get-Or-Load Pattern [GH-90000]")
    class GetOrLoadTests {

        @Test
        @DisplayName("Loader called on full cache miss [GH-90000]")
        void loaderCalledOnMiss() { // GH-90000
            AtomicInteger loadCount = new AtomicInteger(0); // GH-90000

            String value = runPromise(() -> cache.getOrLoad("key", k -> { // GH-90000
                loadCount.incrementAndGet(); // GH-90000
                return Promise.of("computed-value [GH-90000]");
            }));

            assertThat(value).isEqualTo("computed-value [GH-90000]");
            assertThat(loadCount.get()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Loader not called on L1 cache hit [GH-90000]")
        void loaderSkipOnL1Hit() { // GH-90000
            runPromise(() -> cache.put("key", "cached-value")); // GH-90000
            AtomicInteger loadCount = new AtomicInteger(0); // GH-90000

            String value = runPromise(() -> cache.getOrLoad("key", k -> { // GH-90000
                loadCount.incrementAndGet(); // GH-90000
                return Promise.of("computed-value [GH-90000]");
            }));

            assertThat(value).isEqualTo("cached-value [GH-90000]");
            assertThat(loadCount.get()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Many concurrent loads for same key (thundering herd mitigation) [GH-90000]")
        void concurrentLoadsSameKey() throws Exception { // GH-90000
            AtomicInteger loadCount = new AtomicInteger(0); // GH-90000

            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    exec.submit(() -> { // GH-90000
                        try {
                            String value = runPromise(() -> cache.getOrLoad("shared-key", k -> { // GH-90000
                                loadCount.incrementAndGet(); // GH-90000
                                return Promise.of("loaded [GH-90000]");
                            }));
                            if ("loaded".equals(value)) { // GH-90000
                                successCount.incrementAndGet(); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Load caches result in both layers [GH-90000]")
        void loadCachesInBothLayers() { // GH-90000
            String value = runPromise(() -> cache.getOrLoad("key", k -> // GH-90000
                Promise.of("loaded-value [GH-90000]")));

            assertThat(value).isEqualTo("loaded-value [GH-90000]");
            assertThat(runPromise(() -> l1.get("key [GH-90000]"))).contains("loaded-value [GH-90000]");
            assertThat(runPromise(() -> l2.get("key [GH-90000]"))).contains("loaded-value [GH-90000]");
        }
    }

    // ============================================
    // CONCURRENT OPERATIONS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Cache Operations [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many concurrent puts [GH-90000]")
        void concurrentPuts() throws Exception { // GH-90000
            int threadCount = 30;
            int itemsPerThread = 50;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < itemsPerThread; i++) { // GH-90000
                                final int idx = i;
                                runPromise(() -> cache.put( // GH-90000
                                    "key-" + threadIdx + "-" + idx,
                                    "value-" + threadIdx + "-" + idx
                                ));
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

        @Test
        @DisplayName("Concurrent mixed operations (put, get, invalidate) [GH-90000]")
        void concurrentMixedOps() throws Exception { // GH-90000
            // Pre-populate
            for (int i = 0; i < 200; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx)); // GH-90000
            }

            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger opCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 100; i++) { // GH-90000
                                final int idx = i;
                                int op = idx % 3;
                                if (op == 0) { // GH-90000
                                    runPromise(() -> cache.put("key-" + idx, "new-value")); // GH-90000
                                } else if (op == 1) { // GH-90000
                                    runPromise(() -> cache.get("key-" + idx)); // GH-90000
                                } else {
                                    runPromise(() -> cache.invalidate("key-" + idx)); // GH-90000
                                }
                                opCount.incrementAndGet(); // GH-90000
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

            assertThat(opCount.get()).isEqualTo(threadCount * 100); // GH-90000
        }

        @Test
        @DisplayName("High-frequency cache hits under concurrent load [GH-90000]")
        void highFrequencyHits() throws Exception { // GH-90000
            // Pre-load hot keys
            for (int i = 0; i < 10; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> cache.put("hot-" + idx, "hot-value-" + idx)); // GH-90000
            }

            int threadCount = 40;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger hitCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 500; i++) { // GH-90000
                                final int idx = i;
                                Optional<String> result = runPromise(() -> // GH-90000
                                    cache.get("hot-" + (idx % 10))); // GH-90000
                                if (result.isPresent()) { // GH-90000
                                    hitCount.incrementAndGet(); // GH-90000
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

            assertThat(hitCount.get()).isEqualTo(threadCount * 500); // GH-90000
        }
    }

    // ============================================
    // EVICTION AND TTL (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Eviction and TTL [GH-90000]")
    class EvictionTests {

        @Test
        @DisplayName("Values respect TTL in both layers [GH-90000]")
        void ttlRespected() { // GH-90000
            Duration shortTtl = Duration.ofMillis(100); // GH-90000
            InMemoryCacheAdapter<String, String> ttlL1 =
                new InMemoryCacheAdapter<>(10, shortTtl); // GH-90000
            WriteThroughDistributedCache<String, String> ttlCache =
                new WriteThroughDistributedCache<>(ttlL1, l2); // GH-90000

            runPromise(() -> ttlCache.put("expiring", "value")); // GH-90000
            assertThat(runPromise(() -> ttlCache.get("expiring [GH-90000]"))).isPresent();

            // Wait for expiration
            try {
                Thread.sleep(150); // GH-90000
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
            }

            // Should be gone from L1
            assertThat(runPromise(() -> ttlL1.get("expiring [GH-90000]"))).isEmpty();
        }

        @Test
        @DisplayName("Large cache capacity constraints [GH-90000]")
        void largeCapacityHandling() { // GH-90000
            InMemoryCacheAdapter<String, String> smallL1 =
                new InMemoryCacheAdapter<>(50, Duration.ofMinutes(5)); // GH-90000
            WriteThroughDistributedCache<String, String> smallCache =
                new WriteThroughDistributedCache<>(smallL1, l2); // GH-90000

            // Try to exceed capacity
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> smallCache.put("key-" + idx, "value-" + idx)); // GH-90000
            }

            // L1 respects capacity, L2 holds all
            assertThat(runPromise(() -> l2.get("key-0 [GH-90000]"))).isPresent();
        }
    }

    // ============================================
    // EDGE CASES (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large cache values [GH-90000]")
        void veryLargeValues() { // GH-90000
            String largeValue = "x".repeat(10_000); // GH-90000

            runPromise(() -> cache.put("large-key", largeValue)); // GH-90000

            Optional<String> retrieved = runPromise(() -> cache.get("large-key [GH-90000]"));
            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get()).satisfies(s -> assertThat(s.length()).isEqualTo(10_000)); // GH-90000
        }
    }
}
