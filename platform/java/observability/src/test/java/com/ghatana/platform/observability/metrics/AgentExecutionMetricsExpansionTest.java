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
@DisplayName("AgentExecutionMetrics - Phase 3 Expansion [GH-90000]")
class AgentExecutionMetricsExpansionTest {

    private MeterRegistry registry;
    private AgentExecutionMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        metrics = new AgentExecutionMetrics("agent-p3", "tenant-test", "pipeline-test"); // GH-90000
        metrics.bindTo(registry); // GH-90000
    }

    // ============================================
    // CONCURRENT EXECUTION TRACKING (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Execution Tracking [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent executions are counted correctly [GH-90000]")
        void concurrentExecutionCounting() throws InterruptedException { // GH-90000
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000
            AtomicInteger failureCount = new AtomicInteger(0); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                int index = i;
                new Thread(() -> { // GH-90000
                    try {
                        if (index % 2 == 0) { // GH-90000
                            metrics.recordExecution(System.currentTimeMillis() - 100, true); // GH-90000
                            successCount.incrementAndGet(); // GH-90000
                        } else {
                            metrics.recordExecution(System.currentTimeMillis() - 50, false); // GH-90000
                            failureCount.incrementAndGet(); // GH-90000
                        }
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000

            assertThat(registry.find("agent.execution.success [GH-90000]").counter().count())
                .isEqualTo((double) successCount.get()); // GH-90000
            assertThat(registry.find("agent.execution.failure [GH-90000]").counter().count())
                .isEqualTo((double) failureCount.get()); // GH-90000
            assertThat(registry.find("agent.execution.duration [GH-90000]").timer().count())
                .isEqualTo(threadCount); // GH-90000
        }
    }

    // ============================================
    // DURATION MEASUREMENT EDGE CASES (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Duration Measurement Edge Cases [GH-90000]")
    class DurationTests {

        @Test
        @DisplayName("Very fast execution (microsecond-level) is recorded [GH-90000]")
        void veryFastExecution() { // GH-90000
            long startTime = System.nanoTime(); // GH-90000
            // Simulate very fast execution
            metrics.recordExecution(System.currentTimeMillis() - 1, true); // GH-90000

            assertThat(registry.find("agent.execution.duration [GH-90000]").timer().count()).isEqualTo(1L);
            // Duration should be positive (even if very small) // GH-90000
            assertThat(registry.find("agent.execution.duration [GH-90000]").timer().totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                .isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Long running execution (minutes) is recorded correctly [GH-90000]")
        void longRunningExecution() { // GH-90000
            // Simulate 5 minute execution
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000); // GH-90000
            metrics.recordExecution(fiveMinutesAgo, true); // GH-90000

            assertThat(registry.find("agent.execution.success [GH-90000]").counter().count()).isEqualTo(1.0);
            assertThat(registry.find("agent.execution.duration [GH-90000]").timer().count()).isEqualTo(1L);
        }
    }

    // ============================================
    // METRIC CONSISTENCY (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Metric Consistency [GH-90000]")
    class ConsistencyTests {

        @Test
        @DisplayName("Success + Failure count equals total execution count [GH-90000]")
        void successFailureConsistency() { // GH-90000
            // Record mixed outcomes
            metrics.recordExecution(System.currentTimeMillis() - 100, true); // GH-90000
            metrics.recordExecution(System.currentTimeMillis() - 80, true); // GH-90000
            metrics.recordExecution(System.currentTimeMillis() - 60, false); // GH-90000
            metrics.recordExecution(System.currentTimeMillis() - 40, false); // GH-90000
            metrics.recordExecution(System.currentTimeMillis() - 20, true); // GH-90000

            double successCount = registry.find("agent.execution.success [GH-90000]").counter().count();
            double failureCount = registry.find("agent.execution.failure [GH-90000]").counter().count();
            long totalDuration = registry.find("agent.execution.duration [GH-90000]").timer().count();

            // Success + Failure should equal total recorded
            assertThat(successCount + failureCount).isEqualTo((double) totalDuration); // GH-90000
            assertThat(successCount).isEqualTo(3.0); // GH-90000
            assertThat(failureCount).isEqualTo(2.0); // GH-90000
        }
    }
}
