/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real-time JVM memory usage monitor for AEP (AEP-005.1).
 *
 * <p>Samples heap and non-heap memory at a configurable interval and exposes
 * the latest snapshot via {@link #currentSnapshot()}.  Listeners may be
 * registered to react to high-watermark crossings (e.g., for alerting).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepMemoryMonitor monitor = AepMemoryMonitor.builder()
 *     .samplingInterval(Duration.ofSeconds(5))
 *     .heapWarningThreshold(0.80)
 *     .build();
 *
 * monitor.start();
 * AepMemoryMonitor.MemorySnapshot snap = monitor.currentSnapshot();
 * monitor.stop();
 * }</pre>
 *
 * @doc.type    class
 * @doc.purpose Real-time JVM memory usage sampling and threshold alerting
 * @doc.layer   product
 * @doc.pattern Observer, Monitor
 */
public final class AepMemoryMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(AepMemoryMonitor.class);

    private final MemoryMXBean memoryBean;
    private final long samplingIntervalMs;
    private final double heapWarningThreshold;

    private final AtomicReference<MemorySnapshot> latest = new AtomicReference<>(MemorySnapshot.EMPTY);
    private final AtomicLong sampleCount = new AtomicLong(0);

    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> task;

    private AepMemoryMonitor(Builder builder) {
        this.memoryBean           = builder.memoryBean;
        this.samplingIntervalMs   = builder.samplingIntervalMs;
        this.heapWarningThreshold = builder.heapWarningThreshold;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts background sampling.  Idempotent — safe to call multiple times.
     */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aep-memory-monitor");
            t.setDaemon(true);
            return t;
        });
        task = scheduler.scheduleAtFixedRate(this::sample,
                0, samplingIntervalMs, TimeUnit.MILLISECONDS);
        LOG.info("AepMemoryMonitor started (interval={}ms, heapThreshold={:.0f}%)",
                samplingIntervalMs, heapWarningThreshold * 100);
    }

    /**
     * Stops background sampling.  Idempotent — safe to call multiple times.
     */
    public void stop() {
        if (task != null) {
            task.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
        LOG.info("AepMemoryMonitor stopped after {} samples", sampleCount.get());
    }

    // ── Sampling ──────────────────────────────────────────────────────────────

    /** Takes a single memory sample and updates the stored snapshot. */
    public void sample() {
        try {
            MemoryUsage heap    = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();

            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            long totalGcCount  = gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
            long totalGcTimeMs = gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();

            double heapRatio = heap.getMax() > 0
                    ? (double) heap.getUsed() / heap.getMax() : 0.0;

            MemorySnapshot snap = new MemorySnapshot(
                    Instant.now(),
                    heap.getUsed(),
                    heap.getMax(),
                    heap.getCommitted(),
                    nonHeap.getUsed(),
                    nonHeap.getMax(),
                    heapRatio,
                    totalGcCount,
                    totalGcTimeMs
            );

            latest.set(snap);
            sampleCount.incrementAndGet();

            if (heapRatio >= heapWarningThreshold) {
                LOG.warn("High heap usage: {:.1f}% ({} MB used / {} MB max)",
                        heapRatio * 100,
                        heap.getUsed() / (1024 * 1024),
                        heap.getMax() / (1024 * 1024));
            } else {
                LOG.debug("Memory sample: heap={:.1f}%, nonHeap={} MB",
                        heapRatio * 100, nonHeap.getUsed() / (1024 * 1024));
            }
        } catch (Exception e) {
            LOG.error("Failed to sample memory: {}", e.getMessage(), e);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the most recent memory snapshot.
     *
     * @return latest snapshot; never {@code null}
     */
    public MemorySnapshot currentSnapshot() {
        return latest.get();
    }

    /** Returns total number of samples taken since {@link #start()}. */
    public long sampleCount() {
        return sampleCount.get();
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Immutable point-in-time memory snapshot.
     *
     * @param timestamp        when the sample was taken
     * @param heapUsedBytes    current heap usage in bytes
     * @param heapMaxBytes     maximum heap in bytes (-1 if undefined)
     * @param heapCommittedBytes committed heap in bytes
     * @param nonHeapUsedBytes non-heap (metaspace + code cache) usage
     * @param nonHeapMaxBytes  non-heap maximum (-1 if undefined)
     * @param heapRatio        heap used / heap max [0, 1]; 0 if max undefined
     * @param gcCollections    cumulative GC collection count
     * @param gcTimeMs         cumulative GC time in milliseconds
     */
    public record MemorySnapshot(
            Instant timestamp,
            long heapUsedBytes,
            long heapMaxBytes,
            long heapCommittedBytes,
            long nonHeapUsedBytes,
            long nonHeapMaxBytes,
            double heapRatio,
            long gcCollections,
            long gcTimeMs
    ) {
        static final MemorySnapshot EMPTY = new MemorySnapshot(
                Instant.EPOCH, 0, 0, 0, 0, 0, 0.0, 0, 0);

        /** Returns {@code true} if this is the initial empty sentinel snapshot. */
        public boolean isEmpty() { return this == EMPTY; }

        /** Returns heap used in megabytes. */
        public double heapUsedMb() { return heapUsedBytes / (1024.0 * 1024); }

        /** Returns heap max in megabytes. */
        public double heapMaxMb() { return heapMaxBytes / (1024.0 * 1024); }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Returns a new builder for {@link AepMemoryMonitor}.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AepMemoryMonitor}.
     */
    public static final class Builder {
        private MemoryMXBean memoryBean          = ManagementFactory.getMemoryMXBean();
        private long samplingIntervalMs          = 5_000;
        private double heapWarningThreshold      = 0.80;

        private Builder() {}

        /**
         * Sampling interval in milliseconds (must be &gt; 0).
         *
         * @param intervalMs positive interval in milliseconds
         * @return this builder
         */
        public Builder samplingIntervalMs(long intervalMs) {
            if (intervalMs <= 0) throw new IllegalArgumentException("samplingIntervalMs must be positive");
            this.samplingIntervalMs = intervalMs;
            return this;
        }

        /**
         * Heap ratio threshold above which a warning is logged [0, 1].
         *
         * @param threshold value in [0, 1]
         * @return this builder
         */
        public Builder heapWarningThreshold(double threshold) {
            if (threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException("heapWarningThreshold must be in [0, 1]");
            }
            this.heapWarningThreshold = threshold;
            return this;
        }

        /**
         * Injectable {@link MemoryMXBean} for testing.
         *
         * @param bean memory bean
         * @return this builder
         */
        public Builder memoryBean(MemoryMXBean bean) {
            this.memoryBean = java.util.Objects.requireNonNull(bean, "bean must not be null");
            return this;
        }

        /** Builds the configured monitor. */
        public AepMemoryMonitor build() {
            return new AepMemoryMonitor(this);
        }
    }
}
