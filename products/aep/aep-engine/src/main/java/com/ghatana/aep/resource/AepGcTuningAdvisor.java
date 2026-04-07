/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * GC behavior advisor for AEP (AEP-005.3).
 *
 * <p>Inspects the running JVM's GC collectors and computes a GC pause overhead
 * metric.  When overhead exceeds the configured threshold (default 10ms average
 * pause) the advisor emits a tuning recommendation.  Target: GC pause time &lt;10ms.
 *
 * <p>This class does not require background sampling — call {@link #analyze()}
 * on demand or wrap it in a scheduled task.
 *
 * @doc.type    class
 * @doc.purpose GC pause analysis and tuning advisor targeting &lt;10ms average pause
 * @doc.layer   product
 * @doc.pattern Advisor
 */
public final class AepGcTuningAdvisor {

    private static final Logger LOG = LoggerFactory.getLogger(AepGcTuningAdvisor.class);

    /** GC pause target in milliseconds (AEP-005.3). */
    public static final double TARGET_AVG_PAUSE_MS = 10.0;

    private final List<GarbageCollectorMXBean> gcBeans;
    private final double pauseTargetMs;

    private AepGcTuningAdvisor(Builder builder) {
        this.gcBeans      = builder.gcBeans;
        this.pauseTargetMs = builder.pauseTargetMs;
    }

    // ── Analysis ─────────────────────────────────────────────────────────────

    /**
     * Analyses current GC metrics and returns a recommendation.
     *
     * @return analysis result; never {@code null}
     */
    public GcAnalysis analyze() {
        long totalCollections = 0;
        long totalTimeMs      = 0;
        StringBuilder collectorInfo = new StringBuilder();

        for (GarbageCollectorMXBean gc : gcBeans) {
            long count = gc.getCollectionCount();
            long time  = gc.getCollectionTime();
            if (count > 0) {
                totalCollections += count;
                totalTimeMs      += time;
                collectorInfo.append(gc.getName()).append("(count=").append(count)
                        .append(",time=").append(time).append("ms) ");
            }
        }

        double avgPauseMs = totalCollections == 0 ? 0.0
                : (double) totalTimeMs / totalCollections;

        boolean meetsTarget = avgPauseMs <= pauseTargetMs;
        String recommendation = buildRecommendation(avgPauseMs, totalCollections);

        GcAnalysis analysis = new GcAnalysis(
                Instant.now(), totalCollections, totalTimeMs, avgPauseMs,
                meetsTarget, collectorInfo.toString().trim(), recommendation);

        if (!meetsTarget) {
            LOG.warn("GC avg pause {:.2f}ms exceeds target {:.0f}ms. {}",
                    avgPauseMs, pauseTargetMs, recommendation);
        } else {
            LOG.debug("GC avg pause {:.2f}ms — within target", avgPauseMs);
        }

        return analysis;
    }

    private String buildRecommendation(double avgPauseMs, long totalCollections) {
        if (avgPauseMs <= pauseTargetMs) {
            return "GC performance is within target — no tuning required.";
        }
        StringBuilder sb = new StringBuilder("Consider: ");
        if (avgPauseMs > pauseTargetMs * 5) {
            sb.append("(1) switch to ZGC or Shenandoah for lower-latency GC; ");
        }
        sb.append("(2) increase -Xmx to reduce GC frequency; ");
        sb.append("(3) tune -XX:G1HeapRegionSize or -XX:MaxGCPauseMillis=10 for G1GC; ");
        if (totalCollections > 1_000) {
            sb.append("(4) high GC frequency detected — investigate object allocation rate.");
        }
        return sb.toString();
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * GC analysis result.
     *
     * @param timestamp         when the analysis was performed
     * @param totalCollections  cumulative GC collection count
     * @param totalGcTimeMs     cumulative GC time in milliseconds
     * @param avgPauseMs        average GC pause per collection in milliseconds
     * @param meetsTarget       whether avg pause is within the configured target
     * @param collectorSummary  human-readable summary of active collectors
     * @param recommendation    tuning advice when target is not met
     */
    public record GcAnalysis(
            Instant timestamp,
            long totalCollections,
            long totalGcTimeMs,
            double avgPauseMs,
            boolean meetsTarget,
            String collectorSummary,
            String recommendation
    ) {}

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder for {@link AepGcTuningAdvisor}. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link AepGcTuningAdvisor}.
     */
    public static final class Builder {
        private List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        private double pauseTargetMs                  = TARGET_AVG_PAUSE_MS;

        private Builder() {}

        /**
         * GC pause target threshold in milliseconds.
         *
         * @param ms positive threshold
         * @return this builder
         */
        public Builder pauseTargetMs(double ms) {
            if (ms <= 0) throw new IllegalArgumentException("pauseTargetMs must be positive");
            this.pauseTargetMs = ms;
            return this;
        }

        /**
         * Injectable GC beans for testing.
         *
         * @param gcBeans list of GC beans
         * @return this builder
         */
        public Builder gcBeans(List<GarbageCollectorMXBean> gcBeans) {
            this.gcBeans = Objects.requireNonNull(gcBeans, "gcBeans must not be null");
            return this;
        }

        public AepGcTuningAdvisor build() { return new AepGcTuningAdvisor(this); }
    }
}

