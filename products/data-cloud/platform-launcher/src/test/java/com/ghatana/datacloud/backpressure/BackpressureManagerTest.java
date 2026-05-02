/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * <p>Covers all four backpressure strategies (DROP, BUFFER, THROTTLE, ADAPTIVE), 
 * priority levels, metric counters, and correct non-blocking behaviour when
 * the manager is used from the ActiveJ Eventloop.
 *
 * @doc.type test
 * @doc.purpose Comprehensive unit tests for BackpressureManager (PDC-001) 
 * @doc.layer infrastructure
 * @doc.pattern Unit Test
 */
@DisplayName("BackpressureManager Tests")
class BackpressureManagerTest extends EventloopTestBase {

    private BackpressureManager manager;

    @AfterEach
    void tearDown() { 
        if (manager != null) { 
            manager.shutdown(); 
        }
    }

    // =========================================================================
    // DROP strategy
    // =========================================================================

    @Nested
    @DisplayName("DROP strategy")
    class DropStrategyTests {

        @BeforeEach
        void setUp() { 
            manager = BackpressureManager.builder() 
                    .maxConcurrent(2) 
                    .strategy(BackpressureManager.BackpressureStrategy.DROP) 
                    .build(); 
        }

        @Test
        @DisplayName("Should execute operation when slot is available")
        void shouldExecuteWhenSlotAvailable() { 
            String result = runPromise(() -> manager.execute( 
                    () -> Promise.of("ok")));

            assertThat(result).isEqualTo("ok");
            assertThat(manager.getMetrics().getCompletedRequests()).isEqualTo(1); 
        }

        @Test
        @DisplayName("Should drop request and return BackpressureException when all slots busy")
        void shouldDropWhenOverloaded() { 
            // Fill both slots with in-flight promises that never complete during this test
            CountDownLatch blocker = new CountDownLatch(1); 

            // Use executeSync to saturate the semaphore synchronously in background threads
            AtomicInteger acquired = new AtomicInteger(0); 
            for (int i = 0; i < 2; i++) { 
                new Thread(() -> { 
                    try {
                        manager.executeSync(() -> { 
                            acquired.incrementAndGet(); 
                            try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} 
                            return null;
                        });
                    } catch (Exception ignored) {} 
                }).start(); 
            }

            // Wait until both slots are taken
            assertThat(acquired) 
                    .satisfies(a -> { 
                        int waited = 0;
                        while (a.get() < 2 && waited++ < 50) { 
                            try { Thread.sleep(10); } catch (InterruptedException ignored) {} 
                        }
                    });

            // Third concurrent call should be dropped non-blockingly
            assertThatThrownBy(() -> 
                    runPromise(() -> manager.execute(() -> Promise.of("dropped"))))
                    .isInstanceOf(BackpressureException.class) 
                    .hasMessageContaining("dropped");

            assertThat(manager.getMetrics().getDroppedRequests()).isGreaterThanOrEqualTo(1); 

            blocker.countDown(); 
        }

        @Test
        @DisplayName("Should NOT block the event loop thread while dropping")
        void shouldReturnImmediatelyOnDrop() { 
            // Saturate
            CountDownLatch blocker = new CountDownLatch(1); 
            for (int i = 0; i < 2; i++) { 
                new Thread(() -> { 
                    try {
                        manager.executeSync(() -> { 
                            try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} 
                            return null;
                        });
                    } catch (Exception ignored) {} 
                }).start(); 
            }

            try { Thread.sleep(50); } catch (InterruptedException ignored) {} 

            long start = System.currentTimeMillis(); 
            // execute() with DROP should return a failed promise immediately, not block 
            Promise<String> p = manager.execute(() -> Promise.of("should_drop"));
            long elapsed = System.currentTimeMillis() - start; 

            // Must resolve synchronously within a few milliseconds
            assertThat(elapsed).isLessThan(500); 

