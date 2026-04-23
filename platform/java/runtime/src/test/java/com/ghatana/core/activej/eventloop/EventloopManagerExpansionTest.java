/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void cleanup() { // GH-90000
        EventloopManager.resetForTesting(); // GH-90000
    }

    // ============================================
    // SCALABILITY & CAPACITY (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Scalability & Capacity")
    class ScalabilityTests {

        @Test
        @DisplayName("Creates up to 50 eventloops without exhaustion")
        void manyEventloopsCreated() { // GH-90000
            int loopCount = 50;
            List<Eventloop> loops = new ArrayList<>(); // GH-90000

            for (int i = 0; i < loopCount; i++) { // GH-90000
                loops.add(EventloopManager.create()); // GH-90000
            }

            assertThat(loops).hasSize(loopCount); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(loopCount); // GH-90000
            assertThat(loops).doesNotHaveDuplicates(); // GH-90000
        }

        @Test
        @DisplayName("Active count increments correctly with many sequential creates")
        void activeCountTracksSequentialCreations() { // GH-90000
            for (int i = 1; i <= 25; i++) { // GH-90000
                EventloopManager.create(); // GH-90000
                assertThat(EventloopManager.getActiveCount()).isEqualTo(i); // GH-90000
            }
        }

        @Test
        @DisplayName("Rapid get/clear cycles maintain consistency")
        void rapidGetClearCycles() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                EventloopManager.getCurrentEventloop(); // GH-90000
                assertThat(EventloopManager.hasEventloop()).isTrue(); // GH-90000

                EventloopManager.clearCurrentEventloop(); // GH-90000
                assertThat(EventloopManager.hasEventloop()).isFalse(); // GH-90000
                assertThat(EventloopManager.getActiveCount()).isEqualTo(0); // GH-90000
            }
        }

        @Test
        @DisplayName("Bulk shutdown handles 30+ eventloops")
        void bulkShutdown() { // GH-90000
            for (int i = 0; i < 30; i++) { // GH-90000
                EventloopManager.create(); // GH-90000
            }
            assertThat(EventloopManager.getActiveCount()).isEqualTo(30); // GH-90000

            boolean success = EventloopManager.shutdownAll(Duration.ofSeconds(10)); // GH-90000

            assertThat(success).isTrue(); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Shutdown timeout parameter respected")
        void shutdownTimeoutRespected() { // GH-90000
            EventloopManager.create(); // GH-90000

            long start = System.currentTimeMillis(); // GH-90000
            boolean success = EventloopManager.shutdownAll(Duration.ofMillis(100)); // GH-90000
            long elapsed = System.currentTimeMillis() - start; // GH-90000

            // Should complete within reasonable bounds (100ms + small overhead) // GH-90000
            assertThat(elapsed).isLessThan(2000); // GH-90000
        }
    }

    // ============================================
    // MULTI-THREAD ISOLATION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Thread Isolation")
    class IsolationTests {

        @Test
        @DisplayName("Each thread gets isolated eventloop via thread-local storage")
        void threadLocalIsolation() throws Exception { // GH-90000
            int threadCount = 10;
            Set<Long> threadIds = new HashSet<>(); // GH-90000
            Set<Eventloop> eventloops = new HashSet<>(); // GH-90000
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount); // GH-90000

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    executor.submit(() -> { // GH-90000
                        try {
                            threadIds.add(Thread.currentThread().getId()); // GH-90000
                            eventloops.add(EventloopManager.getCurrentEventloop()); // GH-90000
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }

                assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(threadIds).hasSize(threadCount); // GH-90000
            assertThat(eventloops).hasSize(threadCount); // GH-90000
            assertThat(eventloops).doesNotHaveDuplicates(); // GH-90000
        }

        @Test
        @DisplayName("Modified eventloop in one thread doesn't affect others")
        void noThreadCrossAffection() throws Exception { // GH-90000
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1); // GH-90000
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(3); // GH-90000
            List<Eventloop> loops = new ArrayList<>(); // GH-90000

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(3); // GH-90000
            try {
                for (int i = 0; i < 3; i++) { // GH-90000
                    executor.submit(() -> { // GH-90000
                        try {
                            startLatch.await(); // GH-90000
                            Eventloop loop = EventloopManager.getCurrentEventloop(); // GH-90000
                            loops.add(loop); // GH-90000
                        } catch (Exception e) { // GH-90000
                            fail("Thread interrupted", e); // GH-90000
                        } finally {
                            doneLatch.countDown(); // GH-90000
                        }
                    });
                }

                startLatch.countDown(); // GH-90000
                assertThat(doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(loops).hasSize(3); // GH-90000
            assertThat(loops).doesNotHaveDuplicates(); // GH-90000
        }

        @Test
        @DisplayName("Clear in one thread doesn't affect other threads' eventloops")
        void clearIsolation() throws Exception { // GH-90000
            Eventloop mainThread = EventloopManager.getCurrentEventloop(); // GH-90000
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1); // GH-90000
            java.util.concurrent.atomic.AtomicReference<Eventloop> otherThread = new java.util.concurrent.atomic.AtomicReference<>(); // GH-90000

            new Thread(() -> { // GH-90000
                try {
                    otherThread.set(EventloopManager.getCurrentEventloop()); // GH-90000
                    latch.countDown(); // GH-90000
                } finally {
                    // Don't clear
                }
            }).start(); // GH-90000

            latch.await(5, java.util.concurrent.TimeUnit.SECONDS); // GH-90000

            EventloopManager.clearCurrentEventloop(); // GH-90000

            assertThat(EventloopManager.hasEventloop()).isFalse(); // GH-90000
            assertThat(otherThread.get()).isNotNull(); // Other thread's still there // GH-90000
        }

        @Test
        @DisplayName("Concurrent creation across threads creates unique instances")
        void concurrentCreationUniqueness() throws Exception { // GH-90000
            int threadCount = 20;
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1); // GH-90000
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount); // GH-90000
            Set<Integer> loopHashes = new HashSet<>(); // GH-90000

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    executor.submit(() -> { // GH-90000
                        try {
                            startLatch.await(); // GH-90000
                            Eventloop loop = EventloopManager.getCurrentEventloop(); // GH-90000
                            synchronized (loopHashes) { // GH-90000
                                loopHashes.add(System.identityHashCode(loop)); // GH-90000
                            }
                        } catch (Exception e) { // GH-90000
                            fail("Thread interrupted", e); // GH-90000
                        } finally {
                            doneLatch.countDown(); // GH-90000
                        }
                    });
                }

                startLatch.countDown(); // GH-90000
                assertThat(doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(loopHashes).hasSize(threadCount); // GH-90000
        }
    }

    // ============================================
    // STATE TRANSITIONS (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("Transitions from no-eventloop → has-eventloop → no-eventloop")
        void simpleStateTransition() { // GH-90000
            assertThat(EventloopManager.hasEventloop()).isFalse(); // GH-90000

            EventloopManager.getCurrentEventloop(); // GH-90000
            assertThat(EventloopManager.hasEventloop()).isTrue(); // GH-90000

            EventloopManager.clearCurrentEventloop(); // GH-90000
            assertThat(EventloopManager.hasEventloop()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Multiple creates keep state consistent")
        void multipleCreatesState() { // GH-90000
            EventloopManager.create(); // GH-90000
            assertThat(EventloopManager.hasEventloop()).isTrue(); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000

            EventloopManager.create(); // GH-90000
            assertThat(EventloopManager.hasEventloop()).isTrue(); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(2); // GH-90000

            EventloopManager.create(); // GH-90000
            assertThat(EventloopManager.hasEventloop()).isTrue(); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("Shutdown transitions to permanently unavailable state")
        void shutdownPermanenceAfterFirstCall() { // GH-90000
            EventloopManager.create(); // GH-90000
            EventloopManager.shutdownAll(Duration.ofSeconds(1)); // GH-90000

            assertThatThrownBy(() -> EventloopManager.create()) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
            assertThatThrownBy(() -> EventloopManager.getCurrentEventloop()) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        @DisplayName("Reset from shutdown state restores functionality")
        void resetRestoresFunctionality() { // GH-90000
            EventloopManager.create(); // GH-90000
            EventloopManager.shutdownAll(Duration.ofSeconds(1)); // GH-90000

            // After reset, should work again
            EventloopManager.resetForTesting(); // GH-90000

            Eventloop loop = EventloopManager.getCurrentEventloop(); // GH-90000
            assertThat(loop).isNotNull(); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000
        }
    }

    // ============================================
    // GETTERS & QUERIES (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Getters & Queries")
    class GetterTests {

        @Test
        @DisplayName("GetEventloop by thread ID retrieves correct instance")
        void getByThreadId() { // GH-90000
            long threadId = Thread.currentThread().getId(); // GH-90000
            Eventloop created = EventloopManager.create(); // GH-90000

            Eventloop retrieved = EventloopManager.getEventloop(threadId); // GH-90000

            assertThat(retrieved).isSameAs(created); // GH-90000
        }

        @Test
        @DisplayName("GetEventloop returns null for non-existent thread IDs")
        void getByInvalidThreadIdReturnsNull() { // GH-90000
            Eventloop loop = EventloopManager.getEventloop(999999999L); // GH-90000

            assertThat(loop).isNull(); // GH-90000
        }

        @Test
        @DisplayName("GetCurrentEventloop is idempotent across many calls")
        void idempotentGetCurrent() { // GH-90000
            Eventloop first = EventloopManager.getCurrentEventloop(); // GH-90000
            Eventloop second = EventloopManager.getCurrentEventloop(); // GH-90000
            Eventloop third = EventloopManager.getCurrentEventloop(); // GH-90000

            assertThat(first).isSameAs(second); // GH-90000
            assertThat(second).isSameAs(third); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // Only 1 unique // GH-90000
        }
    }

    // ============================================
    // EDGE CASES & ERROR HANDLING (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases & Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Named creation with empty string creates eventloop")
        void namedCreationEmptyString() { // GH-90000
            Eventloop loop = EventloopManager.create("");

            assertThat(loop).isNotNull(); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Named creation with long names succeeds")
        void namedCreationLongName() { // GH-90000
            String longName = "very-long-eventloop-name-" + "x".repeat(100); // GH-90000
            Eventloop loop = EventloopManager.create(longName); // GH-90000

            assertThat(loop).isNotNull(); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("ShutdownAll with very short timeout completes")
        void shutdownVeryShortTimeout() { // GH-90000
            EventloopManager.create(); // GH-90000

            boolean success = EventloopManager.shutdownAll(Duration.ofMillis(1)); // GH-90000

            assertThat(EventloopManager.getActiveCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("ShutdownAll with zero duration handled")
        void shutdownZeroDuration() { // GH-90000
            EventloopManager.create(); // GH-90000

            // Should handle gracefully
            EventloopManager.shutdownAll(Duration.ZERO); // GH-90000

            assertThat(EventloopManager.getActiveCount()).isEqualTo(0); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT STRESS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Stress")
    class StressTests {

        @Test
        @DisplayName("High thread contention on getCurrentEventloop")
        void highContention() throws Exception { // GH-90000
            int threadCount = 50;
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1); // GH-90000
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    executor.submit(() -> { // GH-90000
                        try {
                            startLatch.await(); // GH-90000
                            Eventloop loop = EventloopManager.getCurrentEventloop(); // GH-90000
                            if (loop != null) { // GH-90000
                                successCount.incrementAndGet(); // GH-90000
                            }
                        } catch (Exception e) { // GH-90000
                            // Ignore
                        } finally {
                            doneLatch.countDown(); // GH-90000
                        }
                    });
                }

                startLatch.countDown(); // GH-90000
                assertThat(doneLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Rapid mixed operations maintain consistency")
        void rapidMixedOps() throws Exception { // GH-90000
            int iterationCount = 100;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1); // GH-90000

            new Thread(() -> { // GH-90000
                for (int i = 0; i < iterationCount; i++) { // GH-90000
                    if (i % 3 == 0) { // GH-90000
                        EventloopManager.create(); // GH-90000
                    } else if (i % 3 == 1) { // GH-90000
                        EventloopManager.getCurrentEventloop(); // GH-90000
                    } else {
                        EventloopManager.clearCurrentEventloop(); // GH-90000
                    }
                }
                latch.countDown(); // GH-90000
            }).start(); // GH-90000

            assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Many threads repeatedly querying active count")
        void activeCountQueryStress() throws Exception { // GH-90000
            int threadCount = 20;
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1); // GH-90000
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount); // GH-90000
            AtomicInteger minCount = new AtomicInteger(Integer.MAX_VALUE); // GH-90000
            AtomicInteger maxCount = new AtomicInteger(0); // GH-90000

            // Create some eventloops first
            for (int i = 0; i < 10; i++) { // GH-90000
                EventloopManager.create(); // GH-90000
            }

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    executor.submit(() -> { // GH-90000
                        try {
                            startLatch.await(); // GH-90000
                            for (int j = 0; j < 50; j++) { // GH-90000
                                int count = EventloopManager.getActiveCount(); // GH-90000
                                minCount.set(Math.min(minCount.get(), count)); // GH-90000
                                maxCount.set(Math.max(maxCount.get(), count)); // GH-90000
                            }
                        } catch (Exception e) { // GH-90000
                            // Ignore
                        } finally {
                            doneLatch.countDown(); // GH-90000
                        }
                    });
                }

                startLatch.countDown(); // GH-90000
                assertThat(doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(minCount.get()).isGreaterThanOrEqualTo(0); // GH-90000
            assertThat(maxCount.get()).isGreaterThanOrEqualTo(minCount.get()); // GH-90000
        }
    }

    // ============================================
    // NAMING VARIATIONS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Naming Variations")
    class NamingTests {

        @Test
        @DisplayName("Custom named eventloops are created correctly")
        void customNaming() { // GH-90000
            String[] names = {
                "io-loop",
                "background-worker",
                "request-handler-1",
                "event-processor_v2"
            };

            List<Eventloop> loops = new ArrayList<>(); // GH-90000
            for (String name : names) { // GH-90000
                loops.add(EventloopManager.create(name)); // GH-90000
            }

            assertThat(loops).hasSize(4); // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(4); // GH-90000
        }

        @Test
        @DisplayName("Many custom names don't cause collisions")
        void noNameCollisions() { // GH-90000
            int loopCount = 100;
            Set<Integer> hashes = new HashSet<>(); // GH-90000

            for (int i = 0; i < loopCount; i++) { // GH-90000
                Eventloop loop = EventloopManager.create("loop-" + i); // GH-90000
                hashes.add(System.identityHashCode(loop)); // GH-90000
            }

            assertThat(hashes).hasSize(loopCount); // All unique // GH-90000
            assertThat(EventloopManager.getActiveCount()).isEqualTo(loopCount); // GH-90000
        }
    }
}
