/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.benchmarks;

import com.ghatana.datacloud.event.buffer.EventBuffer;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * JMH benchmarks for {@link EventBuffer} — the in-memory backpressure buffer that
 * sits between event intake and the durable event log store.
 *
 * <h2>What is measured</h2>
 * <ol>
 *   <li><b>offer throughput</b> — single-threaded sequential enqueue rate (fast path, no spill)</li>
 *   <li><b>drain throughput</b> — sequential drain rate on a pre-filled buffer</li>
 *   <li><b>offer-drain loop</b> — combined offer+drain cycle latency per batch</li>
 *   <li><b>concurrent offer</b> — multi-threaded enqueue contention ({@link Threads})</li>
 * </ol>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew :products:data-cloud:delivery:runtime-composition:jmh
 * # Results at build/reports/jmh/results.json
 *
 * # Run only EventBuffer benchmarks:
 * ./gradlew :products:data-cloud:delivery:runtime-composition:jmh \
 *   -Pjmh.include="EventBufferBenchmark"
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JMH benchmarks for EventBuffer offer/drain throughput and contention
 * @doc.layer product
 * @doc.pattern Benchmark
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class EventBufferBenchmark {

    // =========================================================================
    // No-op EventLogStore: never called in the sub-high-water-mark benchmarks.
    // Used only to satisfy EventBuffer's constructor requirement.
    // =========================================================================

    /**
     * Minimal no-op implementation of {@link EventLogStore} — used only to satisfy
     * EventBuffer's constructor. The spill path is never triggered in benchmarks
     * that stay below the high-water mark.
     */
    private static final EventLogStore NOOP_STORE = new EventLogStore() {
        @Override
        public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
            return Promise.of(Offset.zero());
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
            List<Offset> offsets = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                offsets.add(Offset.of((long) i));
            }
            return Promise.of(offsets);
        }

        @Override
        public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, Instant start, Instant end, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) {
            return Promise.of(Offset.zero());
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) {
            return Promise.of(Offset.zero());
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
            return Promise.of(new Subscription() {
                @Override public void cancel() { }
                @Override public boolean isCancelled() { return true; }
                @Override public void setErrorHandler(Consumer<Throwable> handler) { }
                @Override public SubscriptionState getState() { return SubscriptionState.ACTIVE; }
            });
        }
    };

    // =========================================================================
    // State: pre-built EventEntry to avoid UUID/ByteBuffer allocation in hot path
    // =========================================================================

    /**
     * Benchmark-scoped state holding a pre-built {@link EventLogStore.EventEntry}.
     * Re-using the same entry eliminates allocation overhead from the hot path.
     */
    @State(Scope.Benchmark)
    public static class EntryState {

        EventLogStore.EventEntry entry;

        @Setup(Level.Trial)
        public void setup() {
            entry = EventLogStore.EventEntry.builder()
                    .eventId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                    .eventType("entity.created")
                    .eventVersion("1.0.0")
                    .payload(ByteBuffer.wrap("{\"id\":\"bench\",\"type\":\"product\"}".getBytes(StandardCharsets.UTF_8)))
                    .build();
        }
    }

    // =========================================================================
    // State: fresh buffer per iteration (avoids capacity exhaustion across runs)
    // =========================================================================

    /**
     * Per-invocation buffer state — a fresh EventBuffer is created for each
     * benchmark invocation to prevent capacity exhaustion across iterations.
     * The buffer is sized to 10 000 events with an 8 000-event high-water mark.
     */
    @State(Scope.Thread)
    public static class BufferState {

        @Param({"100", "1000"})
        public int batchSize;

        EventBuffer buffer;

        @Setup(Level.Invocation)
        public void setup() {
            buffer = new EventBuffer(NOOP_STORE, "bench-buffer");
        }

        @TearDown(Level.Invocation)
        public void teardown() {
            // drain any remaining events so the next invocation starts clean
            buffer.drain(Integer.MAX_VALUE);
        }
    }

    // =========================================================================
    // State: pre-filled buffer for drain benchmarks
    // =========================================================================

    /**
     * Pre-filled buffer state for drain benchmarks.
     * Filled to batchSize events before each invocation.
     */
    @State(Scope.Thread)
    public static class FilledBufferState {

        @Param({"100", "1000"})
        public int batchSize;

        EventBuffer buffer;
        EventLogStore.EventEntry entry;

        @Setup(Level.Invocation)
        public void setup() {
            entry = EventLogStore.EventEntry.builder()
                    .eventId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                    .eventType("entity.updated")
                    .payload(ByteBuffer.wrap("{}".getBytes(StandardCharsets.UTF_8)))
                    .build();
            buffer = new EventBuffer(NOOP_STORE, "bench-filled-buffer");
            for (int i = 0; i < batchSize; i++) {
                buffer.offer(entry);
            }
        }
    }

    // =========================================================================
    // Benchmark 1 — Single-threaded offer throughput (sub-high-water-mark)
    //
    // Measures: how fast EventBuffer.offer() can enqueue a pre-built EventEntry.
    // The buffer has DEFAULT_CAPACITY=10 000 and batchSize ≤ 1000, so no spill
    // is triggered. This exercises the ArrayBlockingQueue.offer() fast path.
    // =========================================================================

    /**
     * Sequential offer: enqueues {@code batchSize} pre-built events into a fresh buffer.
     */
    @Benchmark
    public void singleThreadedOffer(BufferState bs, EntryState es) {
        for (int i = 0; i < bs.batchSize; i++) {
            bs.buffer.offer(es.entry);
        }
    }

    // =========================================================================
    // Benchmark 2 — Drain throughput on a pre-filled buffer
    //
    // Measures: how fast EventBuffer.drain(batchSize) can pull events out.
    // The buffer was pre-filled in @Setup so the hot path only tests the drain loop.
    // =========================================================================

    /**
     * Sequential drain: drains all {@code batchSize} events from a pre-filled buffer.
     */
    @Benchmark
    public void singleThreadedDrain(FilledBufferState fbs, Blackhole bh) {
        List<EventLogStore.EventEntry> drained = fbs.buffer.drain(fbs.batchSize);
        bh.consume(drained);
    }

    // =========================================================================
    // Benchmark 3 — Offer-then-drain cycle
    //
    // Measures: the round-trip latency of offering batchSize events and
    // immediately draining them all. Represents the steady-state pipeline loop.
    // =========================================================================

    /**
     * Offer-drain cycle: enqueues {@code batchSize} events then drains them all.
     */
    @Benchmark
    public void offerThenDrainCycle(BufferState bs, EntryState es, Blackhole bh) {
        for (int i = 0; i < bs.batchSize; i++) {
            bs.buffer.offer(es.entry);
        }
        List<EventLogStore.EventEntry> drained = bs.buffer.drain(bs.batchSize);
        bh.consume(drained);
    }

    // =========================================================================
    // Benchmark 4 — Concurrent single-event offer
    //
    // Measures: multi-threaded contention on a shared EventBuffer.
    // Uses 4 JMH worker threads, each calling offer() once per invocation.
    // ArrayBlockingQueue should handle this without locks on the offer path.
    // =========================================================================

    /**
     * Concurrent single-event offer: 4 threads each enqueue one event per iteration.
     */
    @Benchmark
    @Threads(4)
    public boolean concurrentOffer(EntryState es) {
        EventBuffer shared = new EventBuffer(NOOP_STORE, "bench-concurrent-buffer");
        return shared.offer(es.entry);
    }
}
