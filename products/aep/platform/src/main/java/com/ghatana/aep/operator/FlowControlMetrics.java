/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.aep.operator;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregated flow-control and reliability metrics for AEP operator pipelines.
 *
 * <p>Collects metrics from {@link BackpressureOperator},
 * {@link RateLimitingOperator}, {@link DeduplicationOperator}, and
 * {@link DeadLetterOperator} into a single snapshot suitable for export
 * to the platform {@code observability} module.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FlowControlMetrics metrics = new FlowControlMetrics("my-pipeline");
 * metrics.recordProcessed();
 * metrics.recordDropped();
 * Map<String, Object> snapshot = metrics.snapshot();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Aggregated flow-control metrics for AEP operator pipelines
 * @doc.layer product-aep
 * @doc.pattern Metrics Collector
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class FlowControlMetrics {

    private final String pipelineId;
    private final Instant startedAt;

    // Counters
    private final AtomicLong totalReceived = new AtomicLong();
    private final AtomicLong totalProcessed = new AtomicLong();
    private final AtomicLong totalDropped = new AtomicLong();
    private final AtomicLong totalDeduplicated = new AtomicLong();
    private final AtomicLong totalRateLimited = new AtomicLong();
    private final AtomicLong totalDeadLettered = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();

    // Latency tracking (nanoseconds)
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private final AtomicLong maxLatencyNanos = new AtomicLong();

    public FlowControlMetrics(String pipelineId) {
        this.pipelineId = pipelineId;
        this.startedAt = Instant.now();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Recording
    // ═══════════════════════════════════════════════════════════════════════════

    public void recordReceived() { totalReceived.incrementAndGet(); }
    public void recordProcessed() { totalProcessed.incrementAndGet(); }
    public void recordDropped() { totalDropped.incrementAndGet(); }
    public void recordDeduplicated() { totalDeduplicated.incrementAndGet(); }
    public void recordRateLimited() { totalRateLimited.incrementAndGet(); }
    public void recordDeadLettered() { totalDeadLettered.incrementAndGet(); }
    public void recordError() { totalErrors.incrementAndGet(); }

    public void recordLatency(long nanos) {
        totalLatencyNanos.addAndGet(nanos);
        maxLatencyNanos.updateAndGet(current -> Math.max(current, nanos));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═══════════════════════════════════════════════════════════════════════════

    public String getPipelineId() { return pipelineId; }
    public long getTotalReceived() { return totalReceived.get(); }
    public long getTotalProcessed() { return totalProcessed.get(); }
    public long getTotalDropped() { return totalDropped.get(); }
    public long getTotalDeduplicated() { return totalDeduplicated.get(); }
    public long getTotalRateLimited() { return totalRateLimited.get(); }
    public long getTotalDeadLettered() { return totalDeadLettered.get(); }
    public long getTotalErrors() { return totalErrors.get(); }

    public double getAverageLatencyMs() {
        long processed = totalProcessed.get();
        return processed > 0
                ? (double) totalLatencyNanos.get() / processed / 1_000_000.0
                : 0.0;
    }

    public double getMaxLatencyMs() {
        return maxLatencyNanos.get() / 1_000_000.0;
    }

    public double getErrorRate() {
        long received = totalReceived.get();
        return received > 0 ? (double) totalErrors.get() / received : 0.0;
    }

    public double getThroughputPerSecond() {
        Duration uptime = Duration.between(startedAt, Instant.now());
        long seconds = uptime.toSeconds();
        return seconds > 0 ? (double) totalProcessed.get() / seconds : 0.0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Snapshot
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns a point-in-time snapshot of all metrics as a map suitable
     * for serialisation or export to the observability module.
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("pipelineId", pipelineId);
        snap.put("startedAt", startedAt.toString());
        snap.put("totalReceived", totalReceived.get());
        snap.put("totalProcessed", totalProcessed.get());
        snap.put("totalDropped", totalDropped.get());
        snap.put("totalDeduplicated", totalDeduplicated.get());
        snap.put("totalRateLimited", totalRateLimited.get());
        snap.put("totalDeadLettered", totalDeadLettered.get());
        snap.put("totalErrors", totalErrors.get());
        snap.put("avgLatencyMs", getAverageLatencyMs());
        snap.put("maxLatencyMs", getMaxLatencyMs());
        snap.put("errorRate", getErrorRate());
        snap.put("throughputPerSec", getThroughputPerSecond());
        return snap;
    }

    /**
     * Resets all counters. Typically used after exporting metrics.
     */
    public void reset() {
        totalReceived.set(0);
        totalProcessed.set(0);
        totalDropped.set(0);
        totalDeduplicated.set(0);
        totalRateLimited.set(0);
        totalDeadLettered.set(0);
        totalErrors.set(0);
        totalLatencyNanos.set(0);
        maxLatencyNanos.set(0);
    }
}
