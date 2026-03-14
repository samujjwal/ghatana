/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Polls the transactional outbox table and delivers unpublished events to the
 * message broker (K17-002/003).
 *
 * <p>The relay uses a single-threaded daemon scheduler and delegates to an
 * {@link OutboxEventPublisher} for the actual message delivery. The scheduler
 * polls every {@code pollIntervalMillis} milliseconds by default.
 *
 * <p>Exactly-once delivery is approximated via:
 * <ol>
 *   <li>{@code SELECT FOR UPDATE SKIP LOCKED} — safe concurrent relay instances</li>
 *   <li>Mark published only after the publisher confirms delivery</li>
 *   <li>Increment attempt counter on failure (max 5 attempts)</li>
 * </ol>
 *
 * <p>Integration: wire to K-05 EventBus by implementing {@link OutboxEventPublisher}.
 *
 * @doc.type class
 * @doc.purpose Polls outbox table and delivers events to message broker (K17-002/003)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int DEFAULT_BATCH_SIZE = 50;

    private final OutboxPort outboxPort;
    private final OutboxEventPublisher publisher;
    private final int batchSize;
    private final ScheduledExecutorService scheduler;

    private final AtomicLong totalPublished = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();

    /**
     * Creates a relay with default batch size (50).
     *
     * @param outboxPort outbox storage port
     * @param publisher  event delivery implementation
     */
    public OutboxRelay(OutboxPort outboxPort, OutboxEventPublisher publisher) {
        this(outboxPort, publisher, DEFAULT_BATCH_SIZE);
    }

    /**
     * Creates a relay with a custom batch size.
     *
     * @param outboxPort  outbox storage port
     * @param publisher   event delivery implementation
     * @param batchSize   maximum number of entries processed per poll cycle
     */
    public OutboxRelay(OutboxPort outboxPort, OutboxEventPublisher publisher, int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        this.outboxPort = outboxPort;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-relay");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the polling relay at the given interval.
     *
     * @param pollIntervalMillis milliseconds between poll cycles
     */
    public void start(long pollIntervalMillis) {
        LOG.info("OutboxRelay starting, pollInterval={}ms, batchSize={}", pollIntervalMillis, batchSize);
        scheduler.scheduleWithFixedDelay(
                this::processBatch,
                0,
                pollIntervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    /** Shuts down the relay gracefully (waits up to 5 seconds for current batch). */
    public void stop() {
        LOG.info("OutboxRelay stopping");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Total number of entries successfully published since relay started. */
    public long getTotalPublished() {
        return totalPublished.get();
    }

    /** Total number of delivery failures since relay started. */
    public long getTotalFailed() {
        return totalFailed.get();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void processBatch() {
        try {
            List<OutboxEntry> entries = outboxPort.fetchUnpublished(batchSize)
                    .getResult();   // blocking inside daemon thread, not in eventloop
            if (entries == null || entries.isEmpty()) return;

            LOG.debug("OutboxRelay processing {} entries", entries.size());

            List<UUID> published = entries.stream()
                    .filter(OutboxEntry::isEligibleForDelivery)
                    .filter(this::deliverSafely)
                    .map(OutboxEntry::id)
                    .collect(Collectors.toList());

            if (!published.isEmpty()) {
                outboxPort.markPublished(published).getResult();
                totalPublished.addAndGet(published.size());
            }

        } catch (Exception e) {
            LOG.error("OutboxRelay poll cycle failed", e);
        }
    }

    private boolean deliverSafely(OutboxEntry entry) {
        try {
            publisher.publish(entry);
            return true;
        } catch (Exception e) {
            totalFailed.incrementAndGet();
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            outboxPort.markFailed(entry.id(), msg).getResult();
            LOG.warn("OutboxRelay failed to deliver entry {}: {}", entry.id(), msg);
            return false;
        }
    }
}
