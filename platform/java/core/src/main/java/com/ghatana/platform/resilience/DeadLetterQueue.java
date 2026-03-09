/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Dead Letter Queue for events that failed processing.
 *
 * <p>Provides bounded, TTL-governed storage with indexing by error type, event type,
 * and time range. Supports replay of individual failed events back through an operator.</p>
 *
 * <p>Thread-safe via concurrent collections. Designed for single-JVM operation;
 * for distributed DLQ, back with a persistent store and use this as the in-memory layer.</p>
 *
 * <p>Usage:
 * <pre>
 * DeadLetterQueue dlq = DeadLetterQueue.builder()
 *     .maxSize(10_000)
 *     .ttl(Duration.ofDays(7))
 *     .enableReplay(true)
 *     .build();
 *
 * dlq.store(event, exception, "enrichment-timeout");
 * List&lt;FailedEvent&gt; recent = dlq.getByTimeRange(startTime, endTime);
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Bounded, TTL-governed dead letter queue for events that failed processing
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DeadLetterQueue {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueue.class);

    private final int maxSize;
    private final Duration ttl;
    private final boolean enableReplay;

    private final ConcurrentLinkedQueue<FailedEvent> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, FailedEvent> index = new ConcurrentHashMap<>();
    private final AtomicLong totalStored = new AtomicLong(0);
    private final AtomicLong totalEvicted = new AtomicLong(0);
    private final AtomicLong totalReplayed = new AtomicLong(0);

    private DeadLetterQueue(Builder builder) {
        this.maxSize = builder.maxSize;
        this.ttl = builder.ttl;
        this.enableReplay = builder.enableReplay;
    }

    /**
     * Store a failed event in the dead letter queue.
     *
     * @param event    the event that failed (opaque payload)
     * @param error    the exception that caused the failure
     * @param reason   human-readable failure reason
     * @return the generated failed event ID
     */
    public String store(Object event, Throwable error, String reason) {
        removeExpired();

        // Enforce size limit — evict oldest if full
        while (queue.size() >= maxSize) {
            FailedEvent evicted = queue.poll();
            if (evicted != null) {
                index.remove(evicted.getId());
                totalEvicted.incrementAndGet();
            }
        }

        FailedEvent failedEvent = new FailedEvent(event, error, reason);
        queue.add(failedEvent);
        index.put(failedEvent.getId(), failedEvent);
        totalStored.incrementAndGet();

        log.debug("DLQ stored event {} (reason: {}, queue size: {})",
                failedEvent.getId(), reason, queue.size());

        return failedEvent.getId();
    }

    /**
     * Retrieve a specific failed event by ID.
     */
    public Optional<FailedEvent> get(String id) {
        return Optional.ofNullable(index.get(id));
    }

    /**
     * Get all failed events currently in the queue (snapshot).
     */
    public List<FailedEvent> getAll() {
        removeExpired();
        return List.copyOf(queue);
    }

    /**
     * Get failed events within a time range.
     */
    public List<FailedEvent> getByTimeRange(Instant from, Instant to) {
        return queue.stream()
                .filter(e -> !e.getFailedAt().isBefore(from) && !e.getFailedAt().isAfter(to))
                .collect(Collectors.toList());
    }

    /**
     * Get failed events matching a specific error type.
     */
    public List<FailedEvent> getByErrorType(String errorType) {
        return queue.stream()
                .filter(e -> errorType.equals(e.getErrorType()))
                .collect(Collectors.toList());
    }

    /**
     * Remove a specific event from the DLQ (e.g., after manual resolution).
     */
    public boolean remove(String id) {
        FailedEvent removed = index.remove(id);
        if (removed != null) {
            queue.remove(removed);
            return true;
        }
        return false;
    }

    /**
     * Clear all events from the DLQ.
     */
    public void clear() {
        queue.clear();
        index.clear();
    }

    /**
     * Remove events that have exceeded their TTL.
     */
    public int removeExpired() {
        if (ttl == null || ttl.isZero()) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(ttl);
        int removed = 0;
        Iterator<FailedEvent> it = queue.iterator();
        while (it.hasNext()) {
            FailedEvent event = it.next();
            if (event.getFailedAt().isBefore(cutoff)) {
                it.remove();
                index.remove(event.getId());
                totalEvicted.incrementAndGet();
                removed++;
            }
        }
        return removed;
    }

    public int size() {
        return queue.size();
    }

    public boolean isReplayEnabled() {
        return enableReplay;
    }

    public long getTotalStored() { return totalStored.get(); }
    public long getTotalEvicted() { return totalEvicted.get(); }
    public long getTotalReplayed() { return totalReplayed.get(); }

    // ────────────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxSize = 10_000;
        private Duration ttl = Duration.ofDays(7);
        private boolean enableReplay = true;

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder enableReplay(boolean enableReplay) {
            this.enableReplay = enableReplay;
            return this;
        }

        public DeadLetterQueue build() {
            return new DeadLetterQueue(this);
        }
    }

    // ────────────────────────────────────────────────────────────────────

    /**
     * Wrapper for a failed event, carrying the original payload, error context,
     * and metadata for debugging and replay.
     */
    public static final class FailedEvent {
        private final String id;
        private final Object originalEvent;
        private final String errorMessage;
        private final String errorType;
        private final String stackTrace;
        private final String reason;
        private final Instant failedAt;

        FailedEvent(Object originalEvent, Throwable error, String reason) {
            this.id = UUID.randomUUID().toString();
            this.originalEvent = originalEvent;
            this.errorMessage = error != null ? error.getMessage() : "unknown";
            this.errorType = error != null ? error.getClass().getSimpleName() : "Unknown";
            this.stackTrace = formatStackTrace(error);
            this.reason = reason;
            this.failedAt = Instant.now();
        }

        public String getId() { return id; }
        public Object getOriginalEvent() { return originalEvent; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorType() { return errorType; }
        public String getStackTrace() { return stackTrace; }
        public String getReason() { return reason; }
        public Instant getFailedAt() { return failedAt; }

        private static String formatStackTrace(Throwable error) {
            if (error == null) return "";
            java.io.StringWriter sw = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(sw));
            return sw.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FailedEvent that)) return false;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
