/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Load tests for event ingestion.
 *
 * Verifies throughput at scale:
 * - Single-threaded baseline performance
 * - Concurrent ingestion performance
 * - Sustained load over time
 * - Memory usage under load
 * - Latency percentiles
 *
 * @doc.type class
 * @doc.purpose Load testing for event ingestion throughput
 * @doc.layer product
 * @doc.pattern LoadTest
 */
@DisplayName("Event Ingestion – Load Tests")
class EventIngestionLoadTest extends EventloopTestBase {

    private AepEngine engine;
    private static final int TARGET_THROUGHPUT_EVENTS_PER_SECOND = 1000;
    private static final int LOAD_TEST_DURATION_SECONDS = 10;
    private static final int CONCURRENT_THREADS = 10;

    @BeforeEach
    void setUp() {
        engine = Aep.forTesting();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("Baseline Performance")
    class BaselineTests {

        @Test
        @DisplayName("single-threaded ingestion baseline")
        void singleThreadedIngestionBaseline() {
            String tenantId = "tenant-baseline";
            
            // Register a simple pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "baseline-pipeline",
                "Baseline Pipeline",
                List.of(
                    new AepEngine.PipelineStep("baseline-step", "register_pattern", Map.of(
                        "name", "baseline-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            runPromise(() -> engine.submitPipeline(tenantId, pipeline));

            // Ingest events sequentially
            int eventCount = 1000;
            long startTime = System.nanoTime();
            
            for (int i = 0; i < eventCount; i++) {
                AepEngine.Event event = new AepEngine.Event(
                    "test.event",
                    Map.of("value", i % 100),
                    Map.of("correlationId", "corr-" + i),
                    Instant.now()
                );
                runPromise(() -> engine.ingestEvent(tenantId, event));
            }
            
            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;
            double eventsPerSecond = (eventCount * 1000.0) / durationMs;

            // Baseline should handle at least 100 events/second single-threaded
            assertThat(eventsPerSecond).isGreaterThan(100);
        }
    }

    @Nested
    @DisplayName("Concurrent Ingestion")
    class ConcurrentTests {

        @Test
        @DisplayName("concurrent ingestion maintains throughput")
        void concurrentIngestionMaintainsThroughput() {
            String tenantId = "tenant-concurrent-load";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "concurrent-pipeline",
                "Concurrent Pipeline",
                List.of(
                    new AepEngine.PipelineStep("concurrent-step", "register_pattern", Map.of(
                        "name", "concurrent-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            runPromise(() -> engine.submitPipeline(tenantId, pipeline));

            // Ingest events concurrently
            int eventsPerThread = 100;
            int totalEvents = eventsPerThread * CONCURRENT_THREADS;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            AtomicLong totalLatencyMs = new AtomicLong(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            long startTime = System.nanoTime();

            for (int thread = 0; thread < CONCURRENT_THREADS; thread++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < eventsPerThread; i++) {
                        int eventId = thread * eventsPerThread + i;
                        AepEngine.Event event = new AepEngine.Event(
                            "test.event",
                            Map.of("value", eventId % 100),
                            Map.of("correlationId", "corr-" + eventId),
                            Instant.now()
                        );
                        
                        long eventStart = System.nanoTime();
                        try {
                            runPromise(() -> engine.ingestEvent(tenantId, event));
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                        long eventEnd = System.nanoTime();
                        totalLatencyMs.addAndGet((eventEnd - eventStart) / 1_000_000);
                    }
                });
                futures.add(future);
            }

            // Wait for all threads to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;
            double eventsPerSecond = (totalEvents * 1000.0) / durationMs;
            double avgLatencyMs = (double) totalLatencyMs.get() / totalEvents;

            // Verify throughput target
            assertThat(eventsPerSecond).isGreaterThan(TARGET_THROUGHPUT_EVENTS_PER_SECOND * 0.5); // At least 50% of target
            assertThat(successCount.get()).isEqualTo(totalEvents);
            assertThat(errorCount.get()).isZero();
            assertThat(avgLatencyMs).isLessThan(100); // Average latency under 100ms
        }

        @Test
        @DisplayName("concurrent ingestion with different event types")
        void concurrentIngestionWithDifferentEventTypes() {
            String tenantId = "tenant-mixed-types";
            
            // Register patterns for different event types
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "mixed-pipeline",
                "Mixed Types Pipeline",
                List.of(
                    new AepEngine.PipelineStep("step1", "register_pattern", Map.of(
                        "name", "login-pattern",
                        "patternType", "SEQUENCE",
                        "eventTypes", List.of("login", "purchase")
                    )),
                    new AepEngine.PipelineStep("step2", "register_pattern", Map.of(
                        "name", "click-pattern",
                        "patternType", "THRESHOLD",
                        "field", "clickCount",
                        "threshold", 10.0
                    ))
                )
            );

            runPromise(() -> engine.submitPipeline(tenantId, pipeline));

            // Ingest mixed event types concurrently
            String[] eventTypes = {"login", "purchase", "click", "view", "logout"};
            int eventsPerThread = 50;
            AtomicInteger successCount = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int thread = 0; thread < CONCURRENT_THREADS; thread++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < eventsPerThread; i++) {
                        int eventId = thread * eventsPerThread + i;
                        String type = eventTypes[eventId % eventTypes.length];
                        AepEngine.Event event = new AepEngine.Event(
                            type,
                            Map.of("value", eventId, "clickCount", eventId % 20),
                            Map.of("correlationId", "corr-" + eventId),
                            Instant.now()
                        );
                        
                        try {
                            runPromise(() -> engine.ingestEvent(tenantId, event));
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            // Log but don't fail test
                        }
                    }
                });
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            int totalEvents = eventsPerThread * CONCURRENT_THREADS;
            assertThat(successCount.get()).isGreaterThan(totalEvents * 0.9); // At least 90% success
        }
    }

    @Nested
    @DisplayName("Sustained Load")
    class SustainedLoadTests {

        @Test
        @DisplayName("sustained load over time")
        void sustainedLoadOverTime() {
            String tenantId = "tenant-sustained";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "sustained-pipeline",
                "Sustained Pipeline",
                List.of(
                    new AepEngine.PipelineStep("sustained-step", "register_pattern", Map.of(
                        "name", "sustained-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            runPromise(() -> engine.submitPipeline(tenantId, pipeline));

            // Sustained load for duration
            int eventsPerSecond = 100;
            int totalEvents = eventsPerSecond * LOAD_TEST_DURATION_SECONDS;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            ConcurrentHashMap<Integer, Long> latencyBuckets = new ConcurrentHashMap<>();

            long startTime = System.nanoTime();
            int eventId = 0;

            while (eventId < totalEvents && 
                   (System.nanoTime() - startTime) < Duration.ofSeconds(LOAD_TEST_DURATION_SECONDS).toNanos()) {
                
                AepEngine.Event event = new AepEngine.Event(
                    "test.event",
                    Map.of("value", eventId % 100),
                    Map.of("correlationId", "corr-" + eventId),
                    Instant.now()
                );
                
                long eventStart = System.nanoTime();
                try {
                    runPromise(() -> engine.ingestEvent(tenantId, event));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
                long eventEnd = System.nanoTime();
                
                long latencyMs = (eventEnd - eventStart) / 1_000_000;
                int bucket = (int) (latencyMs / 10) * 10; // Bucket by 10ms
                latencyBuckets.merge(bucket, 1L, Long::sum);
                
                eventId++;
                
                // Throttle to target rate
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                long expectedMs = (eventId * 1000) / eventsPerSecond;
                if (elapsedMs < expectedMs) {
                    try {
                        Thread.sleep(expectedMs - elapsedMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;
            double actualEventsPerSecond = (successCount.get() * 1000.0) / durationMs;

            // Verify sustained performance
            assertThat(actualEventsPerSecond).isGreaterThan(eventsPerSecond * 0.8); // At least 80% of target
            assertThat(errorCount.get()).isLessThan(totalEvents * 0.01); // Less than 1% errors
            
            // Verify latency distribution (most events should be under 50ms)
            long fastEvents = latencyBuckets.entrySet().stream()
                .filter(e -> e.getKey() < 50)
                .mapToLong(Map.Entry::getValue)
                .sum();
            assertThat(fastEvents).isGreaterThan(successCount.get() * 0.9);
        }
    }

    @Nested
    @DisplayName("Memory Under Load")
    class MemoryTests {

        @Test
        @DisplayName("memory usage remains stable under load")
        void memoryUsageRemainsStableUnderLoad() {
            String tenantId = "tenant-memory";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "memory-pipeline",
                "Memory Pipeline",
                List.of(
                    new AepEngine.PipelineStep("memory-step", "register_pattern", Map.of(
                        "name", "memory-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            runPromise(() -> engine.submitPipeline(tenantId, pipeline));

            // Measure memory before load
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            // Ingest events
            int eventCount = 10000;
            for (int i = 0; i < eventCount; i++) {
                AepEngine.Event event = new AepEngine.Event(
                    "test.event",
                    Map.of("value", i % 100, "data", "x".repeat(100)), // Larger payload
                    Map.of("correlationId", "corr-" + i),
                    Instant.now()
                );
                runPromise(() -> engine.ingestEvent(tenantId, event));
            }

            // Measure memory after load
            runtime.gc();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryDelta = memoryAfter - memoryBefore;

            // Memory growth should be reasonable (less than 100MB for 10k events)
            assertThat(memoryDelta).isLessThan(100 * 1024 * 1024);
        }
    }

    @Nested
    @DisplayName("Latency Percentiles")
    class LatencyTests {

        @Test
        @DisplayName("measures latency percentiles under load")
        void measuresLatencyPercentilesUnderLoad() {
            String tenantId = "tenant-latency";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "latency-pipeline",
                "Latency Pipeline",
                List.of(
                    new AepEngine.PipelineStep("latency-step", "register_pattern", Map.of(
                        "name", "latency-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            runPromise(() -> engine.submitPipeline(tenantId, pipeline));

            // Ingest events and measure latency
            int eventCount = 1000;
            List<Long> latencies = new ArrayList<>();

            for (int i = 0; i < eventCount; i++) {
                AepEngine.Event event = new AepEngine.Event(
                    "test.event",
                    Map.of("value", i % 100),
                    Map.of("correlationId", "corr-" + i),
                    Instant.now()
                );
                
                long start = System.nanoTime();
                runPromise(() -> engine.ingestEvent(tenantId, event));
                long end = System.nanoTime();
                latencies.add((end - start) / 1_000_000); // Convert to ms
            }

            // Calculate percentiles
            latencies.sort(Long::compareTo);
            long p50 = latencies.get(latencies.size() / 2);
            long p95 = latencies.get((int) (latencies.size() * 0.95));
            long p99 = latencies.get((int) (latencies.size() * 0.99));

            // Verify latency targets
            assertThat(p50).isLessThan(50);   // 50th percentile under 50ms
            assertThat(p95).isLessThan(100);  // 95th percentile under 100ms
            assertThat(p99).isLessThan(200);  // 99th percentile under 200ms
        }
    }
}
