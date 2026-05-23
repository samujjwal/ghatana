package com.ghatana.kernel.interaction;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Interaction SLO enforcer with latency budget configuration.
 *
 * <p>PERF-002: Enforces Service Level Objectives (SLOs) for product interactions
 * with configurable latency budgets. Tracks latency metrics and enforces SLO
 * compliance through circuit breaker patterns and budget exhaustion handling.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Per-contract latency budget configuration</li>
 *   <li>Latency percentile tracking (p50, p95, p99)</li>
 *   <li>Budget exhaustion detection and enforcement</li>
 *   <li>SLO violation alerts and metrics</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Interaction SLO enforcement with latency budget configuration
 * @doc.layer kernel
 * @doc.pattern Resilience
 */
public final class InteractionSLOEnforcer {

    public record SLOConfig(
        String contractId,
        Duration latencyBudget,
        double errorRateThreshold,
        Duration budgetWindow
    ) {
        public SLOConfig {
            if (latencyBudget == null || latencyBudget.isNegative() || latencyBudget.isZero()) {
                throw new IllegalArgumentException("latencyBudget must be positive");
            }
            if (errorRateThreshold < 0 || errorRateThreshold > 1) {
                throw new IllegalArgumentException("errorRateThreshold must be between 0 and 1");
            }
            if (budgetWindow == null || budgetWindow.isNegative() || budgetWindow.isZero()) {
                throw new IllegalArgumentException("budgetWindow must be positive");
            }
        }
    }

    public record LatencyMetrics(
        long p50Ms,
        long p95Ms,
        long p99Ms,
        long maxMs,
        long count
    ) {
        public LatencyMetrics {
            if (p50Ms < 0 || p95Ms < 0 || p99Ms < 0 || maxMs < 0) {
                throw new IllegalArgumentException("Latency values must be non-negative");
            }
            if (count < 0) {
                throw new IllegalArgumentException("Count must be non-negative");
            }
        }
    }

    public record SLOStatus(
        String contractId,
        boolean withinBudget,
        double budgetUtilization,
        LatencyMetrics latencyMetrics,
        double errorRate,
        boolean errorRateWithinThreshold
    ) {
        public SLOStatus {
            if (budgetUtilization < 0 || budgetUtilization > 1) {
                throw new IllegalArgumentException("budgetUtilization must be between 0 and 1");
            }
            if (errorRate < 0 || errorRate > 1) {
                throw new IllegalArgumentException("errorRate must be between 0 and 1");
            }
        }
    }

    private final Map<String, SLOConfig> sloConfigs;
    private final Map<String, LatencyTracker> latencyTrackers;
    private final Map<String, ErrorTracker> errorTrackers;
    private final Map<String, SLOStatus> currentStatus;

    public InteractionSLOEnforcer() {
        this.sloConfigs = new ConcurrentHashMap<>();
        this.latencyTrackers = new ConcurrentHashMap<>();
        this.errorTrackers = new ConcurrentHashMap<>();
        this.currentStatus = new ConcurrentHashMap<>();
    }

    /**
     * Registers an SLO configuration for a contract.
     */
    public void registerSLO(SLOConfig config) {
        sloConfigs.put(config.contractId(), config);
        latencyTrackers.put(config.contractId(), new LatencyTracker());
        errorTrackers.put(config.contractId(), new ErrorTracker());
    }

    /**
     * Records a successful interaction with its latency.
     */
    public void recordSuccess(String contractId, Duration latency) {
        LatencyTracker tracker = latencyTrackers.get(contractId);
        if (tracker != null) {
            tracker.record(latency.toMillis());
        }
    }

    /**
     * Records a failed interaction.
     */
    public void recordFailure(String contractId) {
        ErrorTracker tracker = errorTrackers.get(contractId);
        if (tracker != null) {
            tracker.recordFailure();
        }
    }

    /**
     * Records a successful interaction (for error rate calculation).
     */
    public void recordSuccessForErrorRate(String contractId) {
        ErrorTracker tracker = errorTrackers.get(contractId);
        if (tracker != null) {
            tracker.recordSuccess();
        }
    }

    /**
     * Checks if an interaction should be allowed based on SLO status.
     */
    public boolean shouldAllowInteraction(String contractId) {
        SLOStatus status = getSLOStatus(contractId);
        return status.withinBudget() && status.errorRateWithinThreshold();
    }

    /**
     * Gets the current SLO status for a contract.
     */
    public SLOStatus getSLOStatus(String contractId) {
        SLOConfig config = sloConfigs.get(contractId);
        if (config == null) {
            // No SLO configured, allow by default
            return new SLOStatus(
                contractId,
                true,
                0.0,
                new LatencyMetrics(0, 0, 0, 0, 0),
                0.0,
                true
            );
        }

        LatencyTracker latencyTracker = latencyTrackers.get(contractId);
        ErrorTracker errorTracker = errorTrackers.get(contractId);

        LatencyMetrics latencyMetrics = latencyTracker != null
            ? latencyTracker.getMetrics()
            : new LatencyMetrics(0, 0, 0, 0, 0);

        double errorRate = errorTracker != null
            ? errorTracker.getErrorRate()
            : 0.0;

        boolean latencyWithinBudget = latencyMetrics.p99Ms() <= config.latencyBudget().toMillis();
        boolean errorRateWithinThreshold = errorRate <= config.errorRateThreshold();

        double budgetUtilization = latencyMetrics.count() > 0
            ? (double) latencyMetrics.p99Ms() / config.latencyBudget().toMillis()
            : 0.0;

        boolean withinBudget = latencyWithinBudget && errorRateWithinThreshold;

        SLOStatus status = new SLOStatus(
            contractId,
            withinBudget,
            budgetUtilization,
            latencyMetrics,
            errorRate,
            errorRateWithinThreshold
        );

        currentStatus.put(contractId, status);
        return status;
    }

    /**
     * Resets SLO tracking for a contract.
     */
    public void resetSLO(String contractId) {
        latencyTrackers.remove(contractId);
        errorTrackers.remove(contractId);
        currentStatus.remove(contractId);
    }

    /**
     * Resets all SLO tracking.
     */
    public void resetAll() {
        latencyTrackers.clear();
        errorTrackers.clear();
        currentStatus.clear();
    }

    private static class LatencyTracker {
        private final AtomicLong count = new AtomicLong();
        private final java.util.TreeSet<Long> latencies = new java.util.TreeSet<>();

        void record(long latencyMs) {
            count.incrementAndGet();
            latencies.add(latencyMs);
        }

        LatencyMetrics getMetrics() {
            long cnt = count.get();
            if (cnt == 0) {
                return new LatencyMetrics(0, 0, 0, 0, 0);
            }

            long[] sorted = latencies.stream().mapToLong(Long::longValue).toArray();
            long p50 = percentile(sorted, 50);
            long p95 = percentile(sorted, 95);
            long p99 = percentile(sorted, 99);
            long max = sorted[sorted.length - 1];

            return new LatencyMetrics(p50, p95, p99, max, cnt);
        }

        private long percentile(long[] sorted, int percentile) {
            int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
        }
    }

    private static class ErrorTracker {
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();

        void recordFailure() {
            totalRequests.incrementAndGet();
            failures.incrementAndGet();
        }

        void recordSuccess() {
            totalRequests.incrementAndGet();
        }

        double getErrorRate() {
            long total = totalRequests.get();
            if (total == 0) {
                return 0.0;
            }
            return (double) failures.get() / total;
        }
    }
}
