/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Operator execution optimizer for AEP (AEP-006.2).
 *
 * <p>Records per-operator execution times and identifies bottlenecks.
 * Target: operator execution time &lt;10ms average.  Provides SLA violation
 * detection and optimization hints.
 *
 * @doc.type    class
 * @doc.purpose Per-operator execution time tracking and optimization advisor
 * @doc.layer   product
 * @doc.pattern Monitor, Advisor
 */
public final class OperatorExecutionOptimizer {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorExecutionOptimizer.class);

    /** Target average operator execution time in milliseconds (AEP-006.2). */
    public static final double TARGET_AVG_EXEC_MS = 10.0;

    private final double slaThresholdMs;
    private final int maxSamplesPerOperator;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> samples =
            new ConcurrentHashMap<>();
    private final AtomicLong totalViolations = new AtomicLong(0);

    private OperatorExecutionOptimizer(Builder builder) {
        this.slaThresholdMs        = builder.slaThresholdMs;
        this.maxSamplesPerOperator = builder.maxSamplesPerOperator;
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    /**
     * Records an operator execution time sample.
     *
     * @param operatorId unique operator identifier
     * @param durationNs execution duration in nanoseconds
     */
    public void record(String operatorId, long durationNs) {
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        if (durationNs < 0) throw new IllegalArgumentException("durationNs must not be negative");

        CopyOnWriteArrayList<Long> list = samples.computeIfAbsent(
                operatorId, k -> new CopyOnWriteArrayList<>());

        // Evict oldest when at capacity
        if (list.size() >= maxSamplesPerOperator) {
            list.remove(0);
        }
        list.add(durationNs);

        double ms = durationNs / 1_000_000.0;
        if (ms > slaThresholdMs) {
            totalViolations.incrementAndGet();
            LOG.warn("SLA violation operatorId={} execMs={:.2f} threshold={:.0f}ms",
                    operatorId, ms, slaThresholdMs);
        }
    }

    /**
     * Convenience method: records using a {@link Duration}.
     *
     * @param operatorId  operator identifier
     * @param duration    execution duration
     */
    public void record(String operatorId, Duration duration) {
        record(operatorId, duration.toNanos());
    }

    // ── Statistics ─────────────────────────────────────────────────────────────

    /**
     * Returns stats for a specific operator.
     *
     * @param operatorId operator identifier
     * @return stats; empty if no samples recorded
     */
    public OperatorStats statsFor(String operatorId) {
        CopyOnWriteArrayList<Long> list = samples.get(operatorId);
        if (list == null || list.isEmpty()) {
            return OperatorStats.empty(operatorId);
        }
        return computeStats(operatorId, List.copyOf(list));
    }

    /**
     * Returns stats for all operators that have at least one sample.
     *
     * @return map from operator ID to stats
     */
    public Map<String, OperatorStats> allStats() {
        return samples.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> computeStats(e.getKey(), List.copyOf(e.getValue()))
                ));
    }

    /**
     * Returns operators whose average execution time exceeds the SLA threshold.
     *
     * @return list of offending operator stats, sorted by average time descending
     */
    public List<OperatorStats> bottlenecks() {
        return allStats().values().stream()
                .filter(s -> s.avgMs() > slaThresholdMs)
                .sorted((a, b) -> Double.compare(b.avgMs(), a.avgMs()))
                .toList();
    }

    /** Total SLA violations recorded since construction. */
    public long totalViolations() { return totalViolations.get(); }

    // ── Internal ──────────────────────────────────────────────────────────────

    private OperatorStats computeStats(String operatorId, List<Long> nanosList) {
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long ns : nanosList) {
            sum += ns;
            if (ns < min) min = ns;
            if (ns > max) max = ns;
        }
        double avgMs  = (sum / (double) nanosList.size()) / 1_000_000.0;
        double minMs  = min / 1_000_000.0;
        double maxMs  = max / 1_000_000.0;
        long violations = nanosList.stream().filter(ns -> ns / 1_000_000.0 > slaThresholdMs).count();
        return new OperatorStats(operatorId, nanosList.size(), avgMs, minMs, maxMs, violations,
                avgMs <= slaThresholdMs);
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Per-operator execution statistics.
     *
     * @param operatorId    operator identifier
     * @param sampleCount   number of samples recorded
     * @param avgMs         average execution time in milliseconds
     * @param minMs         minimum execution time in milliseconds
     * @param maxMs         maximum execution time in milliseconds
     * @param violations    samples exceeding the SLA threshold
     * @param meetsTarget   whether avgMs &le; SLA threshold
     */
    public record OperatorStats(
            String operatorId,
            int sampleCount,
            double avgMs,
            double minMs,
            double maxMs,
            long violations,
            boolean meetsTarget
    ) {
        static OperatorStats empty(String id) {
            return new OperatorStats(id, 0, 0, 0, 0, 0, true);
        }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link OperatorExecutionOptimizer}.
     */
    public static final class Builder {
        private double slaThresholdMs        = TARGET_AVG_EXEC_MS;
        private int maxSamplesPerOperator    = 1_000;

        private Builder() {}

        public Builder slaThresholdMs(double ms) {
            if (ms <= 0) throw new IllegalArgumentException("slaThresholdMs must be positive");
            this.slaThresholdMs = ms;
            return this;
        }

        public Builder maxSamplesPerOperator(int max) {
            if (max <= 0) throw new IllegalArgumentException("maxSamplesPerOperator must be positive");
            this.maxSamplesPerOperator = max;
            return this;
        }

        public OperatorExecutionOptimizer build() { return new OperatorExecutionOptimizer(this); }
    }
}

