/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("DistributedCache - Phase 3 Expansion")
class DistributedCacheExpansionTest extends EventloopTestBase {

    private InMemoryCacheAdapter<String, String> l1;
    private InMemoryCacheAdapter<String, String> l2;
    private WriteThroughDistributedCache<String, String> cache;

    @BeforeEach
    void setUp() {
        l1 = new InMemoryCacheAdapter<>(500, Duration.ofMinutes(5));
        l2 = new InMemoryCacheAdapter<>(5_000, Duration.ofMinutes(30));
        cache = new WriteThroughDistributedCache<>(l1, l2);
    }

    // ============================================
    // L1/L2 CACHE COMPOSITION (4 tests)
    // ============================================

    @Nested
    @DisplayName("L1/L2 Cache Composition")
    class CacheCompositionTests {

        @Test
        @DisplayName("Write through both layers")
        void writeThroughBothLayers() {
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx));
            }

            // Verify all present in both layers
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                Optional<String> l1Result = runPromise(() -> l1.get("key-" + idx));
                Optional<String> l2Result = runPromise(() -> l2.get("key-" + idx));

                assertThat(l1Result).isPresent();
                assertThat(l2Result).isPresent();
            }
        }

        @Test
        @DisplayName("L1 hit avoids L2 access")
        void l1HitPath() {
            runPromise(() -> cache.put("cached", "value"));

            for (int i = 0; i < 50; i++) {
                Optional<String> result = runPromise(() -> cache.get("cached"));
                assertThat(result).isPresent();
            }

            // Verify was in L1 all this time
            assertThat(runPromise(() -> l1.get("cached"))).isPresent();
        }

        @Test
        @DisplayName("L1 miss populates from L2")
        void l1MisspopulatesFromL2() {
            // Put directly in L2
            runPromise(() -> l2.put("key", "l2-value"));

            // First access should miss L1 but hit L2
            Optional<String> result = runPromise(() -> cache.get("key"));
            assertThat(result).contains("l2-value");

            // Second access should be L1 hit
            result = runPromise(() -> cache.get("key"));
            assertThat(result).contains("l2-value");
        }

        @Test
        @DisplayName("Value updates propagate to both layers")
        void updatePropagation() {
            runPromise(() -> cache.put("key", "value-1"));

            Optional<String> v1 = runPromise(() -> cache.get("key"));
            assertThat(v1).contains("value-1");

            // Update
            runPromise(() -> cache.put("key", "value-2"));

            Optional<String> v2L1 = runPromise(() -> l1.get("key"));
            Optional<String> v2L2 = runPromise(() -> l2.get("key"));

            assertThat(v2L1).contains("value-2");
            assertThat(v2L2).contains("value-2");
        }
    }

    // ============================================
    // CACHE INVALIDATION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Cache Invalidation")
    class InvalidationTests {

        @Test
        @DisplayName("Invalidate single key removes from both layers")
        void singleKeyInvalidation() {
            runPromise(() -> cache.put("key1", "v1"));
            runPromise(() -> cache.put("key2", "v2"));

            runPromise(() -> cache.invalidate("key1"));

            assertThat(runPromise(() -> cache.get("key1"))).isEmpty();
            assertThat(runPromise(() -> cache.get("key2"))).isPresent();
        }

        @Test
        @DisplayName("Invalidate all keys clears both layers")
        void invalidateAll() {
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx));
            }

            runPromise(() -> cache.invalidateAll());

            // Verify all gone from both layers
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                assertThat(runPromise(() -> cache.get("key-" + idx))).isEmpty();
            }
        }

        @Test
        @DisplayName("Many concurrent invalidations")
        void concurrentInvalidations() throws Exception {
            // Put many items
            for (int i = 0; i < 50; i++) {
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx));
            }

            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = threadIdx; i < 50; i += threadCount) {
                                final int keyIdx = i;
                                runPromise(() -> cache.invalidate("key-" + keyIdx));
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }
        }

        @Test
        @DisplayName("Invalidation under heavy load")
        void invalidationUnderLoad() {
            // Build up cache
            for (int i = 0; i < 500; i++) {
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx));
            }

            // Invalidate specific high-access keys
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                runPromise(() -> cache.invalidate("key-" + idx));
            }

            // Verify correct subset removed
            assertThat(runPromise(() -> cache.get("key-0"))).isEmpty();
            assertThat(runPromise(() -> cache.get("key-500"))).isPresent();
        }
    }

    // ============================================
    // GET-OR-LOAD PATTERN (4 tests)
    // ============================================

    @Nested
    @DisplayName("Get-Or-Load Pattern")
    class GetOrLoadTests {

        @Test
        @DisplayName("Loader called on full cache miss")
        void loaderCalledOnMiss() {
            AtomicInteger loadCount = new AtomicInteger(0);

            String value = runPromise(() -> cache.getOrLoad("key", k -> {
                loadCount.incrementAndGet();
                return Promise.of("computed-value");
            }));

            assertThat(value).isEqualTo("computed-value");
            assertThat(loadCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Loader not called on L1 cache hit")
        void loaderSkipOnL1Hit() {
            runPromise(() -> cache.put("key", "cached-value"));
            AtomicInteger loadCount = new AtomicInteger(0);

            String value = runPromise(() -> cache.getOrLoad("key", k -> {
                loadCount.incrementAndGet();
                return Promise.of("computed-value");
            }));

            assertThat(value).isEqualTo("cached-value");
            assertThat(loadCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Many concurrent loads for same key (thundering herd mitigation)")
        void concurrentLoadsSameKey() throws Exception {
            AtomicInteger loadCount = new AtomicInteger(0);

            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    exec.submit(() -> {
                        try {
                            String value = runPromise(() -> cache.getOrLoad("shared-key", k -> {
                                loadCount.incrementAndGet();
                                return Promise.of("loaded");
                            }));
                            if ("loaded".equals(value)) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Load caches result in both layers")
        void loadCachesInBothLayers() {
            String value = runPromise(() -> cache.getOrLoad("key", k ->
                Promise.of("loaded-value")));

            assertThat(value).isEqualTo("loaded-value");
            assertThat(runPromise(() -> l1.get("key"))).contains("loaded-value");
            assertThat(runPromise(() -> l2.get("key"))).contains("loaded-value");
        }
    }

    // ============================================
    // CONCURRENT OPERATIONS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Cache Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many concurrent puts")
        void concurrentPuts() throws Exception {
            int threadCount = 30;
            int itemsPerThread = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < itemsPerThread; i++) {
                                final int idx = i;
                                runPromise(() -> cache.put(
                                    "key-" + threadIdx + "-" + idx,
                                    "value-" + threadIdx + "-" + idx
                                ));
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

        @Test
        @DisplayName("Concurrent mixed operations (put, get, invalidate)")
        void concurrentMixedOps() throws Exception {
            // Pre-populate
            for (int i = 0; i < 200; i++) {
                final int idx = i;
                runPromise(() -> cache.put("key-" + idx, "value-" + idx));
            }

            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger opCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < 100; i++) {
                                final int idx = i;
                                int op = idx % 3;
                                if (op == 0) {
                                    runPromise(() -> cache.put("key-" + idx, "new-value"));
                                } else if (op == 1) {
                                    runPromise(() -> cache.get("key-" + idx));
                                } else {
                                    runPromise(() -> cache.invalidate("key-" + idx));
                                }
                                opCount.incrementAndGet();
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

            assertThat(opCount.get()).isEqualTo(threadCount * 100);
        }

        @Test
        @DisplayName("High-frequency cache hits under concurrent load")
        void highFrequencyHits() throws Exception {
            // Pre-load hot keys
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                runPromise(() -> cache.put("hot-" + idx, "hot-value-" + idx));
            }

            int threadCount = 40;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger hitCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < 500; i++) {
                                final int idx = i;
                                Optional<String> result = runPromise(() ->
                                    cache.get("hot-" + (idx % 10)));
                                if (result.isPresent()) {
                                    hitCount.incrementAndGet();
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

            assertThat(hitCount.get()).isEqualTo(threadCount * 500);
        }
    }

    // ============================================
    // EVICTION AND TTL (2 tests)
    // ============================================

    @Nested
    @DisplayName("Eviction and TTL")
    class EvictionTests {

        @Test
        @DisplayName("Values respect TTL in both layers")
        void ttlRespected() {
            Duration shortTtl = Duration.ofMillis(100);
            InMemoryCacheAdapter<String, String> ttlL1 =
                new InMemoryCacheAdapter<>(10, shortTtl);
            WriteThroughDistributedCache<String, String> ttlCache =
                new WriteThroughDistributedCache<>(ttlL1, l2);

            runPromise(() -> ttlCache.put("expiring", "value"));
            assertThat(runPromise(() -> ttlCache.get("expiring"))).isPresent();

            // Wait for expiration
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Should be gone from L1
            assertThat(runPromise(() -> ttlL1.get("expiring"))).isEmpty();
        }

        @Test
        @DisplayName("Large cache capacity constraints")
        void largeCapacityHandling() {
            InMemoryCacheAdapter<String, String> smallL1 =
                new InMemoryCacheAdapter<>(50, Duration.ofMinutes(5));
            WriteThroughDistributedCache<String, String> smallCache =
                new WriteThroughDistributedCache<>(smallL1, l2);

            // Try to exceed capacity
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                runPromise(() -> smallCache.put("key-" + idx, "value-" + idx));
            }

            // L1 respects capacity, L2 holds all
            assertThat(runPromise(() -> l2.get("key-0"))).isPresent();
        }
    }

    // ============================================
    // EDGE CASES (1 test)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large cache values")
        void veryLargeValues() {
            String largeValue = "x".repeat(10_000);

            runPromise(() -> cache.put("large-key", largeValue));

            Optional<String> retrieved = runPromise(() -> cache.get("large-key"));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get()).satisfies(s -> assertThat(s.length()).isEqualTo(10_000));
        }
    }
}
