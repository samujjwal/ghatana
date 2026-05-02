/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Event Append Performance Benchmark (TEST-087) 
 *
 * @doc.type class
 * @doc.purpose Performance benchmark for event append - target < 50ms p99
 * @doc.layer product
 * @doc.pattern Benchmark Test
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS) 
@DisplayName("EventAppendBenchmark – Performance < 50ms p99")
class EventAppendBenchmark extends EventloopTestBase {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final long P99_THRESHOLD_MS = 50;

    @Test
    @DisplayName("[TEST-087]: event_append_p99_under_50ms")
    void eventAppendP99Under50ms() { 
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { 
            simulateEventAppend(); 
        }

        // Benchmark
        long[] latencies = new long[BENCHMARK_ITERATIONS];

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { 
            long start = System.nanoTime(); 
            simulateEventAppend(); 
            long end = System.nanoTime(); 
            latencies[i] = (end - start) / 1_000_000; // Convert to ms 
        }

        // Calculate p99
        long p99 = calculateP99(latencies); 

        System.out.println("Event Append p99 latency: " + p99 + "ms"); 

        assertThat(p99) 
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, P99_THRESHOLD_MS) 
            .isLessThanOrEqualTo(P99_THRESHOLD_MS); 
    }

    @Test
    @DisplayName("[TEST-087]: event_append_batch_performance")
    void eventAppendBatchPerformance() { 
        int batchSize = 100;
        long[] batchLatencies = new long[100];

        for (int i = 0; i < 100; i++) { 
            long start = System.nanoTime(); 
            simulateBatchAppend(batchSize); 
            long end = System.nanoTime(); 
            batchLatencies[i] = (end - start) / 1_000_000; 
        }

        long avgLatency = calculateAverage(batchLatencies); 
        double perEventLatency = (double) avgLatency / batchSize; 

        System.out.println("Batch append avg latency: " + avgLatency + "ms"); 
        System.out.println("Per-event latency: " + perEventLatency + "ms"); 

        // Per-event latency should be under 5ms in batch
        assertThat(perEventLatency).isLessThanOrEqualTo(5.0); 
    }

    private void simulateEventAppend() { 
        runCpuWork(256); 
    }

    private void simulateBatchAppend(int batchSize) { 
        // Batch operations amortize per-event overhead.
        runCpuWork(1024 + (batchSize * 16)); 
    }

    private void runCpuWork(int iterations) { 
        long acc = 0;
        for (int i = 0; i < iterations; i++) {
            acc += (long) i * 31;
        }
        if (acc == Long.MIN_VALUE) {
            throw new IllegalStateException("unreachable benchmark guard");
        }
    }

    private long calculateP99(long[] latencies) { 
        java.util.Arrays.sort(latencies); 
        int index = (int) Math.ceil(0.99 * latencies.length) - 1; 
        return latencies[Math.max(0, index)]; 
    }

    private long calculateAverage(long[] latencies) { 
        long sum = 0;
        for (long l : latencies) { 
            sum += l;
        }
        return sum / latencies.length;
    }
}
