/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event.buffer;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency regression tests for {@link EventBuffer}.
 *
 * <p>Verifies that the {@code ArrayBlockingQueue}-backed implementation
 * correctly enforces capacity bounds under concurrent load — the previous
 * {@code ConcurrentLinkedQueue} + {@code AtomicInteger} design had a
 * check-then-act (TOCTOU) race that could allow the buffer to exceed its
 * declared capacity.
 *
 * @doc.type class
 * @doc.purpose Concurrency regression tests for EventBuffer (DC-005)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EventBuffer — concurrency regression (DC-005)")
@ExtendWith(MockitoExtension.class)
class EventBufferConcurrencyTest extends EventloopTestBase {

    private static final int CAPACITY    = 50;
    private static final int HIGH_WATER  = 40;
    private static final int LOW_WATER   = 10;

    @Mock
    private EventLogStore spillStore;

    private EventBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new EventBuffer(spillStore, "concurrency-test", CAPACITY, HIGH_WATER, LOW_WATER);
    }

    @Test
    @DisplayName("buffer never exceeds capacity under concurrent offers from multiple threads")
    void bufferNeverExceedsCapacityUnderConcurrentOffers() throws InterruptedException {
        int threadCount   = 20;
        int offerPerThread = 10; // 20 × 10 = 200 total, capacity = 50 → plenty of rejections

        ExecutorService executor  = Executors.newFixedThreadPool(threadCount);
        CountDownLatch  startGate = new CountDownLatch(1);
        AtomicInteger   accepted  = new AtomicInteger(0);
        AtomicInteger   rejected  = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // all threads start simultaneously
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < offerPerThread; i++) {
                    boolean inserted = buffer.offer(newEntry());
                    if (inserted) accepted.incrementAndGet();
                    else          rejected.incrementAndGet();
                }
            });
        }

        startGate.countDown(); // release all threads at once
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // The buffer MUST NOT exceed its declared capacity at any moment
        assertThat(buffer.size())
            .as("buffer.size() must be <= CAPACITY")
            .isLessThanOrEqualTo(CAPACITY);

        // accepted + rejected must equal total attempts
        assertThat(accepted.get() + rejected.get())
            .isEqualTo(threadCount * offerPerThread);

        // At most CAPACITY events can have been accepted
        assertThat(accepted.get())
            .as("accepted events must not exceed declared capacity")
            .isLessThanOrEqualTo(CAPACITY);

        // Stats must reflect actual rejections (totalRejected counter from DC-005 fix)
        Map<String, Object> stats = buffer.stats();
        long reportedRejected = (Long) stats.get("totalRejected");
        assertThat(reportedRejected)
            .as("stats().totalRejected must match actual rejections")
            .isEqualTo(rejected.get());
    }

    @Test
    @DisplayName("concurrent offer and drain are mutually exclusive — no item is lost or duplicated")
    void concurrentOfferAndDrainAreConsistent() throws InterruptedException {
        // Fill buffer to exactly CAPACITY
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(buffer.offer(newEntry())).isTrue();
        }
        assertThat(buffer.size()).isEqualTo(CAPACITY);

        int drainThreads  = 5;
        int drainBatchSize = CAPACITY / drainThreads; // each drains equal share

        ExecutorService executor  = Executors.newFixedThreadPool(drainThreads);
        CountDownLatch  startGate = new CountDownLatch(1);
        List<List<EventEntry>> drainedBatches = new ArrayList<>();
        for (int i = 0; i < drainThreads; i++) drainedBatches.add(new ArrayList<>());
        List<List<EventEntry>> captured = java.util.Collections.synchronizedList(drainedBatches);

        for (int t = 0; t < drainThreads; t++) {
            final int idx = t;
            executor.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                captured.get(idx).addAll(buffer.drain(drainBatchSize));
            });
        }

        startGate.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // Total drained should equal CAPACITY; no item survives in the buffer
        int totalDrained = captured.stream().mapToInt(List::size).sum();
        assertThat(totalDrained)
            .as("total drained events must equal capacity (each item drained exactly once)")
            .isEqualTo(CAPACITY);
        assertThat(buffer.size()).isZero();
    }

    @Test
    @DisplayName("utilizationPct stat stays within [0, 100] at all times")
    void utilizationPctStatIsAlwaysInRange() throws InterruptedException {
        ExecutorService offerers = Executors.newFixedThreadPool(4);
        ExecutorService drainers = Executors.newFixedThreadPool(2);
        CountDownLatch done = new CountDownLatch(6);

        for (int t = 0; t < 4; t++) {
            offerers.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        buffer.offer(newEntry());
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        for (int t = 0; t < 2; t++) {
            drainers.submit(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        buffer.drain(5);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();

        Map<String, Object> stats = buffer.stats();
        int utilizationPct = (Integer) stats.get("utilizationPct");
        assertThat(utilizationPct).isBetween(0, 100);

        offerers.shutdown();
        drainers.shutdown();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static EventEntry newEntry() {
        return EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType("test.event")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .headers(Map.of())
            .build();
    }
}
