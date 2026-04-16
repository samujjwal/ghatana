/**
 * Copyright (c) 2025 Ghatana Technologies
 * Data Cloud Query Benchmarks
 *
 * JMH microbenchmarks for Data Cloud query performance.
 * Measures query latency for different data sizes and complexity.
 *
 * @doc.type benchmark
 * @doc.purpose Performance measurement for Data Cloud queries
 * @doc.layer test
 * @doc.pattern JMH Benchmark
 */
package com.ghatana.yappc.infrastructure.datacloud.benchmarks;

import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.client.DataCloudClient;
import io.activej.eventloop.Eventloop;
import io.activej.inject.Injector;
import io.activej.promise.Promise;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark suite for Data Cloud query operations.
 *
 * <p><b>Benchmark Scenarios</b>
 * <ul>
 *   <li>Simple equality queries</li>
 *   <li>Multi-filter queries</li>
 *   <li>Paginated queries</li>
 *   <li>Large dataset queries</li>
 * </ul>
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
public class DataCloudQueryBenchmark {

    private YappcDataCloudRepository<TestEntity> repository;
    private DataCloudClient client;
    private Eventloop eventloop;

    @Setup
    public void setup() {
        eventloop = Eventloop.create();
        eventloop.start();

        Injector injector = Injector.of(
            new TestDataCloudModule(),
            new TestRepositoryModule()
        );

        client = injector.getInstance(DataCloudClient.class);
        repository = new YappcDataCloudRepository<>(client, "test_entities", TestEntity.class);

        // Seed test data
        seedTestData(1000);
    }

    @TearDown
    public void tearDown() {
        if (eventloop != null) {
            eventloop.shutdown();
        }
    }

    private void seedTestData(int count) {
        // Create test entities for benchmarking
        for (int i = 0; i < count; i++) {
            TestEntity entity = new TestEntity(
                "entity-" + i,
                "Type " + (i % 10),
                "Status " + (i % 5),
                System.currentTimeMillis() - (i * 3600000L)
            );
            repository.save(entity).toCompletableFuture().join();
        }
    }

    /**
     * Benchmark: Simple equality query by single field
     * Tests filtering by one indexed field.
     */
    @Benchmark
    public void simpleEqualityQuery(Blackhole blackhole) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("type", "Type 0");

        Promise<List<TestEntity>> result = repository.findByFilter(filter, null, 100, 0);
        blackhole.consume(result.toCompletableFuture().join());
    }

    /**
     * Benchmark: Multi-filter query
     * Tests filtering by multiple fields.
     */
    @Benchmark
    public void multiFilterQuery(Blackhole blackhole) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("type", "Type 1");
        filter.put("status", "Status 0");

        Promise<List<TestEntity>> result = repository.findByFilter(filter, null, 100, 0);
        blackhole.consume(result.toCompletableFuture().join());
    }

    /**
     * Benchmark: Paginated query
     * Tests cursor-based pagination performance.
     */
    @Benchmark
    public void paginatedQuery(Blackhole blackhole) {
        Map<String, Object> filter = new HashMap<>();

        Promise<PaginatedResult<TestEntity>> result = repository.findByFilterPaginated(
            filter, null, null, 50
        );
        blackhole.consume(result.toCompletableFuture().join());
    }

    /**
     * Benchmark: Find by field
     * Tests direct field lookup performance.
     */
    @Benchmark
    public void findByField(Blackhole blackhole) {
        Promise<List<TestEntity>> result = repository.findByField("type", "Type 2");
        blackhole.consume(result.toCompletableFuture().join());
    }

    /**
     * Benchmark: Find by ID
     * Tests primary key lookup performance.
     */
    @Benchmark
    public void findById(Blackhole blackhole) {
        String randomId = "entity-" + (int) (Math.random() * 1000);
        Promise<TestEntity> result = repository.findById(randomId);
        blackhole.consume(result.toCompletableFuture().join());
    }

    // Test entity for benchmarking
    public static record TestEntity(
        String id,
        String type,
        String status,
        long createdAt
    ) {}
}
