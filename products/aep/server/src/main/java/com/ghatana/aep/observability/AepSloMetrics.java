/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SLO metric definitions and recording helpers for AEP.
 *
 * <p>Records the six Phase-6 SLOs defined in the World-Class AEP Report:
 * <ol>
 *   <li>Intake latency — event received → pipeline processing started</li>
 *   <li>Pipeline completion rate — completed / (completed + failed)</li>
 *   <li>Run failure rate — failed / total</li>
 *   <li>Review-queue latency — review item enqueued → first decision</li>
 *   <li>Policy-promotion latency — item approved → policy marked ACTIVE</li>
 *   <li>Replay success rate — replay attempts that complete without error</li>
 * </ol>
 *
 * <p>All measurements delegate to the platform {@link MetricsCollector} which
 * wraps Micrometer. Metric names follow the {@code aep.slo.*} namespace.
 *
 * @doc.type class
 * @doc.purpose SLO metric definitions and recording for Phase-6 observability
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class AepSloMetrics {

    private static final Logger log = LoggerFactory.getLogger(AepSloMetrics.class);

    // ─── Metric names ─────────────────────────────────────────────────────────

    /** Histogram of event-intake-to-processing-start latency in milliseconds. */
    public static final String INTAKE_LATENCY_MS = "aep.slo.intake.latency.ms";

    /** Counter for successfully completed pipeline runs. */
    public static final String RUNS_COMPLETED = "aep.slo.runs.completed";

    /** Counter for failed pipeline runs. */
    public static final String RUNS_FAILED = "aep.slo.runs.failed";

    /**
     * Gauge tracking current run-failure rate (snapshot, updated on each run).
     * Separate counter approach: derive the ratio in your dashboard.
     */
    public static final String RUNS_TOTAL = "aep.slo.runs.total";

    /** Histogram of review-item-enqueued → first-decision latency in ms. */
    public static final String REVIEW_QUEUE_LATENCY_MS = "aep.slo.review.queue.latency.ms";

    /** Histogram of policy item approved → ACTIVE promotion latency in ms. */
    public static final String POLICY_PROMOTION_LATENCY_MS = "aep.slo.policy.promotion.latency.ms";

    /** Counter for replay attempts. */
    public static final String REPLAY_ATTEMPTS = "aep.slo.replay.attempts";

    /** Counter for successful replays. */
    public static final String REPLAY_SUCCEEDED = "aep.slo.replay.succeeded";

    /** Counter for failed replays. */
    public static final String REPLAY_FAILED = "aep.slo.replay.failed";

    // ─── State for rolling run-failure-rate gauge ─────────────────────────────

    private final AtomicLong totalRuns = new AtomicLong(0);
    private final AtomicLong failedRuns = new AtomicLong(0);

    private final MetricsCollector metricsCollector;
    private final MeterRegistry meterRegistry;

    /**
     * Creates SLO metrics helper backed by the given collector.
     *
     * @param metricsCollector platform metrics collector (never null)
     */
    public AepSloMetrics(@NotNull MetricsCollector metricsCollector) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector required");
        this.meterRegistry = metricsCollector.getMeterRegistry();
        registerGauges();
    }

    private void registerGauges() {
        try {
            meterRegistry.gauge("aep.slo.run.failure.rate",
                    failedRuns, ref -> totalRuns.get() == 0
                            ? 0.0 : (double) ref.get() / totalRuns.get());
        } catch (Exception e) {
            log.debug("Could not register failure-rate gauge (test environment?): {}", e.getMessage());
        }
    }

    // ─── SLO 1: Intake latency ────────────────────────────────────────────────

    /**
     * Records the time from event receipt to processing-started.
     *
     * @param receivedAt  when the HTTP request arrived
     * @param processedAt when the engine began processing
     * @param tenantId    tenant tag
     */
    public void recordIntakeLatency(
            @NotNull Instant receivedAt,
            @NotNull Instant processedAt,
            @NotNull String tenantId) {
        long latencyMs = Duration.between(receivedAt, processedAt).toMillis();
        metricsCollector.recordTimer(INTAKE_LATENCY_MS, latencyMs, "tenant", tenantId);
        log.debug("[slo] intake-latency tenant={} latency={}ms", tenantId, latencyMs);
    }

    // ─── SLO 2+3: Pipeline completion / failure rates ─────────────────────────

    /**
     * Records a completed pipeline run (success).
     *
     * @param tenantId   tenant tag
     * @param pipelineId pipeline tag
     * @param durationMs run duration
     */
    public void recordRunCompleted(
            @NotNull String tenantId,
            @NotNull String pipelineId,
            long durationMs) {
        totalRuns.incrementAndGet();
        metricsCollector.incrementCounter(RUNS_COMPLETED, "tenant", tenantId, "pipeline", pipelineId);
        metricsCollector.incrementCounter(RUNS_TOTAL, "tenant", tenantId);
        metricsCollector.recordTimer("aep.slo.run.duration.ms", durationMs,
                "tenant", tenantId, "pipeline", pipelineId, "status", "SUCCEEDED");
        log.debug("[slo] run-completed tenant={} pipeline={} duration={}ms",
                tenantId, pipelineId, durationMs);
    }

    /**
     * Records a failed pipeline run.
     *
     * @param tenantId    tenant tag
     * @param pipelineId  pipeline tag
     * @param durationMs  run duration until failure
     * @param failureKind short failure classifier (e.g. "timeout", "engine_error")
     */
    public void recordRunFailed(
            @NotNull String tenantId,
            @NotNull String pipelineId,
            long durationMs,
            @NotNull String failureKind) {
        totalRuns.incrementAndGet();
        failedRuns.incrementAndGet();
        metricsCollector.incrementCounter(RUNS_FAILED,
                "tenant", tenantId, "pipeline", pipelineId, "failure", failureKind);
        metricsCollector.incrementCounter(RUNS_TOTAL, "tenant", tenantId);
        metricsCollector.recordTimer("aep.slo.run.duration.ms", durationMs,
                "tenant", tenantId, "pipeline", pipelineId, "status", "FAILED");
        log.debug("[slo] run-failed tenant={} pipeline={} failure={} duration={}ms",
                tenantId, pipelineId, failureKind, durationMs);
    }

    // ─── SLO 4: Review-queue latency ─────────────────────────────────────────

    /**
     * Records the latency from HITL item enqueue → first decision.
     *
     * @param enqueuedAt      when the review item was submitted
     * @param decidedAt       when the first approve/reject decision was recorded
     * @param tenantId        tenant tag
     * @param reviewItemType  type tag ("POLICY", "AGENT_BEHAVIOR", etc.)
     */
    public void recordReviewQueueLatency(
            @NotNull Instant enqueuedAt,
            @NotNull Instant decidedAt,
            @NotNull String tenantId,
            @NotNull String reviewItemType) {
        long latencyMs = Duration.between(enqueuedAt, decidedAt).toMillis();
        metricsCollector.recordTimer(REVIEW_QUEUE_LATENCY_MS, latencyMs,
                "tenant", tenantId, "itemType", reviewItemType);
        log.debug("[slo] review-queue-latency tenant={} itemType={} latency={}ms",
                tenantId, reviewItemType, latencyMs);
    }

    // ─── SLO 5: Policy-promotion latency ─────────────────────────────────────

    /**
     * Records the latency from policy approval → ACTIVE status.
     *
     * @param approvedAt    when the review item was approved
     * @param promotedAt    when the policy became ACTIVE
     * @param tenantId      tenant tag
     * @param skillId       skill/agent identity tag
     */
    public void recordPolicyPromotionLatency(
            @NotNull Instant approvedAt,
            @NotNull Instant promotedAt,
            @NotNull String tenantId,
            @NotNull String skillId) {
        long latencyMs = Duration.between(approvedAt, promotedAt).toMillis();
        metricsCollector.recordTimer(POLICY_PROMOTION_LATENCY_MS, latencyMs,
                "tenant", tenantId, "skillId", skillId);
        log.debug("[slo] policy-promotion-latency tenant={} skill={} latency={}ms",
                tenantId, skillId, latencyMs);
    }

    // ─── SLO 6: Replay success rate ───────────────────────────────────────────

    /**
     * Records a replay attempt outcome.
     *
     * @param success     true if the replay completed without error
     * @param tenantId    tenant tag
     * @param pipelineId  pipeline tag
     */
    public void recordReplayAttempt(boolean success, @NotNull String tenantId, @NotNull String pipelineId) {
        metricsCollector.incrementCounter(REPLAY_ATTEMPTS,
                "tenant", tenantId, "pipeline", pipelineId);
        if (success) {
            metricsCollector.incrementCounter(REPLAY_SUCCEEDED,
                    "tenant", tenantId, "pipeline", pipelineId);
            log.debug("[slo] replay-success tenant={} pipeline={}", tenantId, pipelineId);
        } else {
            metricsCollector.incrementCounter(REPLAY_FAILED,
                    "tenant", tenantId, "pipeline", pipelineId);
            log.debug("[slo] replay-failed tenant={} pipeline={}", tenantId, pipelineId);
        }
    }

    // ─── Snapshots for /metrics/slo endpoint ─────────────────────────────────

    /**
     * Returns a point-in-time snapshot of rolling run counters.
     * Useful for serving {@code GET /metrics/slo} without Prometheus scraping.
     *
     * @return immutable map with total/failed/failureRate entries
     */
    public Map<String, Object> runCountSnapshot() {
        long total = totalRuns.get();
        long failed = failedRuns.get();
        double rate = total == 0 ? 0.0 : (double) failed / total;
        return Map.of(
                "totalRuns", total,
                "failedRuns", failed,
                "runFailureRate", rate);
    }
}
