package com.ghatana.datacloud.attention;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.EventRecord;
import com.ghatana.datacloud.attention.SalienceScorer.ScoringContext;
import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.datacloud.workspace.SpotlightItem;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages cognitive attention for the organizational brain.
 *
 * <p><b>Purpose</b><br>
 * The AttentionManager is the central component for prioritizing and routing
 * events based on their salience. It:
 * <ul>
 *   <li>Computes salience scores for incoming events</li>
 *   <li>Routes high-salience items to the global workspace</li>
 *   <li>Triggers emergency broadcasts for critical items</li>
 *   <li>Maintains attention metrics for observability</li>
 * </ul>
 *
 * <p><b>Routing Logic</b><br>
 * <pre>
 * if (salience >= EMERGENCY_THRESHOLD) {
 *     // Broadcast to all subscribers immediately
 *     globalWorkspace.broadcast(item);
 * } else if (salience >= ELEVATION_THRESHOLD) {
 *     // Add to spotlight for deliberate processing
 *     globalWorkspace.spotlight(item);
 * } else {
 *     // Continue normal processing
 *     return OperatorResult.of(event);
 * }
 * </pre>
 *
 * <p><b>Performance</b><br>
 * Target: < 50ms p99 for attention routing
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AttentionManager attention = AttentionManager.builder()
 *     .salienceScorer(scorer)
 *     .globalWorkspace(workspace)
 *     .metricsCollector(metrics)
 *     .elevationThreshold(0.7)
 *     .emergencyThreshold(0.95)
 *     .build();
 *
 * // Score and route an event
 * AttentionResult result = attention.attend(eventRecord, context).getResult();
 * if (result.wasElevated()) {
 *     // Item is in global workspace
 * }
 * }</pre>
 *
 * @see SalienceScorer
 * @see GlobalWorkspace
 * @doc.type class
 * @doc.purpose Cognitive attention management
 * @doc.layer core
 * @doc.pattern Mediator, Observer
 */
@Slf4j
@RequiredArgsConstructor
@Builder
public class AttentionManager {

    /**
     * Default threshold for elevating items to global workspace.
     */
    public static final double DEFAULT_ELEVATION_THRESHOLD = 0.7;

    /**
     * Default threshold for emergency broadcast.
     */
    public static final double DEFAULT_EMERGENCY_THRESHOLD = 0.95;

    // Dependencies
    private final SalienceScorer salienceScorer;
    private final GlobalWorkspace globalWorkspace;
    private final MetricsCollector metricsCollector;

    // Configuration
    @Builder.Default
    private final double elevationThreshold = DEFAULT_ELEVATION_THRESHOLD;

    @Builder.Default
    private final double emergencyThreshold = DEFAULT_EMERGENCY_THRESHOLD;

    // Metrics
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong elevatedCount = new AtomicLong(0);
    private final AtomicLong emergencyCount = new AtomicLong(0);

