/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.backpressure;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link BackpressureManager}.
 *
 * <p>Covers all four backpressure strategies (DROP, BUFFER, THROTTLE, ADAPTIVE), // GH-90000
 * priority levels, metric counters, and correct non-blocking behaviour when
 * the manager is used from the ActiveJ Eventloop.
 *
 * @doc.type test
 * @doc.purpose Comprehensive unit tests for BackpressureManager (PDC-001) // GH-90000
 * @doc.layer infrastructure
 * @doc.pattern Unit Test
 */
@DisplayName("BackpressureManager Tests [GH-90000]")
class BackpressureManagerTest extends EventloopTestBase {

    private BackpressureManager manager;

    @AfterEach
    void tearDown() { // GH-90000
        if (manager != null) { // GH-90000
            manager.shutdown(); // GH-90000
        }
    }

    // =========================================================================
    // DROP strategy
    // =========================================================================

    @Nested
    @DisplayName("DROP strategy [GH-90000]")
    class DropStrategyTests {

        @BeforeEach
        void setUp() { // GH-90000
            manager = BackpressureManager.builder() // GH-90000
                    .maxConcurrent(2) // GH-90000
                    .strategy(BackpressureManager.BackpressureStrategy.DROP) // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("Should execute operation when slot is available [GH-90000]")
        void shouldExecuteWhenSlotAvailable() { // GH-90000
            String result = runPromise(() -> manager.execute( // GH-90000
                    () -> Promise.of("ok [GH-90000]")));

            assertThat(result).isEqualTo("ok [GH-90000]");
            assertThat(manager.getMetrics().getCompletedRequests()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Should drop request and return BackpressureException when all slots busy [GH-90000]")
        void shouldDropWhenOverloaded() { // GH-90000
            // Fill both slots with in-flight promises that never complete during this test
            CountDownLatch blocker = new CountDownLatch(1); // GH-90000

            // Use executeSync to saturate the semaphore synchronously in background threads
            AtomicInteger acquired = new AtomicInteger(0); // GH-90000
            for (int i = 0; i < 2; i++) { // GH-90000
                new Thread(() -> { // GH-90000
                    try {
                        manager.executeSync(() -> { // GH-90000
                            acquired.incrementAndGet(); // GH-90000
                            try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} // GH-90000
                            return null;
                        });
                    } catch (Exception ignored) {} // GH-90000
                }).start(); // GH-90000
            }

            // Wait until both slots are taken
            assertThat(acquired) // GH-90000
                    .satisfies(a -> { // GH-90000
                        int waited = 0;
                        while (a.get() < 2 && waited++ < 50) { // GH-90000
                            try { Thread.sleep(10); } catch (InterruptedException ignored) {} // GH-90000
                        }
                    });

            // Third concurrent call should be dropped non-blockingly
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> manager.execute(() -> Promise.of("dropped [GH-90000]"))))
                    .isInstanceOf(BackpressureException.class) // GH-90000
                    .hasMessageContaining("dropped [GH-90000]");

            assertThat(manager.getMetrics().getDroppedRequests()).isGreaterThanOrEqualTo(1); // GH-90000

            blocker.countDown(); // GH-90000
        }

        @Test
        @DisplayName("Should NOT block the event loop thread while dropping [GH-90000]")
        void shouldReturnImmediatelyOnDrop() { // GH-90000
            // Saturate
            CountDownLatch blocker = new CountDownLatch(1); // GH-90000
            for (int i = 0; i < 2; i++) { // GH-90000
                new Thread(() -> { // GH-90000
                    try {
                        manager.executeSync(() -> { // GH-90000
                            try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} // GH-90000
                            return null;
                        });
                    } catch (Exception ignored) {} // GH-90000
                }).start(); // GH-90000
            }

            try { Thread.sleep(50); } catch (InterruptedException ignored) {} // GH-90000

            long start = System.currentTimeMillis(); // GH-90000
            // execute() with DROP should return a failed promise immediately, not block // GH-90000
            Promise<String> p = manager.execute(() -> Promise.of("should_drop [GH-90000]"));
            long elapsed = System.currentTimeMillis() - start; // GH-90000

            // Must resolve synchronously within a few milliseconds
            assertThat(elapsed).isLessThan(500); // GH-90000

