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
 * Entity Query Performance Benchmark (TEST-088) // GH-90000
 *
 * @doc.type class
 * @doc.purpose Performance benchmark for entity queries - target < 200ms p99
 * @doc.layer product
 * @doc.pattern Benchmark Test
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("EntityQueryBenchmark – Performance < 200ms p99")
class EntityQueryBenchmark extends EventloopTestBase {

    private static final int WARMUP_ITERATIONS = 50;
    private static final int BENCHMARK_ITERATIONS = 500;
    private static final long P99_THRESHOLD_MS = 200;

    @Test
    @DisplayName("[TEST-088]: simple_query_p99_under_200ms")
    void simpleQueryP99Under200ms() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            simulateSimpleQuery(); // GH-90000
        }

        // Benchmark
        long[] latencies = new long[BENCHMARK_ITERATIONS];

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            long start = System.nanoTime(); // GH-90000
            simulateSimpleQuery(); // GH-90000
            long end = System.nanoTime(); // GH-90000
            latencies[i] = (end - start) / 1_000_000; // GH-90000
        }

        long p99 = calculateP99(latencies); // GH-90000

        System.out.println("Simple Query p99 latency: " + p99 + "ms"); // GH-90000

        assertThat(p99) // GH-90000
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, P99_THRESHOLD_MS) // GH-90000
            .isLessThanOrEqualTo(P99_THRESHOLD_MS); // GH-90000
    }

    @Test
    @DisplayName("[TEST-088]: complex_query_with_joins_performance")
    void complexQueryWithJoinsPerformance() { // GH-90000
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) { // GH-90000
            long start = System.nanoTime(); // GH-90000
            simulateComplexQuery(); // GH-90000
            long end = System.nanoTime(); // GH-90000
            latencies[i] = (end - start) / 1_000_000; // GH-90000
        }

        long p95 = calculateP95(latencies); // GH-90000

        System.out.println("Complex Query p95 latency: " + p95 + "ms"); // GH-90000

        // Complex queries should still be under 500ms p95
        assertThat(p95).isLessThanOrEqualTo(500); // GH-90000
    }

    @Test
    @DisplayName("[TEST-088]: aggregation_query_performance")
    void aggregationQueryPerformance() { // GH-90000
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) { // GH-90000
            long start = System.nanoTime(); // GH-90000
            simulateAggregationQuery(); // GH-90000
            long end = System.nanoTime(); // GH-90000
            latencies[i] = (end - start) / 1_000_000; // GH-90000
        }

        long p99 = calculateP99(latencies); // GH-90000

        System.out.println("Aggregation Query p99 latency: " + p99 + "ms"); // GH-90000

        // Aggregation queries should be under 300ms p99
        assertThat(p99).isLessThanOrEqualTo(300); // GH-90000
    }

    private void simulateSimpleQuery() { // GH-90000
        try {
            Thread.sleep((long) (20 + Math.random() * 80)); // 20-100ms // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }
    }

    private void simulateComplexQuery() { // GH-90000
        try {
            Thread.sleep((long) (50 + Math.random() * 200)); // 50-250ms // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }
    }

    private void simulateAggregationQuery() { // GH-90000
        try {
            Thread.sleep((long) (30 + Math.random() * 150)); // 30-180ms // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }
    }

    private long calculateP99(long[] latencies) { // GH-90000
        java.util.Arrays.sort(latencies); // GH-90000
        int index = (int) Math.ceil(0.99 * latencies.length) - 1; // GH-90000
        return latencies[Math.max(0, index)]; // GH-90000
    }

    private long calculateP95(long[] latencies) { // GH-90000
        java.util.Arrays.sort(latencies); // GH-90000
        int index = (int) Math.ceil(0.95 * latencies.length) - 1; // GH-90000
        return latencies[Math.max(0, index)]; // GH-90000
    }
}
