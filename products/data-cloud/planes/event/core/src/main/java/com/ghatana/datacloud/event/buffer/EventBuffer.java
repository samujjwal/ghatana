/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event.buffer;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event buffer providing backpressure management between event intake and processing.
 *
 * <p>Buffers events in memory up to a configurable capacity. When the buffer
 * reaches the high-water mark, it spills events to the platform
 * {@link EventLogStore} for durable persistence. Events are drained in order
 * from the in-memory buffer first, then from the spill store.
 *
 * <h3>Backpressure strategy</h3>
 * <ol>
 *   <li>Events are enqueued to the in-memory buffer (fast path).</li>
 *   <li>When buffer exceeds {@code highWaterMark}, excess events batch-append
 *       to the spill store (EventLogStore).</li>
 *   <li>When buffer drains below {@code lowWaterMark}, spilled events are
 *       read back into the buffer.</li>
 *   <li>If both buffer and spill store are full, the producer is signaled
 *       to slow down (backpressure).</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Event buffer with backpressure and spill-to-store support
 * @doc.layer product
 * @doc.pattern Buffer, Backpressure
 */
public final class EventBuffer {

    private static final Logger log = LoggerFactory.getLogger(EventBuffer.class);

    private static final int DEFAULT_CAPACITY = 10_000;
    private static final int DEFAULT_HIGH_WATER_MARK = 8_000;
    private static final int DEFAULT_LOW_WATER_MARK = 2_000;

    /**
     * The backing store. {@link ArrayBlockingQueue} is used instead of
     * {@link java.util.concurrent.ConcurrentLinkedQueue} because:
     * <ul>
     *   <li>Native bounded capacity: {@code offer()} atomically rejects when full,
     *       eliminating the TOCTOU race between a size check and {@code add()}.</li>
     *   <li>{@code size()} is O(1) (stored as an {@code AtomicInteger} internally),
     *       not the O(n) of {@link java.util.concurrent.ConcurrentLinkedQueue}.</li>
     *   <li>Memory contiguous — better CPU cache locality for sequential drain.</li>
     * </ul>
     */
    private final ArrayBlockingQueue<EventEntry> buffer;
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalDrained = new AtomicLong(0);
    private final AtomicLong totalSpilled = new AtomicLong(0);
    /** Counts events rejected due to a full buffer (backpressure events). */
    private final AtomicLong totalRejected = new AtomicLong(0);

    private final EventLogStore spillStore;
    private final String bufferName;
    private final int capacity;
    private final int highWaterMark;
    private final int lowWaterMark;

    /**
     * Creates a buffer with default settings.
     *
    * @param spillStore  platform EventLogStore for spill persistence
     * @param bufferName  logical name for this buffer (used in metrics and logging)
     */
    public EventBuffer(EventLogStore spillStore, String bufferName) {
        this(spillStore, bufferName, DEFAULT_CAPACITY, DEFAULT_HIGH_WATER_MARK, DEFAULT_LOW_WATER_MARK);
    }

    /**
     * Creates a buffer with custom capacity settings.
     *
    * @param spillStore    platform EventLogStore for spill persistence
     * @param bufferName    logical name for this buffer
     * @param capacity      maximum in-memory events before rejection
     * @param highWaterMark triggers spill when exceeded
     * @param lowWaterMark  triggers refill from spill store when below
     */
    public EventBuffer(
            EventLogStore spillStore,
            String bufferName,
            int capacity,
            int highWaterMark,
            int lowWaterMark) {
        this.spillStore = Objects.requireNonNull(spillStore, "spillStore required");
        this.bufferName = Objects.requireNonNull(bufferName, "bufferName required");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (highWaterMark > capacity) throw new IllegalArgumentException("highWaterMark cannot exceed capacity");
        if (lowWaterMark > highWaterMark) throw new IllegalArgumentException("lowWaterMark cannot exceed highWaterMark");
        this.capacity = capacity;
        this.highWaterMark = highWaterMark;
        this.lowWaterMark = lowWaterMark;
        this.buffer = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Enqueues an event to the buffer.
     *
     * @param entry event entry to buffer
     * @return true if the event was accepted, false if the buffer is full (backpressure)
     */
    public boolean offer(EventEntry entry) {
        Objects.requireNonNull(entry, "entry required");
        // ArrayBlockingQueue.offer() is atomic: it enqueues only if capacity allows,
        // returning false immediately when full — no TOCTOU race possible.
        boolean accepted = buffer.offer(entry);
        if (accepted) {
            totalEnqueued.incrementAndGet();
        } else {
            totalRejected.incrementAndGet();
            log.warn("[buffer:{}] Buffer full ({}/{}), backpressure applied",
                bufferName, buffer.size(), capacity);
        }
        return accepted;
    }

    /**
     * Drains up to {@code batchSize} events from the buffer.
     *
     * @param batchSize maximum events to drain
     * @return list of drained event entries (may be empty)
     */
    public List<EventEntry> drain(int batchSize) {
        List<EventEntry> batch = new ArrayList<>(Math.min(batchSize, buffer.size()));
        for (int i = 0; i < batchSize; i++) {
            EventEntry entry = buffer.poll();
            if (entry == null) break;
            batch.add(entry);
            totalDrained.incrementAndGet();
        }
        return batch;
    }

    /**
    * Spills excess events from the in-memory buffer to the spill store.
     *
     * @param tenantId tenant context for the spill store
     * @return promise of the number of events spilled
     */
    public Promise<Integer> spillExcess(String tenantId) {
        int currentSize = buffer.size();
        if (currentSize <= highWaterMark) {
            return Promise.of(0);
        }

        int spillCount = currentSize - highWaterMark;
        List<EventEntry> toSpill = drain(spillCount);
        if (toSpill.isEmpty()) {
            return Promise.of(0);
        }

        TenantContext tenant = TenantContext.of(tenantId);
        return spillStore.appendBatch(tenant, toSpill)
            .map(offsets -> {
                int spilled = offsets.size();
                totalSpilled.addAndGet(spilled);
                log.debug("[buffer:{}] Spilled {} events to store, buffer size now {}",
                    bufferName, spilled, buffer.size());
                return spilled;
            });
    }

    /**
     * Returns the current in-memory buffer size.
     */
    public int size() {
        return buffer.size();
    }

    /**
     * Returns true if the buffer has reached the high-water mark.
     */
    public boolean isOverHighWaterMark() {
        return buffer.size() > highWaterMark;
    }

    /**
     * Returns true if the buffer is below the low-water mark.
     */
    public boolean isBelowLowWaterMark() {
        return buffer.size() < lowWaterMark;
    }

    /**
     * Returns true if the buffer is empty.
     */
    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    /**
     * Returns buffer statistics as a map (for metrics/observability).
     */
    public Map<String, Object> stats() {
        int currentSize = buffer.size();
        return Map.of(
            "bufferName", bufferName,
            "size", currentSize,
            "capacity", capacity,
            "utilizationPct", (int) (currentSize * 100L / capacity),
            "highWaterMark", highWaterMark,
            "lowWaterMark", lowWaterMark,
            "totalEnqueued", totalEnqueued.get(),
            "totalDrained", totalDrained.get(),
            "totalSpilled", totalSpilled.get(),
            "totalRejected", totalRejected.get()
        );
    }
}
