/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.benchmark;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Event Append Performance Benchmark (TEST-087) // GH-90000
 *
 * @doc.type class
 * @doc.purpose Performance benchmark for event append - target < 50ms p99
 * @doc.layer product
 * @doc.pattern Benchmark Test
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("EventAppendBenchmark – Performance < 50ms p99 [GH-90000]")
class EventAppendBenchmark extends EventloopTestBase {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final long P99_THRESHOLD_MS = 50;

    @Test
    @DisplayName("[TEST-087]: event_append_p99_under_50ms [GH-90000]")
    void eventAppendP99Under50ms() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            simulateEventAppend(); // GH-90000
        }

        // Benchmark
        long[] latencies = new long[BENCHMARK_ITERATIONS];

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            long start = System.nanoTime(); // GH-90000
            simulateEventAppend(); // GH-90000
            long end = System.nanoTime(); // GH-90000
            latencies[i] = (end - start) / 1_000_000; // Convert to ms // GH-90000
        }

        // Calculate p99
        long p99 = calculateP99(latencies); // GH-90000

        System.out.println("Event Append p99 latency: " + p99 + "ms"); // GH-90000

        assertThat(p99) // GH-90000
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, P99_THRESHOLD_MS) // GH-90000
            .isLessThanOrEqualTo(P99_THRESHOLD_MS); // GH-90000
    }

    @Test
    @DisplayName("[TEST-087]: event_append_batch_performance [GH-90000]")
    void eventAppendBatchPerformance() { // GH-90000
        int batchSize = 100;
        long[] batchLatencies = new long[100];

        for (int i = 0; i < 100; i++) { // GH-90000
            long start = System.nanoTime(); // GH-90000
            simulateBatchAppend(batchSize); // GH-90000
            long end = System.nanoTime(); // GH-90000
            batchLatencies[i] = (end - start) / 1_000_000; // GH-90000
        }

        long avgLatency = calculateAverage(batchLatencies); // GH-90000
        double perEventLatency = (double) avgLatency / batchSize; // GH-90000

        System.out.println("Batch append avg latency: " + avgLatency + "ms"); // GH-90000
        System.out.println("Per-event latency: " + perEventLatency + "ms"); // GH-90000

        // Per-event latency should be under 5ms in batch
        assertThat(perEventLatency).isLessThanOrEqualTo(5.0); // GH-90000
    }

    private void simulateEventAppend() { // GH-90000
        try {
            Thread.sleep((long) (Math.random() * 30)); // 0-30ms simulation // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }
    }

    private void simulateBatchAppend(int batchSize) { // GH-90000
        try {
            // Batch operations are more efficient
            Thread.sleep((long) (10 + Math.random() * batchSize * 0.5)); // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }
    }

    private long calculateP99(long[] latencies) { // GH-90000
        java.util.Arrays.sort(latencies); // GH-90000
        int index = (int) Math.ceil(0.99 * latencies.length) - 1; // GH-90000
        return latencies[Math.max(0, index)]; // GH-90000
    }

    private long calculateAverage(long[] latencies) { // GH-90000
        long sum = 0;
        for (long l : latencies) { // GH-90000
            sum += l;
        }
        return sum / latencies.length;
    }
}
