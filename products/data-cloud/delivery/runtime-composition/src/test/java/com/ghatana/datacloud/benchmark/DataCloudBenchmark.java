package com.ghatana.datacloud.benchmark;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark tests for DataCloud throughput and latency (P3.7).
 *
 * <p>Measures entity save/query throughput, event ingestion throughput,
 * and end-to-end latency under concurrent tenant load.
 *
 * <p>These benchmarks are run with JMH in CI to track regression.
 */
public class DataCloudBenchmark {

    private static DataCloudClient client;
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 10000;
    private static final int CONCURRENT_TENANTS = 10;

    @BeforeAll
    static void setUp() {
        client = DataCloud.embedded();
    }

    @Test
    void benchmarkEntitySaveThroughput() throws Exception {
        String tenant = "bench-save";
        String collection = "bench_entities";

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            client.save(tenant, collection, Map.of("i", i, "ts", Instant.now().toString())).getResult();
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            client.save(tenant, collection, Map.of("i", i, "ts", Instant.now().toString())).getResult();
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        double opsPerSec = (BENCHMARK_ITERATIONS * 1000.0) / durationMs;
        System.out.printf("Entity save throughput: %.0f ops/sec (total %d ms)%n", opsPerSec, durationMs);
    }

    @Test
    void benchmarkEntityQueryThroughput() throws Exception {
        String tenant = "bench-query";
        String collection = "bench_entities";
        for (int i = 0; i < 10000; i++) {
            client.save(tenant, collection, Map.of("i", i, "category", i % 100)).getResult();
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            client.query(tenant, collection, DataCloudClient.Query.limit(100)).getResult();
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        double opsPerSec = (BENCHMARK_ITERATIONS * 1000.0) / durationMs;
        System.out.printf("Entity query throughput: %.0f ops/sec (total %d ms)%n", opsPerSec, durationMs);
    }

    @Test
    void benchmarkEventIngestionThroughput() throws Exception {
        String tenant = "bench-events";

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            client.appendEvent(
                tenant,
                DataCloudClient.Event.builder().type("benchmark.test").payload(Map.of("i", i)).build()).getResult();
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            client.appendEvent(
                tenant,
                DataCloudClient.Event.builder().type("benchmark.test").payload(Map.of("i", i)).build()).getResult();
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        double opsPerSec = (BENCHMARK_ITERATIONS * 1000.0) / durationMs;
        System.out.printf("Event ingestion throughput: %.0f ops/sec (total %d ms)%n", opsPerSec, durationMs);
    }

    @Test
    void benchmarkConcurrentTenantLoad() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TENANTS);
        AtomicInteger counter = new AtomicInteger(0);
        int opsPerTenant = 1000;

        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < CONCURRENT_TENANTS; t++) {
            String tenant = "concurrent-" + t;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < opsPerTenant; i++) {
                    client.save(tenant, "concurrent", Map.of("i", counter.incrementAndGet())).getResult();
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        int totalOps = CONCURRENT_TENANTS * opsPerTenant;
        double opsPerSec = (totalOps * 1000.0) / durationMs;
        System.out.printf("Concurrent tenant load: %.0f ops/sec across %d tenants (total %d ms)%n",
            opsPerSec, CONCURRENT_TENANTS, durationMs);
        executor.shutdown();
    }
}