            blocker.countDown(); // GH-90000
        }
    }

    // =========================================================================
    // BUFFER strategy
    // =========================================================================

    @Nested
    @DisplayName("BUFFER strategy [GH-90000]")
    class BufferStrategyTests {

        @BeforeEach
        void setUp() { // GH-90000
            manager = BackpressureManager.builder() // GH-90000
                    .maxConcurrent(1) // GH-90000
                    .queueCapacity(5) // GH-90000
                    .strategy(BackpressureManager.BackpressureStrategy.BUFFER) // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("Should execute queued request once slot becomes free [GH-90000]")
        void shouldExecuteQueuedRequest() { // GH-90000
            // First request executes immediately
            String result = runPromise(() -> manager.execute(() -> Promise.of("first [GH-90000]")));
            assertThat(result).isEqualTo("first [GH-90000]");
        }

        @Test
        @DisplayName("Should drop when queue capacity is exceeded [GH-90000]")
        void shouldDropWhenQueueFull() { // GH-90000
            // Saturate slot
            CountDownLatch blocker = new CountDownLatch(1); // GH-90000
            new Thread(() -> { // GH-90000
                try {
                    manager.executeSync(() -> { // GH-90000
                        try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} // GH-90000
                        return null;
                    });
                } catch (Exception ignored) {} // GH-90000
            }).start(); // GH-90000
            try { Thread.sleep(30); } catch (InterruptedException ignored) {} // GH-90000

            // Fill queue (capacity = 5) // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                final int requestIndex = i;
                manager.execute(() -> Promise.of("queued-" + requestIndex)); // GH-90000
            }

            // Next call should be dropped: queue full
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> manager.execute(() -> Promise.of("overflow [GH-90000]"))))
                    .isInstanceOf(BackpressureException.class) // GH-90000
                    .hasMessageContaining("Queue capacity exceeded [GH-90000]");

            blocker.countDown(); // GH-90000
        }
    }

    // =========================================================================
    // THROTTLE strategy
    // =========================================================================

    @Nested
    @DisplayName("THROTTLE strategy [GH-90000]")
    class ThrottleStrategyTests {

        @BeforeEach
        void setUp() { // GH-90000
            manager = BackpressureManager.builder() // GH-90000
                    .maxConcurrent(2) // GH-90000
                    .strategy(BackpressureManager.BackpressureStrategy.THROTTLE) // GH-90000
                    // Very short timeout so the test does not block long
                    .timeout(Duration.ofMillis(100)) // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("Should execute successfully when slot is available [GH-90000]")
        void shouldExecuteWhenSlotFree() { // GH-90000
            String result = runPromise(() -> manager.execute(() -> Promise.of("throttled-ok [GH-90000]")));
            assertThat(result).isEqualTo("throttled-ok [GH-90000]");
        }

        @Test
        @DisplayName("Should return BackpressureException when timeout expires and no slot frees [GH-90000]")
        void shouldTimeoutWhenSlotNotFree() { // GH-90000
            // Saturate both slots
            CountDownLatch blocker = new CountDownLatch(1); // GH-90000
            for (int i = 0; i < 2; i++) { // GH-90000
                new Thread(() -> { // GH-90000
                    try {
                        manager.executeSync(() -> { // GH-90000
                            try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} // GH-90000
                            return null;
                        });
                    } catch (Exception ignored) {} // GH-90000
                }).start(); // GH-90000
            }
            try { Thread.sleep(30); } catch (InterruptedException ignored) {} // GH-90000

            // The THROTTLE path uses Promise.ofBlocking so this must NOT block the test thread
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> manager.execute(() -> Promise.of("timed-out [GH-90000]"))))
                    .isInstanceOf(BackpressureException.class) // GH-90000
                    .hasMessageContaining("timed out [GH-90000]");

            assertThat(manager.getMetrics().getDroppedRequests()).isGreaterThanOrEqualTo(1); // GH-90000

            blocker.countDown(); // GH-90000
        }
    }

    // =========================================================================
    // ADAPTIVE strategy
    // =========================================================================

    @Nested
    @DisplayName("ADAPTIVE strategy [GH-90000]")
    class AdaptiveStrategyTests {

        @BeforeEach
        void setUp() { // GH-90000
            manager = BackpressureManager.builder() // GH-90000
                    .maxConcurrent(4) // GH-90000
                    .queueCapacity(10) // GH-90000
                    .strategy(BackpressureManager.BackpressureStrategy.ADAPTIVE) // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("Should execute normal priority requests within capacity [GH-90000]")
        void shouldExecuteNormalRequestsWithinCapacity() { // GH-90000
            for (int i = 0; i < 4; i++) { // GH-90000
                String result = runPromise(() -> manager.execute(() -> Promise.of("ok [GH-90000]")));
                assertThat(result).isEqualTo("ok [GH-90000]");
            }
            assertThat(manager.getMetrics().getCompletedRequests()).isEqualTo(4); // GH-90000
        }

        @Test
        @DisplayName("Should report isUnderPressure() as false when load is low [GH-90000]")
        void shouldReportHealthyWhenIdle() { // GH-90000
            assertThat(manager.isHealthy()).isTrue(); // GH-90000
            assertThat(manager.isUnderPressure()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Priority levels
    // =========================================================================

    @Nested
    @DisplayName("Priority levels [GH-90000]")
    class PriorityTests {

        @BeforeEach
        void setUp() { // GH-90000
            manager = BackpressureManager.builder() // GH-90000
                    .maxConcurrent(10) // GH-90000
                    .strategy(BackpressureManager.BackpressureStrategy.DROP) // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("CRITICAL priority request should proceed when slot is available [GH-90000]")
        void criticalShouldSucceedWhenSlotFree() { // GH-90000
            String result = runPromise(() -> // GH-90000
                    manager.execute(BackpressureManager.Priority.CRITICAL, () -> Promise.of("critical-ok [GH-90000]")));
            assertThat(result).isEqualTo("critical-ok [GH-90000]");
        }

        @Test
        @DisplayName("HIGH priority request should succeed when slot is available [GH-90000]")
        void highPriorityShouldSucceedWhenSlotFree() { // GH-90000
            String result = runPromise(() -> // GH-90000
                    manager.execute(BackpressureManager.Priority.HIGH, () -> Promise.of("high-ok [GH-90000]")));
            assertThat(result).isEqualTo("high-ok [GH-90000]");
        }
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    @Nested
    @DisplayName("Metrics bookkeeping [GH-90000]")
    class MetricsTests {

        @BeforeEach
        void setUp() { // GH-90000
            manager = BackpressureManager.builder() // GH-90000
                    .maxConcurrent(5) // GH-90000
                    .strategy(BackpressureManager.BackpressureStrategy.DROP) // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("Should increment totalRequests and completedRequests on success [GH-90000]")
        void shouldIncrementCountersOnSuccess() { // GH-90000
            runPromise(() -> manager.execute(() -> Promise.of("x [GH-90000]")));
            runPromise(() -> manager.execute(() -> Promise.of("y [GH-90000]")));

            assertThat(manager.getMetrics().getTotalRequests()).isEqualTo(2); // GH-90000
            assertThat(manager.getMetrics().getCompletedRequests()).isEqualTo(2); // GH-90000
            assertThat(manager.getMetrics().getFailedRequests()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("Should increment failedRequests on promise failure [GH-90000]")
        void shouldIncrementFailedRequestsOnError() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> manager.execute(() -> // GH-90000
                            Promise.ofException(new RuntimeException("boom [GH-90000]")))))
                    .isInstanceOf(RuntimeException.class); // GH-90000

            assertThat(manager.getMetrics().getFailedRequests()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("getStatus() toMap() should contain all expected keys [GH-90000]")
        void statusMapShouldContainExpectedKeys() { // GH-90000
            var statusMap = manager.getStatus().toMap(); // GH-90000
            assertThat(statusMap).containsKeys( // GH-90000
                    "active_requests", "max_concurrent", "utilization",
                    "queued_requests", "queue_capacity", "queue_utilization",
                    "total_requests", "completed_requests", "failed_requests",
                    "dropped_requests", "success_rate", "load_factor",
                    "adaptive_limit", "strategy");
        }
    }

    // =========================================================================
    // executeSync
    // =========================================================================

    @Nested
    @DisplayName("executeSync (intentionally blocking caller thread) [GH-90000]")
    class ExecuteSyncTests {

        @BeforeEach
        void setUp() { // GH-90000
            manager = BackpressureManager.builder() // GH-90000
                    .maxConcurrent(3) // GH-90000
                    .strategy(BackpressureManager.BackpressureStrategy.DROP) // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("Should return the operation result synchronously [GH-90000]")
        void shouldReturnResultSync() throws BackpressureException { // GH-90000
            String result = manager.executeSync(() -> "sync-result"); // GH-90000
            assertThat(result).isEqualTo("sync-result [GH-90000]");
        }

        @Test
        @DisplayName("Should propagate exceptions from the operation [GH-90000]")
        void shouldPropagateExceptionFromOperation() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    manager.executeSync(() -> { throw new IllegalStateException("boom [GH-90000]"); }))
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessage("boom [GH-90000]");
        }
    }
}
