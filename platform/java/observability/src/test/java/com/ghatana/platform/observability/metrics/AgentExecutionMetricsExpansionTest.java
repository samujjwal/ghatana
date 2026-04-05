package com.ghatana.platform.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: Agent execution metrics edge cases and concurrent scenarios.
 * Tests concurrent recording, edge cases, and metric consistency.
 *
 * @doc.type class
 * @doc.purpose Agent execution metrics edge cases and concurrent scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentExecutionMetrics - Phase 3 Expansion")
class AgentExecutionMetricsExpansionTest {

    private MeterRegistry registry;
    private AgentExecutionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AgentExecutionMetrics("agent-p3", "tenant-test", "pipeline-test");
        metrics.bindTo(registry);
    }

    // ============================================
    // CONCURRENT EXECUTION TRACKING (1 test)
    // ============================================

    @Nested
    @DisplayName("Concurrent Execution Tracking")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent executions are counted correctly")
        void concurrentExecutionCounting() throws InterruptedException {
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                int index = i;
                new Thread(() -> {
                    try {
                        if (index % 2 == 0) {
                            metrics.recordExecution(System.currentTimeMillis() - 100, true);
                            successCount.incrementAndGet();
                        } else {
                            metrics.recordExecution(System.currentTimeMillis() - 50, false);
                            failureCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertThat(registry.find("agent.execution.success").counter().count())
                .isEqualTo((double) successCount.get());
            assertThat(registry.find("agent.execution.failure").counter().count())
                .isEqualTo((double) failureCount.get());
            assertThat(registry.find("agent.execution.duration").timer().count())
                .isEqualTo(threadCount);
        }
    }

    // ============================================
    // DURATION MEASUREMENT EDGE CASES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Duration Measurement Edge Cases")
    class DurationTests {

        @Test
        @DisplayName("Very fast execution (microsecond-level) is recorded")
        void veryFastExecution() {
            long startTime = System.nanoTime();
            // Simulate very fast execution
            metrics.recordExecution(System.currentTimeMillis() - 1, true);

            assertThat(registry.find("agent.execution.duration").timer().count()).isEqualTo(1L);
            // Duration should be positive (even if very small)
            assertThat(registry.find("agent.execution.duration").timer().totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                .isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Long running execution (minutes) is recorded correctly")
        void longRunningExecution() {
            // Simulate 5 minute execution
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
            metrics.recordExecution(fiveMinutesAgo, true);

            assertThat(registry.find("agent.execution.success").counter().count()).isEqualTo(1.0);
            assertThat(registry.find("agent.execution.duration").timer().count()).isEqualTo(1L);
        }
    }

    // ============================================
    // METRIC CONSISTENCY (1 test)
    // ============================================

    @Nested
    @DisplayName("Metric Consistency")
    class ConsistencyTests {

        @Test
        @DisplayName("Success + Failure count equals total execution count")
        void successFailureConsistency() {
            // Record mixed outcomes
            metrics.recordExecution(System.currentTimeMillis() - 100, true);
            metrics.recordExecution(System.currentTimeMillis() - 80, true);
            metrics.recordExecution(System.currentTimeMillis() - 60, false);
            metrics.recordExecution(System.currentTimeMillis() - 40, false);
            metrics.recordExecution(System.currentTimeMillis() - 20, true);

            double successCount = registry.find("agent.execution.success").counter().count();
            double failureCount = registry.find("agent.execution.failure").counter().count();
            long totalDuration = registry.find("agent.execution.duration").timer().count();

            // Success + Failure should equal total recorded
            assertThat(successCount + failureCount).isEqualTo((double) totalDuration);
            assertThat(successCount).isEqualTo(3.0);
            assertThat(failureCount).isEqualTo(2.0);
        }
    }
}
