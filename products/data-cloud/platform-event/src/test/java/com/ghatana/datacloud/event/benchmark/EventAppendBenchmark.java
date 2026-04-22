/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event.benchmark;

import com.ghatana.datacloud.event.buffer.EventBuffer;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Event Append Performance Benchmark for platform-event module.
 *
 * <p>Benchmarks actual event append operations:
 * <ul>
 *   <li>Single event append through EventBuffer.offer()</li> // GH-90000
 *   <li>Batch event append through EventBuffer.drain()</li> // GH-90000
 *   <li>EventBuffer spill operations</li>
 * </ul>
 *
 * <p>Performance targets:
 * <ul>
 *   <li>Single event offer: < 1ms p99</li>
 *   <li>Batch drain (100 events): < 10ms p99</li> // GH-90000
 *   <li>Buffer spill (1000 events): < 100ms p99</li> // GH-90000
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Performance benchmark for event append operations
 * @doc.layer product
 * @doc.pattern Benchmark Test
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("EventAppendBenchmark – platform-event Performance [GH-90000]")
class EventAppendBenchmark extends EventloopTestBase {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final long SINGLE_EVENT_P99_THRESHOLD_MS = 1;
    private static final long BATCH_DRAIN_P99_THRESHOLD_MS = 10;
    private static final long SPILL_P99_THRESHOLD_MS = 100;

    @Test
    @DisplayName("single event offer p99 under 1ms [GH-90000]")
    void singleEventOfferP99Under1ms() { // GH-90000
        EventBuffer buffer = new EventBuffer(new InMemoryEventLogStore(), "benchmark-buffer"); // GH-90000

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            EventLogStore.EventEntry entry = createEventEntry(i); // GH-90000
            buffer.offer(entry); // GH-90000
        }

        // Benchmark
        long[] latencies = new long[BENCHMARK_ITERATIONS];

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            EventLogStore.EventEntry entry = createEventEntry(i); // GH-90000
            long start = System.nanoTime(); // GH-90000
            boolean accepted = buffer.offer(entry); // GH-90000
            long end = System.nanoTime(); // GH-90000
            
