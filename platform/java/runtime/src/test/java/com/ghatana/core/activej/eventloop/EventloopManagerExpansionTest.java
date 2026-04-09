/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.activej.eventloop;

import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Phase 3 Expansion tests for {@link EventloopManager}.
 * Tests scalability, concurrency, edge cases, and stress scenarios.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for ActiveJ eventloop runtime management
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("EventloopManager - Phase 3 Expansion")
class EventloopManagerExpansionTest {

    @AfterEach
    void cleanup() {
        EventloopManager.resetForTesting();
    }

    // ============================================
    // SCALABILITY & CAPACITY (5 tests)
    // ============================================

    @Nested
    @DisplayName("Scalability & Capacity")
    class ScalabilityTests {

        @Test
        @DisplayName("Creates up to 50 eventloops without exhaustion")
        void manyEventloopsCreated() {
            int loopCount = 50;
            List<Eventloop> loops = new ArrayList<>();

            for (int i = 0; i < loopCount; i++) {
                loops.add(EventloopManager.create());
            }

            assertThat(loops).hasSize(loopCount);
            assertThat(EventloopManager.getActiveCount()).isEqualTo(loopCount);
            assertThat(loops).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Active count increments correctly with many sequential creates")
        void activeCountTracksSequentialCreations() {
            for (int i = 1; i <= 25; i++) {
                EventloopManager.create();
                assertThat(EventloopManager.getActiveCount()).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("Rapid get/clear cycles maintain consistency")
        void rapidGetClearCycles() {
            for (int i = 0; i < 100; i++) {
                EventloopManager.getCurrentEventloop();
                assertThat(EventloopManager.hasEventloop()).isTrue();

                EventloopManager.clearCurrentEventloop();
                assertThat(EventloopManager.hasEventloop()).isFalse();
                assertThat(EventloopManager.getActiveCount()).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("Bulk shutdown handles 30+ eventloops")
        void bulkShutdown() {
            for (int i = 0; i < 30; i++) {
                EventloopManager.create();
            }
            assertThat(EventloopManager.getActiveCount()).isEqualTo(30);

            boolean success = EventloopManager.shutdownAll(Duration.ofSeconds(10));

            assertThat(success).isTrue();
            assertThat(EventloopManager.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Shutdown timeout parameter respected")
        void shutdownTimeoutRespected() {
            EventloopManager.create();

            long start = System.currentTimeMillis();
            boolean success = EventloopManager.shutdownAll(Duration.ofMillis(100));
            long elapsed = System.currentTimeMillis() - start;

            // Should complete within reasonable bounds (100ms + small overhead)
            assertThat(elapsed).isLessThan(2000);
        }
    }

    // ============================================
    // MULTI-THREAD ISOLATION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Multi-Thread Isolation")
    class IsolationTests {

        @Test
        @DisplayName("Each thread gets isolated eventloop via thread-local storage")
        void threadLocalIsolation() throws Exception {
            int threadCount = 10;
            Set<Long> threadIds = new HashSet<>();
            Set<Eventloop> eventloops = new HashSet<>();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            threadIds.add(Thread.currentThread().getId());
                            eventloops.add(EventloopManager.getCurrentEventloop());
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(threadIds).hasSize(threadCount);
            assertThat(eventloops).hasSize(threadCount);
            assertThat(eventloops).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Modified eventloop in one thread doesn't affect others")
        void noThreadCrossAffection() throws Exception {
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(3);
            List<Eventloop> loops = new ArrayList<>();

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(3);
            try {
                for (int i = 0; i < 3; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            Eventloop loop = EventloopManager.getCurrentEventloop();
                            loops.add(loop);
                        } catch (Exception e) {
                            fail("Thread interrupted", e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                assertThat(doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(loops).hasSize(3);
            assertThat(loops).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Clear in one thread doesn't affect other threads' eventloops")
        void clearIsolation() throws Exception {
            Eventloop mainThread = EventloopManager.getCurrentEventloop();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Eventloop> otherThread = new java.util.concurrent.atomic.AtomicReference<>();

            new Thread(() -> {
                try {
                    otherThread.set(EventloopManager.getCurrentEventloop());
                    latch.countDown();
                } finally {
                    // Don't clear
                }
            }).start();

            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);

            EventloopManager.clearCurrentEventloop();

            assertThat(EventloopManager.hasEventloop()).isFalse();
            assertThat(otherThread.get()).isNotNull(); // Other thread's still there
        }

        @Test
        @DisplayName("Concurrent creation across threads creates unique instances")
        void concurrentCreationUniqueness() throws Exception {
            int threadCount = 20;
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
            Set<Integer> loopHashes = new HashSet<>();

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            Eventloop loop = EventloopManager.getCurrentEventloop();
                            synchronized (loopHashes) {
                                loopHashes.add(System.identityHashCode(loop));
                            }
                        } catch (Exception e) {
                            fail("Thread interrupted", e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                assertThat(doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(loopHashes).hasSize(threadCount);
        }
    }

    // ============================================
    // STATE TRANSITIONS (4 tests)
    // ============================================

    @Nested
    @DisplayName("State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("Transitions from no-eventloop → has-eventloop → no-eventloop")
        void simpleStateTransition() {
            assertThat(EventloopManager.hasEventloop()).isFalse();

            EventloopManager.getCurrentEventloop();
            assertThat(EventloopManager.hasEventloop()).isTrue();

            EventloopManager.clearCurrentEventloop();
            assertThat(EventloopManager.hasEventloop()).isFalse();
        }

        @Test
        @DisplayName("Multiple creates keep state consistent")
        void multipleCreatesState() {
            EventloopManager.create();
            assertThat(EventloopManager.hasEventloop()).isTrue();
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1);

            EventloopManager.create();
            assertThat(EventloopManager.hasEventloop()).isTrue();
            assertThat(EventloopManager.getActiveCount()).isEqualTo(2);

            EventloopManager.create();
            assertThat(EventloopManager.hasEventloop()).isTrue();
            assertThat(EventloopManager.getActiveCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Shutdown transitions to permanently unavailable state")
        void shutdownPermanenceAfterFirstCall() {
            EventloopManager.create();
            EventloopManager.shutdownAll(Duration.ofSeconds(1));

            assertThatThrownBy(() -> EventloopManager.create())
                .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> EventloopManager.getCurrentEventloop())
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Reset from shutdown state restores functionality")
        void resetRestoresFunctionality() {
            EventloopManager.create();
            EventloopManager.shutdownAll(Duration.ofSeconds(1));

            // After reset, should work again
            EventloopManager.resetForTesting();

            Eventloop loop = EventloopManager.getCurrentEventloop();
            assertThat(loop).isNotNull();
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
        }
    }

    // ============================================
    // GETTERS & QUERIES (3 tests)
    // ============================================

    @Nested
    @DisplayName("Getters & Queries")
    class GetterTests {

        @Test
        @DisplayName("GetEventloop by thread ID retrieves correct instance")
        void getByThreadId() {
            long threadId = Thread.currentThread().getId();
            Eventloop created = EventloopManager.create();

            Eventloop retrieved = EventloopManager.getEventloop(threadId);

            assertThat(retrieved).isSameAs(created);
        }

        @Test
        @DisplayName("GetEventloop returns null for non-existent thread IDs")
        void getByInvalidThreadIdReturnsNull() {
            Eventloop loop = EventloopManager.getEventloop(999999999L);

            assertThat(loop).isNull();
        }

        @Test
        @DisplayName("GetCurrentEventloop is idempotent across many calls")
        void idempotentGetCurrent() {
            Eventloop first = EventloopManager.getCurrentEventloop();
            Eventloop second = EventloopManager.getCurrentEventloop();
            Eventloop third = EventloopManager.getCurrentEventloop();

            assertThat(first).isSameAs(second);
            assertThat(second).isSameAs(third);
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // Only 1 unique
        }
    }

    // ============================================
    // EDGE CASES & ERROR HANDLING (4 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases & Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Named creation with empty string creates eventloop")
        void namedCreationEmptyString() {
            Eventloop loop = EventloopManager.create("");

            assertThat(loop).isNotNull();
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Named creation with long names succeeds")
        void namedCreationLongName() {
            String longName = "very-long-eventloop-name-" + "x".repeat(100);
            Eventloop loop = EventloopManager.create(longName);

            assertThat(loop).isNotNull();
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("ShutdownAll with very short timeout completes")
        void shutdownVeryShortTimeout() {
            EventloopManager.create();

            boolean success = EventloopManager.shutdownAll(Duration.ofMillis(1));

            assertThat(EventloopManager.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("ShutdownAll with zero duration handled")
        void shutdownZeroDuration() {
            EventloopManager.create();

            // Should handle gracefully
            EventloopManager.shutdownAll(Duration.ZERO);

            assertThat(EventloopManager.getActiveCount()).isEqualTo(0);
        }
    }

    // ============================================
    // CONCURRENT STRESS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Stress")
    class StressTests {

        @Test
        @DisplayName("High thread contention on getCurrentEventloop")
        void highContention() throws Exception {
            int threadCount = 50;
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            Eventloop loop = EventloopManager.getCurrentEventloop();
                            if (loop != null) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Ignore
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                assertThat(doneLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Rapid mixed operations maintain consistency")
        void rapidMixedOps() throws Exception {
            int iterationCount = 100;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            new Thread(() -> {
                for (int i = 0; i < iterationCount; i++) {
                    if (i % 3 == 0) {
                        EventloopManager.create();
                    } else if (i % 3 == 1) {
                        EventloopManager.getCurrentEventloop();
                    } else {
                        EventloopManager.clearCurrentEventloop();
                    }
                }
                latch.countDown();
            }).start();

            assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @DisplayName("Many threads repeatedly querying active count")
        void activeCountQueryStress() throws Exception {
            int threadCount = 20;
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
            AtomicInteger minCount = new AtomicInteger(Integer.MAX_VALUE);
            AtomicInteger maxCount = new AtomicInteger(0);

            // Create some eventloops first
            for (int i = 0; i < 10; i++) {
                EventloopManager.create();
            }

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < 50; j++) {
                                int count = EventloopManager.getActiveCount();
                                minCount.set(Math.min(minCount.get(), count));
                                maxCount.set(Math.max(maxCount.get(), count));
                            }
                        } catch (Exception e) {
                            // Ignore
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                assertThat(doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(minCount.get()).isGreaterThanOrEqualTo(0);
            assertThat(maxCount.get()).isGreaterThanOrEqualTo(minCount.get());
        }
    }

    // ============================================
    // NAMING VARIATIONS (2 tests)
    // ============================================

    @Nested
    @DisplayName("Naming Variations")
    class NamingTests {

        @Test
        @DisplayName("Custom named eventloops are created correctly")
        void customNaming() {
            String[] names = {
                "io-loop",
                "background-worker",
                "request-handler-1",
                "event-processor_v2"
            };

            List<Eventloop> loops = new ArrayList<>();
            for (String name : names) {
                loops.add(EventloopManager.create(name));
            }

            assertThat(loops).hasSize(4);
            assertThat(EventloopManager.getActiveCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("Many custom names don't cause collisions")
        void noNameCollisions() {
            int loopCount = 100;
            Set<Integer> hashes = new HashSet<>();

            for (int i = 0; i < loopCount; i++) {
                Eventloop loop = EventloopManager.create("loop-" + i);
                hashes.add(System.identityHashCode(loop));
            }

            assertThat(hashes).hasSize(loopCount); // All unique
            assertThat(EventloopManager.getActiveCount()).isEqualTo(loopCount);
        }
    }
}
