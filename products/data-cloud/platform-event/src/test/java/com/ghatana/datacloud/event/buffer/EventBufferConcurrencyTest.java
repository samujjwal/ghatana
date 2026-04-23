/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event.buffer;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
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
 * check-then-act (TOCTOU) race that could allow the buffer to exceed its // GH-90000
 * declared capacity.
 *
 * @doc.type class
 * @doc.purpose Concurrency regression tests for EventBuffer (DC-005) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EventBuffer — concurrency regression (DC-005)")
@ExtendWith(MockitoExtension.class) // GH-90000
class EventBufferConcurrencyTest extends EventloopTestBase {

    private static final int CAPACITY    = 50;
    private static final int HIGH_WATER  = 40;
    private static final int LOW_WATER   = 10;

    @Mock
    private EventLogStore spillStore;

    private EventBuffer buffer;

    @BeforeEach
    void setUp() { // GH-90000
        buffer = new EventBuffer(spillStore, "concurrency-test", CAPACITY, HIGH_WATER, LOW_WATER); // GH-90000
    }

    @Test
    @DisplayName("buffer never exceeds capacity under concurrent offers from multiple threads")
    void bufferNeverExceedsCapacityUnderConcurrentOffers() throws InterruptedException { // GH-90000
        int threadCount   = 20;
        int offerPerThread = 10; // 20 × 10 = 200 total, capacity = 50 → plenty of rejections

        ExecutorService executor  = Executors.newFixedThreadPool(threadCount); // GH-90000
        CountDownLatch  startGate = new CountDownLatch(1); // GH-90000
        AtomicInteger   accepted  = new AtomicInteger(0); // GH-90000
        AtomicInteger   rejected  = new AtomicInteger(0); // GH-90000

        for (int t = 0; t < threadCount; t++) { // GH-90000
            executor.submit(() -> { // GH-90000
                try {
                    startGate.await(); // all threads start simultaneously // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                    return;
                }
                for (int i = 0; i < offerPerThread; i++) { // GH-90000
                    boolean inserted = buffer.offer(newEntry()); // GH-90000
                    if (inserted) accepted.incrementAndGet(); // GH-90000
                    else          rejected.incrementAndGet(); // GH-90000
                }
            });
        }

        startGate.countDown(); // release all threads at once // GH-90000
        executor.shutdown(); // GH-90000
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); // GH-90000

        // The buffer MUST NOT exceed its declared capacity at any moment
        assertThat(buffer.size()) // GH-90000
            .as("buffer.size() must be <= CAPACITY")
            .isLessThanOrEqualTo(CAPACITY); // GH-90000

        // accepted + rejected must equal total attempts
        assertThat(accepted.get() + rejected.get()) // GH-90000
            .isEqualTo(threadCount * offerPerThread); // GH-90000

        // At most CAPACITY events can have been accepted
        assertThat(accepted.get()) // GH-90000
            .as("accepted events must not exceed declared capacity")
            .isLessThanOrEqualTo(CAPACITY); // GH-90000

        // Stats must reflect actual rejections (totalRejected counter from DC-005 fix) // GH-90000
        Map<String, Object> stats = buffer.stats(); // GH-90000
        long reportedRejected = (Long) stats.get("totalRejected");
        assertThat(reportedRejected) // GH-90000
            .as("stats().totalRejected must match actual rejections")
            .isEqualTo(rejected.get()); // GH-90000
    }

    @Test
    @DisplayName("concurrent offer and drain are mutually exclusive — no item is lost or duplicated")
    void concurrentOfferAndDrainAreConsistent() throws InterruptedException { // GH-90000
        // Fill buffer to exactly CAPACITY
        for (int i = 0; i < CAPACITY; i++) { // GH-90000
            assertThat(buffer.offer(newEntry())).isTrue(); // GH-90000
        }
        assertThat(buffer.size()).isEqualTo(CAPACITY); // GH-90000

        int drainThreads  = 5;
        int drainBatchSize = CAPACITY / drainThreads; // each drains equal share

        ExecutorService executor  = Executors.newFixedThreadPool(drainThreads); // GH-90000
        CountDownLatch  startGate = new CountDownLatch(1); // GH-90000
        List<List<EventEntry>> drainedBatches = new ArrayList<>(); // GH-90000
        for (int i = 0; i < drainThreads; i++) drainedBatches.add(new ArrayList<>()); // GH-90000
        List<List<EventEntry>> captured = Collections.synchronizedList(drainedBatches); // GH-90000

        for (int t = 0; t < drainThreads; t++) { // GH-90000
            final int idx = t;
            executor.submit(() -> { // GH-90000
                try { startGate.await(); } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
                captured.get(idx).addAll(buffer.drain(drainBatchSize)); // GH-90000
            });
        }

        startGate.countDown(); // GH-90000
        executor.shutdown(); // GH-90000
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); // GH-90000

        // Total drained should equal CAPACITY; no item survives in the buffer
        int totalDrained = captured.stream().mapToInt(List::size).sum(); // GH-90000
        assertThat(totalDrained) // GH-90000
            .as("total drained events must equal capacity (each item drained exactly once)")
            .isEqualTo(CAPACITY); // GH-90000
        assertThat(buffer.size()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("utilizationPct stat stays within [0, 100] at all times")
    void utilizationPctStatIsAlwaysInRange() throws InterruptedException { // GH-90000
        ExecutorService offerers = Executors.newFixedThreadPool(4); // GH-90000
        ExecutorService drainers = Executors.newFixedThreadPool(2); // GH-90000
        CountDownLatch done = new CountDownLatch(6); // GH-90000

        for (int t = 0; t < 4; t++) { // GH-90000
            offerers.submit(() -> { // GH-90000
                try {
                    for (int i = 0; i < 100; i++) { // GH-90000
                        buffer.offer(newEntry()); // GH-90000
                    }
                } finally {
                    done.countDown(); // GH-90000
                }
            });
        }
        for (int t = 0; t < 2; t++) { // GH-90000
            drainers.submit(() -> { // GH-90000
                try {
                    for (int i = 0; i < 50; i++) { // GH-90000
                        buffer.drain(5); // GH-90000
                    }
                } finally {
                    done.countDown(); // GH-90000
                }
            });
        }

        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue(); // GH-90000

        Map<String, Object> stats = buffer.stats(); // GH-90000
        int utilizationPct = (Integer) stats.get("utilizationPct");
        assertThat(utilizationPct).isBetween(0, 100); // GH-90000

        offerers.shutdown(); // GH-90000
        drainers.shutdown(); // GH-90000
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static EventEntry newEntry() { // GH-90000
        return EventEntry.builder() // GH-90000
            .eventId(UUID.randomUUID()) // GH-90000
            .eventType("test.event")
            .payload(ByteBuffer.wrap("{}".getBytes())) // GH-90000
            .headers(Map.of()) // GH-90000
            .build(); // GH-90000
    }
}
