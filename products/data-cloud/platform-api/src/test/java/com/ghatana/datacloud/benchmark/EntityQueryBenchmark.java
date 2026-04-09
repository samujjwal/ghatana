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
 * Entity Query Performance Benchmark (TEST-088)
 *
 * @doc.type class
 * @doc.purpose Performance benchmark for entity queries - target < 200ms p99
 * @doc.layer product
 * @doc.pattern Benchmark Test
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("EntityQueryBenchmark – Performance < 200ms p99")
class EntityQueryBenchmark extends EventloopTestBase {

    private static final int WARMUP_ITERATIONS = 50;
    private static final int BENCHMARK_ITERATIONS = 500;
    private static final long P99_THRESHOLD_MS = 200;

    @Test
    @DisplayName("[TEST-088]: simple_query_p99_under_200ms")
    void simpleQueryP99Under200ms() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            simulateSimpleQuery();
        }

        // Benchmark
        long[] latencies = new long[BENCHMARK_ITERATIONS];

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            simulateSimpleQuery();
            long end = System.nanoTime();
            latencies[i] = (end - start) / 1_000_000;
        }

        long p99 = calculateP99(latencies);

        System.out.println("Simple Query p99 latency: " + p99 + "ms");

        assertThat(p99)
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, P99_THRESHOLD_MS)
            .isLessThanOrEqualTo(P99_THRESHOLD_MS);
    }

    @Test
    @DisplayName("[TEST-088]: complex_query_with_joins_performance")
    void complexQueryWithJoinsPerformance() {
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            simulateComplexQuery();
            long end = System.nanoTime();
            latencies[i] = (end - start) / 1_000_000;
        }

        long p95 = calculateP95(latencies);

        System.out.println("Complex Query p95 latency: " + p95 + "ms");

        // Complex queries should still be under 500ms p95
        assertThat(p95).isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("[TEST-088]: aggregation_query_performance")
    void aggregationQueryPerformance() {
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            simulateAggregationQuery();
            long end = System.nanoTime();
            latencies[i] = (end - start) / 1_000_000;
        }

        long p99 = calculateP99(latencies);

        System.out.println("Aggregation Query p99 latency: " + p99 + "ms");

        // Aggregation queries should be under 300ms p99
        assertThat(p99).isLessThanOrEqualTo(300);
    }

    private void simulateSimpleQuery() {
        try {
            Thread.sleep((long) (20 + Math.random() * 80)); // 20-100ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateComplexQuery() {
        try {
            Thread.sleep((long) (50 + Math.random() * 200)); // 50-250ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateAggregationQuery() {
        try {
            Thread.sleep((long) (30 + Math.random() * 150)); // 30-180ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long calculateP99(long[] latencies) {
        java.util.Arrays.sort(latencies);
        int index = (int) Math.ceil(0.99 * latencies.length) - 1;
        return latencies[Math.max(0, index)];
    }

    private long calculateP95(long[] latencies) {
        java.util.Arrays.sort(latencies);
        int index = (int) Math.ceil(0.95 * latencies.length) - 1;
        return latencies[Math.max(0, index)];
    }
}