            assertThat(accepted).isTrue(); // GH-90000
            latencies[i] = (end - start) / 1_000_000; // Convert to ms // GH-90000
        }

        long p99 = calculateP99(latencies); // GH-90000

        System.out.println("Single event offer p99 latency: " + p99 + "ms"); // GH-90000

        assertThat(p99) // GH-90000
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, SINGLE_EVENT_P99_THRESHOLD_MS) // GH-90000
            .isLessThanOrEqualTo(SINGLE_EVENT_P99_THRESHOLD_MS); // GH-90000
    }

    @Test
    @DisplayName("batch drain (100 events) p99 under 10ms [GH-90000]")
    void batchDrainP99Under10ms() { // GH-90000
        EventBuffer buffer = new EventBuffer(new InMemoryEventLogStore(), "benchmark-buffer"); // GH-90000
        int batchSize = 100;

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            fillBuffer(buffer, batchSize); // GH-90000
            buffer.drain(batchSize); // GH-90000
        }

        // Benchmark
        long[] latencies = new long[BENCHMARK_ITERATIONS];

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            fillBuffer(buffer, batchSize); // GH-90000
            long start = System.nanoTime(); // GH-90000
            List<EventLogStore.EventEntry> drained = buffer.drain(batchSize); // GH-90000
            long end = System.nanoTime(); // GH-90000
            
            assertThat(drained).hasSize(batchSize); // GH-90000
            latencies[i] = (end - start) / 1_000_000; // GH-90000
        }

        long p99 = calculateP99(latencies); // GH-90000

        System.out.println("Batch drain (100 events) p99 latency: " + p99 + "ms"); // GH-90000

        assertThat(p99) // GH-90000
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, BATCH_DRAIN_P99_THRESHOLD_MS) // GH-90000
            .isLessThanOrEqualTo(BATCH_DRAIN_P99_THRESHOLD_MS); // GH-90000
    }

    @Test
    @DisplayName("buffer spill (1000 events) p99 under 100ms [GH-90000]")
    void bufferSpillP99Under100ms() { // GH-90000
        int capacity = 5000;
        int highWaterMark = 4000;
        int spillCount = 1000;
        
        EventBuffer buffer = new EventBuffer( // GH-90000
            new InMemoryEventLogStore(), // GH-90000
            "benchmark-buffer",
            capacity,
            highWaterMark,
            1000
        );

        // Warmup
        for (int i = 0; i < 10; i++) { // GH-90000
            fillBuffer(buffer, highWaterMark + spillCount); // GH-90000
            runPromise(() -> buffer.spillExcess("tenant-123 [GH-90000]"));
        }

        // Benchmark
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) { // GH-90000
            fillBuffer(buffer, highWaterMark + spillCount); // GH-90000
            long start = System.nanoTime(); // GH-90000
            int spilled = runPromise(() -> buffer.spillExcess("tenant-123 [GH-90000]"));
            long end = System.nanoTime(); // GH-90000
            
            assertThat(spilled).isGreaterThanOrEqualTo(spillCount); // GH-90000
            latencies[i] = (end - start) / 1_000_000; // GH-90000
        }

        long p99 = calculateP99(latencies); // GH-90000

        System.out.println("Buffer spill (1000 events) p99 latency: " + p99 + "ms"); // GH-90000

        assertThat(p99) // GH-90000
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, SPILL_P99_THRESHOLD_MS) // GH-90000
            .isLessThanOrEqualTo(SPILL_P99_THRESHOLD_MS); // GH-90000
    }

    @Test
    @DisplayName("concurrent event offer throughput [GH-90000]")
    void concurrentEventOfferThroughput() { // GH-90000
        EventBuffer buffer = new EventBuffer( // GH-90000
            new InMemoryEventLogStore(), // GH-90000
            "benchmark-buffer",
            500_000,
            450_000,
            50_000
        );
        int threadCount = 10;
        int eventsPerThread = 1000;
        long durationMs = 5000; // 5 second test

        long start = System.currentTimeMillis(); // GH-90000
        long end = start + durationMs;

        List<Thread> threads = new ArrayList<>(); // GH-90000
        AtomicLong totalAccepted = new AtomicLong(0); // GH-90000

        for (int t = 0; t < threadCount; t++) { // GH-90000
            Thread thread = new Thread(() -> { // GH-90000
                int accepted = 0;
                while (System.currentTimeMillis() < end) { // GH-90000
                    for (int i = 0; i < eventsPerThread; i++) { // GH-90000
                        EventLogStore.EventEntry entry = createEventEntry(accepted); // GH-90000
                        if (buffer.offer(entry)) { // GH-90000
                            accepted++;
                        }
                    }
                }
                totalAccepted.addAndGet(accepted); // GH-90000
            });
            threads.add(thread); // GH-90000
            thread.start(); // GH-90000
        }

        for (Thread thread : threads) { // GH-90000
            try {
                thread.join(); // GH-90000
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
            }
        }

        long actualDuration = System.currentTimeMillis() - start; // GH-90000
        double throughput = (double) totalAccepted.get() / (actualDuration / 1000.0); // GH-90000

        System.out.println("Concurrent offer throughput: " + throughput + " events/sec"); // GH-90000
        System.out.println("Total events accepted: " + totalAccepted.get()); // GH-90000

        // Target: 10,000 events/sec
        assertThat(throughput).isGreaterThan(10000.0); // GH-90000
    }

    private void fillBuffer(EventBuffer buffer, int count) { // GH-90000
        // Clear buffer first
        buffer.drain(buffer.size()); // GH-90000
        
        for (int i = 0; i < count; i++) { // GH-90000
            EventLogStore.EventEntry entry = createEventEntry(i); // GH-90000
            buffer.offer(entry); // GH-90000
        }
    }

    private EventLogStore.EventEntry createEventEntry(int index) { // GH-90000
        return EventLogStore.EventEntry.builder() // GH-90000
            .eventType("event-type-" + (index % 10)) // GH-90000
            .payload("{\"data\":\"value-" + index + "\"}") // GH-90000
            .headers(Map.of("header", "value")) // GH-90000
            .timestamp(Instant.now()) // GH-90000
            .build(); // GH-90000
    }

    private long calculateP99(long[] latencies) { // GH-90000
        java.util.Arrays.sort(latencies); // GH-90000
        int index = (int) Math.ceil(0.99 * latencies.length) - 1; // GH-90000
        return latencies[Math.max(0, index)]; // GH-90000
    }

    /**
     * In-memory EventLogStore implementation for benchmarking.
     */
    private static class InMemoryEventLogStore implements EventLogStore {
        private final List<EventLogStore.EventEntry> events = new ArrayList<>(); // GH-90000
        private volatile long currentOffset = 0;

        @Override
        public Promise<Offset> append(TenantContext tenant, EventLogStore.EventEntry entry) { // GH-90000
            events.add(entry); // GH-90000
            return Promise.of(Offset.of(currentOffset++)); // GH-90000
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventLogStore.EventEntry> entries) { // GH-90000
            events.addAll(entries); // GH-90000
            List<Offset> offsets = new ArrayList<>(); // GH-90000
            for (int i = 0; i < entries.size(); i++) { // GH-90000
                offsets.add(Offset.of(currentOffset++)); // GH-90000
            }
            return Promise.of(offsets); // GH-90000
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> read(TenantContext tenant, Offset from, int limit) { // GH-90000
            int startIdx = (int) Long.parseLong(from.value()); // GH-90000
            int endIdx = Math.min(startIdx + limit, events.size()); // GH-90000
            return Promise.of(events.subList(startIdx, endIdx)); // GH-90000
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> readByTimeRange( // GH-90000
            TenantContext tenant,
            Instant startTime,
            Instant endTime,
            int limit
        ) {
            List<EventLogStore.EventEntry> filtered = new ArrayList<>(); // GH-90000
            for (EventLogStore.EventEntry entry : events) { // GH-90000
                if (!entry.timestamp().isBefore(startTime) && entry.timestamp().isBefore(endTime)) { // GH-90000
                    filtered.add(entry); // GH-90000
                    if (filtered.size() >= limit) break; // GH-90000
                }
            }
            return Promise.of(filtered); // GH-90000
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> readByType( // GH-90000
            TenantContext tenant,
            String eventType,
            Offset from,
            int limit
        ) {
            List<EventLogStore.EventEntry> filtered = new ArrayList<>(); // GH-90000
            int startIdx = (int) Long.parseLong(from.value()); // GH-90000
            for (int i = startIdx; i < events.size() && filtered.size() < limit; i++) { // GH-90000
                EventLogStore.EventEntry entry = events.get(i); // GH-90000
                if (entry.eventType().equals(eventType)) { // GH-90000
                    filtered.add(entry); // GH-90000
                }
            }
            return Promise.of(filtered); // GH-90000
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) { // GH-90000
            if (events.isEmpty()) { // GH-90000
                return Promise.of(Offset.of(0)); // GH-90000
            }
            return Promise.of(Offset.of(currentOffset - 1)); // GH-90000
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) { // GH-90000
            return Promise.of(Offset.of(0)); // GH-90000
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventLogStore.EventEntry> handler) { // GH-90000
            // Simple implementation that delivers current events and cancels immediately
            int startIdx = (int) Long.parseLong(from.value()); // GH-90000
            for (int i = startIdx; i < events.size(); i++) { // GH-90000
                handler.accept(events.get(i)); // GH-90000
            }
            return Promise.of(new Subscription() { // GH-90000
                private volatile boolean cancelled = false;
                
                @Override
                public void cancel() { // GH-90000
                    cancelled = true;
                }
                
                @Override
                public boolean isCancelled() { // GH-90000
                    return cancelled;
                }
            });
        }
    }
}
