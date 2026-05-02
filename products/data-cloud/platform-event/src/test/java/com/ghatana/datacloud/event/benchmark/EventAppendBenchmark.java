/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 *   <li>Single event append through EventBuffer.offer()</li> 
 *   <li>Batch event append through EventBuffer.drain()</li> 
 *   <li>EventBuffer spill operations</li>
 * </ul>
 *
 * <p>Performance targets:
 * <ul>
 *   <li>Single event offer: < 1ms p99</li>
 *   <li>Batch drain (100 events): < 10ms p99</li> 
 *   <li>Buffer spill (1000 events): < 100ms p99</li> 
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Performance benchmark for event append operations
 * @doc.layer product
 * @doc.pattern Benchmark Test
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS) 
@DisplayName("EventAppendBenchmark – platform-event Performance")
class EventAppendBenchmark extends EventloopTestBase {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final long SINGLE_EVENT_P99_THRESHOLD_MS = 1;
    private static final long BATCH_DRAIN_P99_THRESHOLD_MS = 10;
    private static final long SPILL_P99_THRESHOLD_MS = 100;

    @Test
    @DisplayName("single event offer p99 under 1ms")
    void singleEventOfferP99Under1ms() { 
        EventBuffer buffer = new EventBuffer(new InMemoryEventLogStore(), "benchmark-buffer"); 

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { 
            EventLogStore.EventEntry entry = createEventEntry(i); 
            buffer.offer(entry); 
        }

        // Benchmark
        long[] latencies = new long[BENCHMARK_ITERATIONS];

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { 
            EventLogStore.EventEntry entry = createEventEntry(i); 
            long start = System.nanoTime(); 
            boolean accepted = buffer.offer(entry); 
            long end = System.nanoTime(); 
            
            assertThat(accepted).isTrue(); 
            latencies[i] = (end - start) / 1_000_000; // Convert to ms 
        }

        long p99 = calculateP99(latencies); 

        System.out.println("Single event offer p99 latency: " + p99 + "ms"); 

        assertThat(p99) 
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, SINGLE_EVENT_P99_THRESHOLD_MS) 
            .isLessThanOrEqualTo(SINGLE_EVENT_P99_THRESHOLD_MS); 
    }

    @Test
    @DisplayName("batch drain (100 events) p99 under 10ms")
    void batchDrainP99Under10ms() { 
        EventBuffer buffer = new EventBuffer(new InMemoryEventLogStore(), "benchmark-buffer"); 
        int batchSize = 100;

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { 
            fillBuffer(buffer, batchSize); 
            buffer.drain(batchSize); 
        }

        // Benchmark
        long[] latencies = new long[BENCHMARK_ITERATIONS];

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { 
            fillBuffer(buffer, batchSize); 
            long start = System.nanoTime(); 
            List<EventLogStore.EventEntry> drained = buffer.drain(batchSize); 
            long end = System.nanoTime(); 
            
            assertThat(drained).hasSize(batchSize); 
            latencies[i] = (end - start) / 1_000_000; 
        }

        long p99 = calculateP99(latencies); 

        System.out.println("Batch drain (100 events) p99 latency: " + p99 + "ms"); 

        assertThat(p99) 
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, BATCH_DRAIN_P99_THRESHOLD_MS) 
            .isLessThanOrEqualTo(BATCH_DRAIN_P99_THRESHOLD_MS); 
    }

    @Test
    @DisplayName("buffer spill (1000 events) p99 under 100ms")
    void bufferSpillP99Under100ms() { 
        int capacity = 5000;
        int highWaterMark = 4000;
        int spillCount = 1000;
        
        EventBuffer buffer = new EventBuffer( 
            new InMemoryEventLogStore(), 
            "benchmark-buffer",
            capacity,
            highWaterMark,
            1000
        );

        // Warmup
        for (int i = 0; i < 10; i++) { 
            fillBuffer(buffer, highWaterMark + spillCount); 
            runPromise(() -> buffer.spillExcess("tenant-123"));
        }

        // Benchmark
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) { 
            fillBuffer(buffer, highWaterMark + spillCount); 
            long start = System.nanoTime(); 
            int spilled = runPromise(() -> buffer.spillExcess("tenant-123"));
            long end = System.nanoTime(); 
            
            assertThat(spilled).isGreaterThanOrEqualTo(spillCount); 
            latencies[i] = (end - start) / 1_000_000; 
        }

        long p99 = calculateP99(latencies); 

        System.out.println("Buffer spill (1000 events) p99 latency: " + p99 + "ms"); 

        assertThat(p99) 
            .withFailMessage("p99 latency %d ms exceeds threshold %d ms", p99, SPILL_P99_THRESHOLD_MS) 
            .isLessThanOrEqualTo(SPILL_P99_THRESHOLD_MS); 
    }

    @Test
    @DisplayName("concurrent event offer throughput")
    void concurrentEventOfferThroughput() { 
        EventBuffer buffer = new EventBuffer( 
            new InMemoryEventLogStore(), 
            "benchmark-buffer",
            500_000,
            450_000,
            50_000
        );
        int threadCount = 10;
        int eventsPerThread = 1000;
        long durationMs = 5000; // 5 second test

        long start = System.currentTimeMillis(); 
        long end = start + durationMs;

        List<Thread> threads = new ArrayList<>(); 
        AtomicLong totalAccepted = new AtomicLong(0); 

        for (int t = 0; t < threadCount; t++) { 
            Thread thread = new Thread(() -> { 
                int accepted = 0;
                while (System.currentTimeMillis() < end) { 
                    for (int i = 0; i < eventsPerThread; i++) { 
                        EventLogStore.EventEntry entry = createEventEntry(accepted); 
                        if (buffer.offer(entry)) { 
                            accepted++;
                        }
                    }
                }
                totalAccepted.addAndGet(accepted); 
            });
            threads.add(thread); 
            thread.start(); 
        }

        for (Thread thread : threads) { 
            try {
                thread.join(); 
            } catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
            }
        }

        long actualDuration = System.currentTimeMillis() - start; 
        double throughput = (double) totalAccepted.get() / (actualDuration / 1000.0); 

        System.out.println("Concurrent offer throughput: " + throughput + " events/sec"); 
        System.out.println("Total events accepted: " + totalAccepted.get()); 

        // Target: 10,000 events/sec
        assertThat(throughput).isGreaterThan(10000.0); 
    }

    private void fillBuffer(EventBuffer buffer, int count) { 
        // Clear buffer first
        buffer.drain(buffer.size()); 
        
        for (int i = 0; i < count; i++) { 
            EventLogStore.EventEntry entry = createEventEntry(i); 
            buffer.offer(entry); 
        }
    }

    private EventLogStore.EventEntry createEventEntry(int index) { 
        return EventLogStore.EventEntry.builder() 
            .eventType("event-type-" + (index % 10)) 
            .payload("{\"data\":\"value-" + index + "\"}") 
            .headers(Map.of("header", "value")) 
            .timestamp(Instant.now()) 
            .build(); 
    }

    private long calculateP99(long[] latencies) { 
        java.util.Arrays.sort(latencies); 
        int index = (int) Math.ceil(0.99 * latencies.length) - 1; 
        return latencies[Math.max(0, index)]; 
    }

    /**
     * In-memory EventLogStore implementation for benchmarking.
     */
    private static class InMemoryEventLogStore implements EventLogStore {
        private final List<EventLogStore.EventEntry> events = new ArrayList<>(); 
        private volatile long currentOffset = 0;

        @Override
        public Promise<Offset> append(TenantContext tenant, EventLogStore.EventEntry entry) { 
            events.add(entry); 
            return Promise.of(Offset.of(currentOffset++)); 
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventLogStore.EventEntry> entries) { 
            events.addAll(entries); 
            List<Offset> offsets = new ArrayList<>(); 
            for (int i = 0; i < entries.size(); i++) { 
                offsets.add(Offset.of(currentOffset++)); 
            }
            return Promise.of(offsets); 
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> read(TenantContext tenant, Offset from, int limit) { 
            int startIdx = (int) Long.parseLong(from.value()); 
            int endIdx = Math.min(startIdx + limit, events.size()); 
            return Promise.of(events.subList(startIdx, endIdx)); 
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> readByTimeRange( 
            TenantContext tenant,
            Instant startTime,
            Instant endTime,
            int limit
        ) {
            List<EventLogStore.EventEntry> filtered = new ArrayList<>(); 
            for (EventLogStore.EventEntry entry : events) { 
                if (!entry.timestamp().isBefore(startTime) && entry.timestamp().isBefore(endTime)) { 
                    filtered.add(entry); 
                    if (filtered.size() >= limit) break; 
                }
            }
            return Promise.of(filtered); 
        }

        @Override
        public Promise<List<EventLogStore.EventEntry>> readByType( 
            TenantContext tenant,
            String eventType,
            Offset from,
            int limit
        ) {
            List<EventLogStore.EventEntry> filtered = new ArrayList<>(); 
            int startIdx = (int) Long.parseLong(from.value()); 
            for (int i = startIdx; i < events.size() && filtered.size() < limit; i++) { 
                EventLogStore.EventEntry entry = events.get(i); 
                if (entry.eventType().equals(eventType)) { 
                    filtered.add(entry); 
                }
            }
            return Promise.of(filtered); 
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) { 
            if (events.isEmpty()) { 
                return Promise.of(Offset.of(0)); 
            }
            return Promise.of(Offset.of(currentOffset - 1)); 
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) { 
            return Promise.of(Offset.of(0)); 
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventLogStore.EventEntry> handler) { 
            // Simple implementation that delivers current events and cancels immediately
            int startIdx = (int) Long.parseLong(from.value()); 
            for (int i = startIdx; i < events.size(); i++) { 
                handler.accept(events.get(i)); 
            }
            return Promise.of(new Subscription() { 
                private volatile boolean cancelled = false;
                
                @Override
                public void cancel() { 
                    cancelled = true;
                }
                
                @Override
                public boolean isCancelled() { 
                    return cancelled;
                }
            });
        }
    }
}