    /**
     * Attend to a record, computing salience and routing appropriately.
     *
     * @param record  The record to attend to
     * @param context Scoring context
     * @return Promise with AttentionResult
     */
    public Promise<AttentionResult> attend(DataRecord record, ScoringContext context) {
        Objects.requireNonNull(record, "record cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        Instant startTime = Instant.now();
        totalProcessed.incrementAndGet();

        return salienceScorer.score(record, context)
                .then(score -> routeByScore(record, score, context))
                .whenComplete((result, ex) -> {
                    recordMetrics(startTime, result, ex);
                });
    }

    /**
     * Attend to a record with a precomputed salience score.
     *
     * @param record The record to attend to
     * @param score  Precomputed salience score
     * @return Promise with AttentionResult
     */
    public Promise<AttentionResult> attendWithScore(DataRecord record, SalienceScore score) {
        Objects.requireNonNull(record, "record cannot be null");
        Objects.requireNonNull(score, "score cannot be null");

        totalProcessed.incrementAndGet();
        
        ScoringContext context = ScoringContext.builder()
                .tenantId(record.getTenantId())
                .build();

        return routeByScore(record, score, context);
    }

    /**
     * Directly elevate an item to the global workspace (bypass scoring).
     *
     * @param record    The record to elevate
     * @param reason    Reason for elevation
     * @param emergency Whether this is an emergency
     * @return Promise with AttentionResult
     */
    public Promise<AttentionResult> elevate(
            DataRecord record,
            String reason,
            boolean emergency) {

        Objects.requireNonNull(record, "record cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        SalienceScore score = SalienceScore.builder()
                .score(emergency ? 1.0 : emergencyThreshold - 0.1)
                .breakdown(Map.of("manualElevation", true, "reason", reason))
                .build();

        SpotlightItem item = SpotlightItem.builder()
                .record(record)
                .salienceScore(score)
                .summary(reason)
                .emergency(emergency)
                .build();

        if (emergency) {
            emergencyCount.incrementAndGet();
            return globalWorkspace.broadcast(item)
                    .map(v -> AttentionResult.builder()
                            .record(record)
                            .score(score)
                            .action(AttentionAction.BROADCAST)
                            .spotlightItem(item)
                            .build());
        } else {
            elevatedCount.incrementAndGet();
            return globalWorkspace.spotlight(item)
                    .map(v -> AttentionResult.builder()
                            .record(record)
                            .score(score)
                            .action(AttentionAction.ELEVATED)
                            .spotlightItem(item)
                            .build());
        }
    }

    /**
     * Score salience for a record without routing.
     *
     * @param record  The record to score
     * @param context Scoring context
     * @return Promise with SalienceScore
     */
    public Promise<SalienceScore> scoreSalience(DataRecord record, ScoringContext context) {
        return salienceScorer.score(record, context);
    }

    /**
     * Get current attention statistics.
     *
     * @return AttentionStats snapshot
     */
    public AttentionStats getStats() {
        return new AttentionStats(
                totalProcessed.get(),
                elevatedCount.get(),
                emergencyCount.get(),
                elevationThreshold,
                emergencyThreshold
        );
    }

    // ==================== Private Methods ====================

    private Promise<AttentionResult> routeByScore(
            DataRecord record,
            SalienceScore score,
            ScoringContext context) {

        if (score.isEmergency() || score.getScore() >= emergencyThreshold) {
            // EMERGENCY: Broadcast immediately
            log.warn("Emergency salience detected for record {}: score={}",
                    record.getId(), score.getScore());

            emergencyCount.incrementAndGet();

            SpotlightItem item = SpotlightItem.builder()
                    .record(record)
                    .salienceScore(score)
                    .summary(generateSummary(record, score))
                    .emergency(true)
                    .build();

            return globalWorkspace.broadcast(item)
                    .map(v -> AttentionResult.builder()
                            .record(record)
                            .score(score)
                            .action(AttentionAction.BROADCAST)
                            .spotlightItem(item)
                            .build());

        } else if (score.isHigh() || score.getScore() >= elevationThreshold) {
            // HIGH PRIORITY: Elevate to spotlight
            log.info("Elevating high-salience record {} to spotlight: score={}",
                    record.getId(), score.getScore());

            elevatedCount.incrementAndGet();

            SpotlightItem item = SpotlightItem.builder()
                    .record(record)
                    .salienceScore(score)
                    .summary(generateSummary(record, score))
                    .emergency(false)
                    .build();

            return globalWorkspace.spotlight(item)
                    .map(v -> AttentionResult.builder()
                            .record(record)
                            .score(score)
                            .action(AttentionAction.ELEVATED)
                            .spotlightItem(item)
                            .build());

        } else {
            // NORMAL: Continue standard processing
            log.debug("Record {} below elevation threshold: score={}",
                    record.getId(), score.getScore());

            return Promise.of(AttentionResult.builder()
                    .record(record)
                    .score(score)
                    .action(AttentionAction.NORMAL)
                    .build());
        }
    }

    private String generateSummary(DataRecord record, SalienceScore score) {
        StringBuilder sb = new StringBuilder();
        sb.append("Record ").append(record.getId());
        sb.append(" (").append(record.getRecordType()).append(")");
        sb.append(" scored ").append(String.format("%.2f", score.getScore()));

        if (score.getBreakdown() != null && !score.getBreakdown().isEmpty()) {
            sb.append(" - ");
            score.getBreakdown().forEach((k, v) -> 
                sb.append(k).append(": ").append(v).append(", ")
            );
        }

        return sb.toString();
    }

    private void recordMetrics(Instant startTime, AttentionResult result, Throwable ex) {
        if (metricsCollector == null) {
            return;
        }

        long latencyMs = java.time.Duration.between(startTime, Instant.now()).toMillis();

        metricsCollector.recordTimer("attention.latency", latencyMs);
        metricsCollector.incrementCounter("attention.processed");

        if (ex != null) {
            metricsCollector.incrementCounter("attention.errors");
        } else if (result != null) {
            metricsCollector.incrementCounter("attention.action." + result.getAction().name().toLowerCase());
            // Note: recordGauge not available, using counter instead for high salience records
            if (result.getScore().isHigh()) {
                metricsCollector.incrementCounter("attention.high_salience");
            }
        }
    }

    /**
     * Result of attention processing.
     */
    @Value
    @Builder
    public static class AttentionResult {
        /**
         * The processed record.
         */
        DataRecord record;

        /**
         * Computed salience score.
         */
        SalienceScore score;

        /**
         * Action taken.
         */
        AttentionAction action;

        /**
         * Spotlight item if elevated/broadcast.
         */
        SpotlightItem spotlightItem;

        /**
         * Check if record was elevated to spotlight.
         */
        public boolean wasElevated() {
            return action == AttentionAction.ELEVATED || action == AttentionAction.BROADCAST;
        }

        /**
         * Check if this was an emergency broadcast.
         */
        public boolean wasEmergency() {
            return action == AttentionAction.BROADCAST;
        }
    }

    /**
     * Actions taken by the attention manager.
     */
    public enum AttentionAction {
        /**
         * Normal processing continues.
         */
        NORMAL,

        /**
         * Elevated to global workspace spotlight.
         */
        ELEVATED,

        /**
         * Emergency broadcast to all subscribers.
         */
        BROADCAST
    }

    /**
     * Attention statistics snapshot.
     */
    public record AttentionStats(
            long totalProcessed,
            long elevatedCount,
            long emergencyCount,
            double elevationThreshold,
            double emergencyThreshold
    ) {
        public double elevationRate() {
            return totalProcessed > 0 ? (double) elevatedCount / totalProcessed : 0.0;
        }

        public double emergencyRate() {
            return totalProcessed > 0 ? (double) emergencyCount / totalProcessed : 0.0;
        }
    }
}