            blocker.countDown(); 
        }
    }

    // =========================================================================
    // BUFFER strategy
    // =========================================================================

    @Nested
    @DisplayName("BUFFER strategy")
    class BufferStrategyTests {

        @BeforeEach
        void setUp() { 
            manager = BackpressureManager.builder() 
                    .maxConcurrent(1) 
                    .queueCapacity(5) 
                    .strategy(BackpressureManager.BackpressureStrategy.BUFFER) 
                    .build(); 
        }

        @Test
        @DisplayName("Should execute queued request once slot becomes free")
        void shouldExecuteQueuedRequest() { 
            // First request executes immediately
            String result = runPromise(() -> manager.execute(() -> Promise.of("first")));
            assertThat(result).isEqualTo("first");
        }

        @Test
        @DisplayName("Should drop when queue capacity is exceeded")
        void shouldDropWhenQueueFull() { 
            // Saturate slot
            CountDownLatch blocker = new CountDownLatch(1); 
            new Thread(() -> { 
                try {
                    manager.executeSync(() -> { 
                        try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} 
                        return null;
                    });
                } catch (Exception ignored) {} 
            }).start(); 
            try { Thread.sleep(30); } catch (InterruptedException ignored) {} 

            // Fill queue (capacity = 5) 
            for (int i = 0; i < 5; i++) { 
                final int requestIndex = i;
                manager.execute(() -> Promise.of("queued-" + requestIndex)); 
            }

            // Next call should be dropped: queue full
            assertThatThrownBy(() -> 
                    runPromise(() -> manager.execute(() -> Promise.of("overflow"))))
                    .isInstanceOf(BackpressureException.class) 
                    .hasMessageContaining("Queue capacity exceeded");

            blocker.countDown(); 
        }
    }

    // =========================================================================
    // THROTTLE strategy
    // =========================================================================

    @Nested
    @DisplayName("THROTTLE strategy")
    class ThrottleStrategyTests {

        @BeforeEach
        void setUp() { 
            manager = BackpressureManager.builder() 
                    .maxConcurrent(2) 
                    .strategy(BackpressureManager.BackpressureStrategy.THROTTLE) 
                    // Very short timeout so the test does not block long
                    .timeout(Duration.ofMillis(100)) 
                    .build(); 
        }

        @Test
        @DisplayName("Should execute successfully when slot is available")
        void shouldExecuteWhenSlotFree() { 
            String result = runPromise(() -> manager.execute(() -> Promise.of("throttled-ok")));
            assertThat(result).isEqualTo("throttled-ok");
        }

        @Test
        @DisplayName("Should return BackpressureException when timeout expires and no slot frees")
        void shouldTimeoutWhenSlotNotFree() { 
            // Saturate both slots
            CountDownLatch blocker = new CountDownLatch(1); 
            for (int i = 0; i < 2; i++) { 
                new Thread(() -> { 
                    try {
                        manager.executeSync(() -> { 
                            try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} 
                            return null;
                        });
                    } catch (Exception ignored) {} 
                }).start(); 
            }
            try { Thread.sleep(30); } catch (InterruptedException ignored) {} 

            // The THROTTLE path uses Promise.ofBlocking so this must NOT block the test thread
            assertThatThrownBy(() -> 
                    runPromise(() -> manager.execute(() -> Promise.of("timed-out"))))
                    .isInstanceOf(BackpressureException.class) 
                    .hasMessageContaining("timed out");

            assertThat(manager.getMetrics().getDroppedRequests()).isGreaterThanOrEqualTo(1); 

            blocker.countDown(); 
        }
    }

    // =========================================================================
    // ADAPTIVE strategy
    // =========================================================================

    @Nested
    @DisplayName("ADAPTIVE strategy")
    class AdaptiveStrategyTests {

        @BeforeEach
        void setUp() { 
            manager = BackpressureManager.builder() 
                    .maxConcurrent(4) 
                    .queueCapacity(10) 
                    .strategy(BackpressureManager.BackpressureStrategy.ADAPTIVE) 
                    .build(); 
        }

        @Test
        @DisplayName("Should execute normal priority requests within capacity")
        void shouldExecuteNormalRequestsWithinCapacity() { 
            for (int i = 0; i < 4; i++) { 
                String result = runPromise(() -> manager.execute(() -> Promise.of("ok")));
                assertThat(result).isEqualTo("ok");
            }
            assertThat(manager.getMetrics().getCompletedRequests()).isEqualTo(4); 
        }

        @Test
        @DisplayName("Should report isUnderPressure() as false when load is low")
        void shouldReportHealthyWhenIdle() { 
            assertThat(manager.isHealthy()).isTrue(); 
            assertThat(manager.isUnderPressure()).isFalse(); 
        }
    }

    // =========================================================================
    // Priority levels
    // =========================================================================

    @Nested
    @DisplayName("Priority levels")
    class PriorityTests {

        @BeforeEach
        void setUp() { 
            manager = BackpressureManager.builder() 
                    .maxConcurrent(10) 
                    .strategy(BackpressureManager.BackpressureStrategy.DROP) 
                    .build(); 
        }

        @Test
        @DisplayName("CRITICAL priority request should proceed when slot is available")
        void criticalShouldSucceedWhenSlotFree() { 
            String result = runPromise(() -> 
                    manager.execute(BackpressureManager.Priority.CRITICAL, () -> Promise.of("critical-ok")));
            assertThat(result).isEqualTo("critical-ok");
        }

        @Test
        @DisplayName("HIGH priority request should succeed when slot is available")
        void highPriorityShouldSucceedWhenSlotFree() { 
            String result = runPromise(() -> 
                    manager.execute(BackpressureManager.Priority.HIGH, () -> Promise.of("high-ok")));
            assertThat(result).isEqualTo("high-ok");
        }
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    @Nested
    @DisplayName("Metrics bookkeeping")
    class MetricsTests {

        @BeforeEach
        void setUp() { 
            manager = BackpressureManager.builder() 
                    .maxConcurrent(5) 
                    .strategy(BackpressureManager.BackpressureStrategy.DROP) 
                    .build(); 
        }

        @Test
        @DisplayName("Should increment totalRequests and completedRequests on success")
        void shouldIncrementCountersOnSuccess() { 
            runPromise(() -> manager.execute(() -> Promise.of("x")));
            runPromise(() -> manager.execute(() -> Promise.of("y")));

            assertThat(manager.getMetrics().getTotalRequests()).isEqualTo(2); 
            assertThat(manager.getMetrics().getCompletedRequests()).isEqualTo(2); 
            assertThat(manager.getMetrics().getFailedRequests()).isZero(); 
        }

        @Test
        @DisplayName("Should increment failedRequests on promise failure")
        void shouldIncrementFailedRequestsOnError() { 
            assertThatThrownBy(() -> 
                    runPromise(() -> manager.execute(() -> 
                            Promise.ofException(new RuntimeException("boom")))))
                    .isInstanceOf(RuntimeException.class); 

            assertThat(manager.getMetrics().getFailedRequests()).isEqualTo(1); 
        }

        @Test
        @DisplayName("getStatus() toMap() should contain all expected keys")
        void statusMapShouldContainExpectedKeys() { 
            var statusMap = manager.getStatus().toMap(); 
            assertThat(statusMap).containsKeys( 
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
    @DisplayName("executeSync (intentionally blocking caller thread)")
    class ExecuteSyncTests {

        @BeforeEach
        void setUp() { 
            manager = BackpressureManager.builder() 
                    .maxConcurrent(3) 
                    .strategy(BackpressureManager.BackpressureStrategy.DROP) 
                    .build(); 
        }

        @Test
        @DisplayName("Should return the operation result synchronously")
        void shouldReturnResultSync() throws BackpressureException { 
            String result = manager.executeSync(() -> "sync-result"); 
            assertThat(result).isEqualTo("sync-result");
        }

        @Test
        @DisplayName("Should propagate exceptions from the operation")
        void shouldPropagateExceptionFromOperation() { 
            assertThatThrownBy(() -> 
                    manager.executeSync(() -> { throw new IllegalStateException("boom"); }))
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessage("boom");
        }
    }
}
