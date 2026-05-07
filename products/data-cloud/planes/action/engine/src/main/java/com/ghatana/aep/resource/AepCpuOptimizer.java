/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CPU utilization monitor and optimization advisor for AEP (AEP-005.2).
 *
 * <p>Samples JVM and system-wide CPU load at configurable intervals.  When CPU
 * utilization consistently exceeds the configured ceiling the advisor emits a
 * structured log and sets a throttle flag that callers can inspect to shed load.
 *
 * <p>Target: CPU utilization &lt;80% under normal load.
 *
 * @doc.type    class
 * @doc.purpose CPU utilization sampling and load-shedding advisor
 * @doc.layer   product
 * @doc.pattern Monitor, Advisor
 */
public final class AepCpuOptimizer {

    private static final Logger LOG = LoggerFactory.getLogger(AepCpuOptimizer.class);

    private final OperatingSystemMXBean osBean;
    private final long samplingIntervalMs;
    private final double highCpuThreshold;
    private final int consecutiveHighSamplesBeforeThrottle;

    private final AtomicReference<CpuSnapshot> latest = new AtomicReference<>(CpuSnapshot.EMPTY);
    private final AtomicLong sampleCount           = new AtomicLong(0);
    private final AtomicLong consecutiveHighCount  = new AtomicLong(0);
    private volatile boolean throttleActive        = false;

    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> task;

    private AepCpuOptimizer(Builder builder) {
        this.osBean                              = builder.osBean;
        this.samplingIntervalMs                  = builder.samplingIntervalMs;
        this.highCpuThreshold                    = builder.highCpuThreshold;
        this.consecutiveHighSamplesBeforeThrottle = builder.consecutiveHighSamplesBeforeThrottle;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Starts background CPU sampling. Idempotent. */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aep-cpu-optimizer");
            t.setDaemon(true);
            return t;
        });
        task = scheduler.scheduleAtFixedRate(this::sample,
                0, samplingIntervalMs, TimeUnit.MILLISECONDS);
        LOG.info("AepCpuOptimizer started (interval={}ms, highThreshold={:.0f}%)",
                samplingIntervalMs, highCpuThreshold * 100);
    }

    /** Stops background sampling. Idempotent. */
    public void stop() {
        if (task != null) task.cancel(false);
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS))
                    scheduler.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
        LOG.info("AepCpuOptimizer stopped");
    }

    // ── Sampling ──────────────────────────────────────────────────────────────

    /** Takes a single CPU sample. */
    public void sample() {
        try {
            double processCpu = getProcessCpuLoad();
            double systemCpu  = osBean.getSystemLoadAverage() / osBean.getAvailableProcessors();
            if (systemCpu < 0) systemCpu = processCpu; // fallback when unavailable

            CpuSnapshot snap = new CpuSnapshot(Instant.now(), processCpu, systemCpu,
                    osBean.getAvailableProcessors(), throttleActive);
            latest.set(snap);
            sampleCount.incrementAndGet();

            if (processCpu >= highCpuThreshold) {
                long consecutive = consecutiveHighCount.incrementAndGet();
                LOG.warn("High CPU utilization: process={:.1f}%, system={:.1f}%, consecutiveSamples={}",
                        processCpu * 100, systemCpu * 100, consecutive);
                if (consecutive >= consecutiveHighSamplesBeforeThrottle && !throttleActive) {
                    throttleActive = true;
                    LOG.warn("CPU throttle ACTIVATED after {} consecutive high samples", consecutive);
                }
            } else {
                consecutiveHighCount.set(0);
                if (throttleActive) {
                    throttleActive = false;
                    LOG.info("CPU throttle DEACTIVATED — utilization normalized to {:.1f}%",
                            processCpu * 100);
                }
                LOG.debug("CPU sample: process={:.1f}%, system={:.1f}%",
                        processCpu * 100, systemCpu * 100);
            }
        } catch (Exception e) {
            LOG.error("CPU sampling failed: {}", e.getMessage(), e);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the most recent CPU snapshot.
     *
     * @return latest snapshot
     */
    public CpuSnapshot currentSnapshot() { return latest.get(); }

    /**
     * Returns {@code true} when CPU has been consistently above the high threshold
     * and callers should shed non-critical load.
     *
     * @return throttle flag
     */
    public boolean isThrottleActive() { return throttleActive; }

    /** Returns total samples taken since start. */
    public long sampleCount() { return sampleCount.get(); }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private double getProcessCpuLoad() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            double load = sunBean.getProcessCpuLoad();
            return load < 0 ? 0.0 : load;
        }
        return 0.0;
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Immutable CPU utilization snapshot.
     *
     * @param timestamp         when the sample was taken
     * @param processCpuLoad    fraction [0, 1] of CPU used by this JVM process
     * @param systemCpuLoad     fraction [0, 1] of system-wide CPU utilization
     * @param availableProcessors number of logical CPU cores
     * @param throttleActive    whether load-shedding is currently active
     */
    public record CpuSnapshot(
            Instant timestamp,
            double processCpuLoad,
            double systemCpuLoad,
            int availableProcessors,
            boolean throttleActive
    ) {
        static final CpuSnapshot EMPTY = new CpuSnapshot(Instant.EPOCH, 0, 0, 1, false);

        /** Returns {@code true} if process CPU is below the 80% target. */
        public boolean meetsTarget() { return processCpuLoad < 0.80; }

        /** Returns process CPU percentage (0–100). */
        public double processCpuPercent() { return processCpuLoad * 100; }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Returns a new builder for {@link AepCpuOptimizer}.
     *
     * @return builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link AepCpuOptimizer}.
     */
    public static final class Builder {
        private OperatingSystemMXBean osBean                       = ManagementFactory.getOperatingSystemMXBean();
        private long samplingIntervalMs                             = 5_000;
        private double highCpuThreshold                             = 0.80;
        private int consecutiveHighSamplesBeforeThrottle            = 3;

        private Builder() {}

        public Builder samplingIntervalMs(long ms) {
            if (ms <= 0) throw new IllegalArgumentException("samplingIntervalMs must be positive");
            this.samplingIntervalMs = ms;
            return this;
        }

        public Builder highCpuThreshold(double threshold) {
            if (threshold < 0 || threshold > 1)
                throw new IllegalArgumentException("highCpuThreshold must be in [0, 1]");
            this.highCpuThreshold = threshold;
            return this;
        }

        public Builder consecutiveHighSamplesBeforeThrottle(int count) {
            if (count <= 0)
                throw new IllegalArgumentException("count must be positive");
            this.consecutiveHighSamplesBeforeThrottle = count;
            return this;
        }

        /** Injectable OS bean for testing. */
        public Builder osBean(OperatingSystemMXBean bean) {
            this.osBean = java.util.Objects.requireNonNull(bean, "bean must not be null");
            return this;
        }

        public AepCpuOptimizer build() { return new AepCpuOptimizer(this); }
    }
}
