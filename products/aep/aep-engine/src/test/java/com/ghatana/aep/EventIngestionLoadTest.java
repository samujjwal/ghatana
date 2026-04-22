/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
@DisplayName("Event Ingestion – Load Tests [GH-90000]")
class EventIngestionLoadTest extends EventloopTestBase {

    private AepEngine engine;
    private static final int TARGET_THROUGHPUT_EVENTS_PER_SECOND = 1000;
    private static final int LOAD_TEST_DURATION_SECONDS = 10;
    private static final int CONCURRENT_THREADS = 10;

    @BeforeEach
    void setUp() { // GH-90000
        engine = Aep.forTesting(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Baseline Performance [GH-90000]")
    class BaselineTests {

        @Test
        @DisplayName("single-threaded ingestion baseline [GH-90000]")
        void singleThreadedIngestionBaseline() { // GH-90000
            String tenantId = "tenant-baseline";
            
            // Register a simple pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "baseline-pipeline",
                "Baseline Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("baseline-step", "register_pattern", Map.of( // GH-90000
                        "name", "baseline-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // Ingest events sequentially
            int eventCount = 1000;
            long startTime = System.nanoTime(); // GH-90000
            
            for (int i = 0; i < eventCount; i++) { // GH-90000
                AepEngine.Event event = new AepEngine.Event( // GH-90000
                    "test.event",
                    Map.of("value", i % 100), // GH-90000
                    Map.of("correlationId", "corr-" + i), // GH-90000
                    Instant.now() // GH-90000
                );
                runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
            }
            
            long endTime = System.nanoTime(); // GH-90000
            long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
            double eventsPerSecond = (eventCount * 1000.0) / durationMs; // GH-90000

            // Baseline should handle at least 100 events/second single-threaded
            assertThat(eventsPerSecond).isGreaterThan(100.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Concurrent Ingestion [GH-90000]")
    class ConcurrentTests {

        @Test
        @DisplayName("concurrent ingestion maintains throughput [GH-90000]")
        void concurrentIngestionMaintainsThroughput() { // GH-90000
            String tenantId = "tenant-concurrent-load";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "concurrent-pipeline",
                "Concurrent Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("concurrent-step", "register_pattern", Map.of( // GH-90000
                        "name", "concurrent-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // Ingest events concurrently
            int eventsPerThread = 100;
            int totalEvents = eventsPerThread * CONCURRENT_THREADS;
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000
            AtomicInteger errorCount = new AtomicInteger(0); // GH-90000
            AtomicLong totalLatencyMs = new AtomicLong(0); // GH-90000

            List<CompletableFuture<Void>> futures = new ArrayList<>(); // GH-90000
            long startTime = System.nanoTime(); // GH-90000

            for (int thread = 0; thread < CONCURRENT_THREADS; thread++) { // GH-90000
                final int threadIndex = thread;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> { // GH-90000
                    for (int i = 0; i < eventsPerThread; i++) { // GH-90000
                        int eventId = threadIndex * eventsPerThread + i;
                        AepEngine.Event event = new AepEngine.Event( // GH-90000
                            "test.event",
                            Map.of("value", eventId % 100), // GH-90000
                            Map.of("correlationId", "corr-" + eventId), // GH-90000
                            Instant.now() // GH-90000
                        );
                        
                        long eventStart = System.nanoTime(); // GH-90000
                        try {
                            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
                            successCount.incrementAndGet(); // GH-90000
                        } catch (Exception e) { // GH-90000
                            errorCount.incrementAndGet(); // GH-90000
                        }
                        long eventEnd = System.nanoTime(); // GH-90000
                        totalLatencyMs.addAndGet((eventEnd - eventStart) / 1_000_000); // GH-90000
                    }
                });
                futures.add(future); // GH-90000
            }

            // Wait for all threads to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join(); // GH-90000

            long endTime = System.nanoTime(); // GH-90000
            long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
            double eventsPerSecond = (totalEvents * 1000.0) / durationMs; // GH-90000
            double avgLatencyMs = (double) totalLatencyMs.get() / totalEvents; // GH-90000

            // Verify throughput target
            assertThat(eventsPerSecond).isGreaterThan(TARGET_THROUGHPUT_EVENTS_PER_SECOND * 0.5); // At least 50% of target // GH-90000
            assertThat(successCount.get()).isEqualTo(totalEvents); // GH-90000
            assertThat(errorCount.get()).isZero(); // GH-90000
            assertThat(avgLatencyMs).isLessThan(100.0); // Average latency under 100ms // GH-90000
        }

        @Test
        @DisplayName("concurrent ingestion with different event types [GH-90000]")
        void concurrentIngestionWithDifferentEventTypes() { // GH-90000
            String tenantId = "tenant-mixed-types";
            
            // Register patterns for different event types
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "mixed-pipeline",
                "Mixed Types Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step1", "register_pattern", Map.of( // GH-90000
                        "name", "login-pattern",
                        "patternType", "SEQUENCE",
                        "eventTypes", List.of("login", "purchase") // GH-90000
                    )),
                    new AepEngine.PipelineStep("step2", "register_pattern", Map.of( // GH-90000
                        "name", "click-pattern",
                        "patternType", "THRESHOLD",
                        "field", "clickCount",
                        "threshold", 10.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // Ingest mixed event types concurrently
            String[] eventTypes = {"login", "purchase", "click", "view", "logout"};
            int eventsPerThread = 50;
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            List<CompletableFuture<Void>> futures = new ArrayList<>(); // GH-90000

            for (int thread = 0; thread < CONCURRENT_THREADS; thread++) { // GH-90000
                final int threadIndex = thread;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> { // GH-90000
                    for (int i = 0; i < eventsPerThread; i++) { // GH-90000
                        int eventId = threadIndex * eventsPerThread + i;
                        String type = eventTypes[eventId % eventTypes.length];
                        AepEngine.Event event = new AepEngine.Event( // GH-90000
                            type,
                            Map.of("value", eventId, "clickCount", eventId % 20), // GH-90000
                            Map.of("correlationId", "corr-" + eventId), // GH-90000
                            Instant.now() // GH-90000
                        );
                        
                        try {
                            runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
                            successCount.incrementAndGet(); // GH-90000
                        } catch (Exception e) { // GH-90000
                            // Log but don't fail test
                        }
                    }
                });
                futures.add(future); // GH-90000
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join(); // GH-90000

            int totalEvents = eventsPerThread * CONCURRENT_THREADS;
            assertThat(successCount.get()).isGreaterThan((int) (totalEvents * 0.9)); // At least 90% success // GH-90000
        }
    }

    @Nested
    @DisplayName("Sustained Load [GH-90000]")
    class SustainedLoadTests {

        @Test
        @DisplayName("sustained load over time [GH-90000]")
        void sustainedLoadOverTime() { // GH-90000
            String tenantId = "tenant-sustained";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "sustained-pipeline",
                "Sustained Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("sustained-step", "register_pattern", Map.of( // GH-90000
                        "name", "sustained-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // Sustained load for duration
            int eventsPerSecond = 100;
            int totalEvents = eventsPerSecond * LOAD_TEST_DURATION_SECONDS;
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000
            AtomicInteger errorCount = new AtomicInteger(0); // GH-90000
            ConcurrentHashMap<Integer, Long> latencyBuckets = new ConcurrentHashMap<>(); // GH-90000

            long startTime = System.nanoTime(); // GH-90000
            int eventId = 0;

            while (eventId < totalEvents &&  // GH-90000
                   (System.nanoTime() - startTime) < Duration.ofSeconds(LOAD_TEST_DURATION_SECONDS).toNanos()) { // GH-90000
                
                AepEngine.Event event = new AepEngine.Event( // GH-90000
                    "test.event",
                    Map.of("value", eventId % 100), // GH-90000
                    Map.of("correlationId", "corr-" + eventId), // GH-90000
                    Instant.now() // GH-90000
                );
                
                long eventStart = System.nanoTime(); // GH-90000
                try {
                    runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
                    successCount.incrementAndGet(); // GH-90000
                } catch (Exception e) { // GH-90000
                    errorCount.incrementAndGet(); // GH-90000
                }
                long eventEnd = System.nanoTime(); // GH-90000
                
                long latencyMs = (eventEnd - eventStart) / 1_000_000; // GH-90000
                int bucket = (int) (latencyMs / 10) * 10; // Bucket by 10ms // GH-90000
                latencyBuckets.merge(bucket, 1L, Long::sum); // GH-90000
                
                eventId++;
                
                // Throttle to target rate
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000; // GH-90000
                long expectedMs = (eventId * 1000) / eventsPerSecond; // GH-90000
                if (elapsedMs < expectedMs) { // GH-90000
                    try {
                        Thread.sleep(expectedMs - elapsedMs); // GH-90000
                    } catch (InterruptedException e) { // GH-90000
                        Thread.currentThread().interrupt(); // GH-90000
                        break;
                    }
                }
            }

            long endTime = System.nanoTime(); // GH-90000
            long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
            double actualEventsPerSecond = (successCount.get() * 1000.0) / durationMs; // GH-90000

            // Verify sustained performance
            assertThat(actualEventsPerSecond).isGreaterThan(eventsPerSecond * 0.8); // At least 80% of target // GH-90000
            assertThat(errorCount.get()).isLessThan((int) Math.ceil(totalEvents * 0.01)); // Less than 1% errors // GH-90000
            
            // Verify latency distribution (most events should be under 50ms) // GH-90000
            long fastEvents = latencyBuckets.entrySet().stream() // GH-90000
                .filter(e -> e.getKey() < 50) // GH-90000
                .mapToLong(Map.Entry::getValue) // GH-90000
                .sum(); // GH-90000
            assertThat(fastEvents).isGreaterThan((long) (successCount.get() * 0.9)); // GH-90000
        }
    }

    @Nested
    @DisplayName("Memory Under Load [GH-90000]")
    class MemoryTests {

        @Test
        @DisplayName("memory usage remains stable under load [GH-90000]")
        void memoryUsageRemainsStableUnderLoad() { // GH-90000
            String tenantId = "tenant-memory";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "memory-pipeline",
                "Memory Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("memory-step", "register_pattern", Map.of( // GH-90000
                        "name", "memory-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // Measure memory before load
            Runtime runtime = Runtime.getRuntime(); // GH-90000
            runtime.gc(); // GH-90000
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory(); // GH-90000

            // Ingest events
            int eventCount = 10000;
            for (int i = 0; i < eventCount; i++) { // GH-90000
                AepEngine.Event event = new AepEngine.Event( // GH-90000
                    "test.event",
                    Map.of("value", i % 100, "data", "x".repeat(100)), // Larger payload // GH-90000
                    Map.of("correlationId", "corr-" + i), // GH-90000
                    Instant.now() // GH-90000
                );
                runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
            }

            // Measure memory after load
            runtime.gc(); // GH-90000
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory(); // GH-90000
            long memoryDelta = memoryAfter - memoryBefore;

            // Memory growth should be reasonable (less than 100MB for 10k events) // GH-90000
            assertThat(memoryDelta).isLessThan(100 * 1024 * 1024); // GH-90000
        }
    }

    @Nested
    @DisplayName("Latency Percentiles [GH-90000]")
    class LatencyTests {

        @Test
        @DisplayName("measures latency percentiles under load [GH-90000]")
        void measuresLatencyPercentilesUnderLoad() { // GH-90000
            String tenantId = "tenant-latency";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "latency-pipeline",
                "Latency Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("latency-step", "register_pattern", Map.of( // GH-90000
                        "name", "latency-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 50.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // Ingest events and measure latency
            int eventCount = 1000;
            List<Long> latencies = new ArrayList<>(); // GH-90000

            for (int i = 0; i < eventCount; i++) { // GH-90000
                AepEngine.Event event = new AepEngine.Event( // GH-90000
                    "test.event",
                    Map.of("value", i % 100), // GH-90000
                    Map.of("correlationId", "corr-" + i), // GH-90000
                    Instant.now() // GH-90000
                );
                
                long start = System.nanoTime(); // GH-90000
                runPromise(() -> engine.ingestEvent(tenantId, event)); // GH-90000
                long end = System.nanoTime(); // GH-90000
                latencies.add((end - start) / 1_000_000); // Convert to ms // GH-90000
            }

            // Calculate percentiles
            latencies.sort(Long::compareTo); // GH-90000
            long p50 = latencies.get(latencies.size() / 2); // GH-90000
            long p95 = latencies.get((int) (latencies.size() * 0.95)); // GH-90000
            long p99 = latencies.get((int) (latencies.size() * 0.99)); // GH-90000

            // Verify latency targets
            assertThat(p50).isLessThan(50);   // 50th percentile under 50ms // GH-90000
            assertThat(p95).isLessThan(100);  // 95th percentile under 100ms // GH-90000
            assertThat(p99).isLessThan(200);  // 99th percentile under 200ms // GH-90000
        }
    }
}
