package com.ghatana.datacloud.attention;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.client.LearningSignal;
import com.ghatana.datacloud.client.LearningSignalStore;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.Anomaly;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.AnomalyContext;
import com.ghatana.datacloud.spi.ai.PredictionCapability;
import com.ghatana.datacloud.spi.ai.PredictionCapability.PredictionRequest;
import com.ghatana.datacloud.spi.ai.PredictionCapability.PredictionType;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of SalienceScorer combining multiple scoring signals.
 *
 * <p><b>Purpose</b><br>
 * Provides comprehensive salience scoring by combining:
 * <ul>
 *   <li>Anomaly detection signals</li>
 *   <li>Prediction-based hotness scoring</li>
 *   <li>Rule-based pattern matching</li>
 *   <li>Statistical baseline deviation</li>
 * </ul>
 *
 * <p><b>Scoring Algorithm</b><br>
 * <pre>
 * finalScore = baseScore 
 *            + (anomalyBoost × anomalyWeight)
 *            + (hotnessBoost × hotnessWeight)
 *            + (patternBoost × patternWeight)
 *            + urgencyBoost
 * 
 * where:
 *   - baseScore = average of component scores
 *   - anomalyBoost = max(anomaly.severity × anomaly.confidence)
 *   - hotnessBoost = predicted data hotness
 *   - patternBoost = pattern match confidence
 *   - urgencyBoost = context-provided urgency
 * </pre>
 *
 * <p><b>Performance</b><br>
 * Target: < 50ms p99 for single record scoring
 *
 * @see SalienceScorer
 * @see SalienceScore
 * @doc.type class
 * @doc.purpose Default salience scorer implementation
 * @doc.layer core
 * @doc.pattern Strategy
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultSalienceScorer implements SalienceScorer {

    private static final String SCORER_ID = "default-salience-scorer";
    private static final String MODEL_VERSION = "1.0.0";

    // Scoring weights
    private static final double ANOMALY_WEIGHT = 0.3;
    private static final double HOTNESS_WEIGHT = 0.2;
    private static final double PATTERN_WEIGHT = 0.2;
    private static final double BASELINE_WEIGHT = 0.3;

    // Dependencies
    private final AnomalyDetectionCapability anomalyDetector;
    private final PredictionCapability predictor;
    private final LearningSignalStore signalStore;

    // Baseline statistics cache per tenant
    private final ConcurrentHashMap<String, BaselineStats> baselineCache = new ConcurrentHashMap<>();

    @Override
    public Promise<SalienceScore> score(DataRecord record, ScoringContext context) {
        Instant startTime = Instant.now();
        Map<String, Object> breakdown = new HashMap<>();

        // Collect scoring signals in parallel
        Promise<Double> anomalyPromise = context.includeAnomalyDetection()
                ? computeAnomalyScore(record, context, breakdown)
                : Promise.of(0.0);

        Promise<Double> hotnessPromise = context.includePrediction()
                ? computeHotnessScore(record, context, breakdown)
                : Promise.of(0.0);

        Promise<Double> patternPromise = context.includePatternMatching()
                ? computePatternScore(record, context, breakdown)
                : Promise.of(0.0);

        Promise<Double> baselinePromise = computeBaselineDeviation(record, context, breakdown);

        // Use Promises.toList to collect results as a list
        return Promises.toList(List.of(anomalyPromise, hotnessPromise, patternPromise, baselinePromise))
                .map(results -> {
                    double anomalyScore = results.get(0);
                    double hotnessScore = results.get(1);
                    double patternScore = results.get(2);
                    double baselineScore = results.get(3);

                    // Compute weighted final score
                    double rawScore = (anomalyScore * ANOMALY_WEIGHT)
                            + (hotnessScore * HOTNESS_WEIGHT)
                            + (patternScore * PATTERN_WEIGHT)
                            + (baselineScore * BASELINE_WEIGHT);

                    // Apply urgency boost
                    double finalScore = Math.min(1.0, rawScore + context.urgencyBoost());

                    // Add timing to breakdown
                    long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
                    breakdown.put("latencyMs", latencyMs);
                    breakdown.put("recordId", record.getId().toString());

                    SalienceScore score = SalienceScore.builder()
                            .score(finalScore)
                            .noveltyScore(anomalyScore)
                            .deviationScore(baselineScore)
                            .relevanceScore(patternScore)
                            .urgencyScore(Math.min(1.0, context.urgencyBoost() + hotnessScore))
                            .breakdown(breakdown)
                            .scorerId(SCORER_ID)
                            .modelVersion(MODEL_VERSION)
                            .confidence(computeConfidence(results))
                            .build();

                    // Emit learning signal asynchronously
                    emitLearningSignal(record, context, score);

                    log.debug("Scored record {} with salience {} in {}ms",
                            record.getId(), finalScore, latencyMs);

                    return score;
                });
    }

    @Override
    public Promise<List<SalienceScore>> scoreBatch(List<DataRecord> records, ScoringContext context) {
        // Score each record and collect results
        List<Promise<SalienceScore>> promises = records.stream()
                .map(record -> score(record, context))
                .toList();

        return Promises.toList(promises);
    }

    @Override
    public String getScorerId() {
        return SCORER_ID;
    }

    @Override
    public String getModelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public boolean supportsFeature(ScoringFeature feature) {
        return switch (feature) {
            case ANOMALY_DETECTION -> anomalyDetector != null;
            case ML_PREDICTION -> predictor != null;
            case PATTERN_MATCHING -> true;
            case TIME_DECAY -> true;
            case CONTEXT_AWARE -> true;
            case GOAL_RELEVANCE -> true;
            case CONTINUOUS_LEARNING -> signalStore != null;
        };
    }

    @Override
    public Promise<Void> updateBaseline(String tenantId) {
        log.info("Updating baseline for tenant {}", tenantId);

        if (anomalyDetector == null) {
            return Promise.complete();
        }

        return anomalyDetector.updateBaseline(tenantId, null)
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to update baseline for tenant {}: {}", tenantId, ex.getMessage());
                    } else {
                        // Invalidate cached baseline
                        baselineCache.remove(tenantId);
                        log.info("Baseline updated for tenant {}", tenantId);
                    }
                });
    }

    // ==================== Private Scoring Methods ====================

    private Promise<Double> computeAnomalyScore(
            DataRecord record,
            ScoringContext context,
            Map<String, Object> breakdown) {

        if (anomalyDetector == null) {
            return Promise.of(0.0);
        }

        AnomalyContext anomalyContext = AnomalyContext.builder()
                .tenantId(context.tenantId())
                .detectionType(AnomalyDetectionCapability.DetectionType.BEHAVIORAL)
                .build();

        return anomalyDetector.detect(anomalyContext)
                .map(anomalies -> {
                    if (anomalies.isEmpty()) {
                        breakdown.put("anomalyCount", 0);
                        return 0.0;
                    }

                    // Find max severity anomaly
                    Optional<Anomaly> maxAnomaly = anomalies.stream()
                            .max((a, b) -> Double.compare(
                                    getSeverityWeight(a.getSeverity()) * a.getConfidence(),
                                    getSeverityWeight(b.getSeverity()) * b.getConfidence()
                            ));

                    if (maxAnomaly.isPresent()) {
                        Anomaly anomaly = maxAnomaly.get();
                        double score = getSeverityWeight(anomaly.getSeverity()) * anomaly.getConfidence();
                        breakdown.put("anomalyCount", anomalies.size());
                        breakdown.put("maxAnomalySeverity", anomaly.getSeverity().name());
                        breakdown.put("maxAnomalyConfidence", anomaly.getConfidence());
                        return Math.min(1.0, score);
                    }

                    return 0.0;
                })
                .whenException(ex -> {
                    log.warn("Anomaly detection failed: {}", ex.getMessage());
                    breakdown.put("anomalyError", ex.getMessage());
                })
                .then(result -> Promise.of(result != null ? result : 0.0));
    }

    private Promise<Double> computeHotnessScore(
            DataRecord record,
            ScoringContext context,
            Map<String, Object> breakdown) {

        if (predictor == null) {
            return Promise.of(0.0);
        }

        PredictionRequest request = PredictionRequest.builder()
                .predictionType(PredictionType.DATA_HOTNESS)
                .tenantId(context.tenantId())
                .collectionName(record.getCollectionName())
                .context(Map.of(
                        "recordId", record.getId().toString(),
                        "recordType", record.getRecordType().name()
                ))
                .build();

        return predictor.predict(request)
                .map(result -> {
                    double hotness = Double.parseDouble(result.getPredictedValue().toString());
                    breakdown.put("predictedHotness", hotness);
                    breakdown.put("hotnessConfidence", result.getConfidence());
                    return Math.min(1.0, hotness * result.getConfidence());
                })
                .whenException(ex -> {
                    log.warn("Hotness prediction failed: {}", ex.getMessage());
                    breakdown.put("hotnessError", ex.getMessage());
                })
                .then(result -> Promise.of(result != null ? result : 0.0));
    }

    private Promise<Double> computePatternScore(
            DataRecord record,
            ScoringContext context,
            Map<String, Object> breakdown) {

        // Pattern matching is synchronous for now
        // In production, this would query the PatternRegistry
        return Promise.of(0.0)
                .map(base -> {
                    // Check for high-priority event types
                    Map<String, Object> data = record.getData();
                    if (data != null) {
                        String eventType = (String) data.get("eventType");
                        if (eventType != null && isHighPriorityEventType(eventType)) {
                            breakdown.put("patternMatch", eventType);
                            breakdown.put("patternMatchType", "high-priority-event");
                            return 0.8;
                        }
                    }

                    // Check goal relevance
                    if (!context.goalIds().isEmpty()) {
                        // Score based on goal alignment
                        double goalScore = computeGoalRelevance(record, context.goalIds());
                        if (goalScore > 0) {
                            breakdown.put("goalRelevance", goalScore);
                            return goalScore;
                        }
                    }

                    return 0.0;
                });
    }

    private Promise<Double> computeBaselineDeviation(
            DataRecord record,
            ScoringContext context,
            Map<String, Object> breakdown) {

        BaselineStats baseline = baselineCache.computeIfAbsent(
                context.tenantId(),
                this::loadBaselineStats
        );

        // Compute deviation from baseline
        double deviation = computeDeviation(record, baseline);
        breakdown.put("baselineDeviation", deviation);

        return Promise.of(deviation);
    }

    // ==================== Helper Methods ====================

    private boolean isHighPriorityEventType(String eventType) {
        return eventType != null && (
                eventType.contains("error") ||
                eventType.contains("alert") ||
                eventType.contains("security") ||
                eventType.contains("fraud") ||
                eventType.contains("critical") ||
                eventType.contains("emergency")
        );
    }

    private double computeGoalRelevance(DataRecord record, List<String> goalIds) {
        // Simple keyword matching for now
        // In production, this would use semantic similarity
        String content = record.getData() != null ? record.getData().toString() : "";
        long matches = goalIds.stream()
                .filter(goal -> content.toLowerCase().contains(goal.toLowerCase()))
                .count();

        return Math.min(1.0, (double) matches / goalIds.size());
    }

    private BaselineStats loadBaselineStats(String tenantId) {
        // Load from cache or compute defaults
        return new BaselineStats(0.5, 0.2, Instant.now());
    }

    private double computeDeviation(DataRecord record, BaselineStats baseline) {
        // Extract a representative scalar value from the record's data payload.
        double value = extractScalarValue(record);

        // Compute a normalised Z-score:  |value - μ| / σ
        // stdDev floor of 0.01 prevents division-by-zero on brand-new baselines.
        double stdDev = baseline.stdDev() > 0.01 ? baseline.stdDev() : 0.01;
        double zScore = Math.abs(value - baseline.mean()) / stdDev;

        // Map Z-score to [0, 1].  Z=0 → 0.0 (no deviation), Z=3 → 1.0 (extreme).
        return Math.min(1.0, zScore / 3.0);
    }

    /**
     * Extracts a representative scalar from a {@link DataRecord}'s data map.
     *
     * <p>Tries well-known numeric field names first (value, score, amount, …).
     * Falls back to using the data-map's cardinality as a proxy for event
     * complexity, normalised to [0, 1].
     */
    private double extractScalarValue(DataRecord record) {
        Map<String, Object> data = record.getData();
        if (data == null || data.isEmpty()) {
            return 0.5; // Neutral baseline for records with no payload
        }

        // Probe well-known numeric feature fields in priority order
        for (String field : List.of("value", "score", "amount", "count",
                "magnitude", "severity", "priority", "weight", "confidence")) {
            Object v = data.get(field);
            if (v instanceof Number n) {
                return n.doubleValue();
            }
        }

        // Secondary probe: collect all numeric values and return their mean
        double sum = 0.0;
        int numericCount = 0;
        for (Object v : data.values()) {
            if (v instanceof Number n) {
                sum += n.doubleValue();
                numericCount++;
            }
        }
        if (numericCount > 0) {
            return sum / numericCount;
        }

        // Fallback: field cardinality as a lightweight complexity proxy
        return Math.min(1.0, data.size() / 20.0);
    }

    private double computeConfidence(List<Double> results) {
        // Average confidence based on successful computations
        long successCount = results.stream()
                .filter(r -> r > 0)
                .count();
        return Math.max(0.5, (double) successCount / results.size());
    }

    private void emitLearningSignal(DataRecord record, ScoringContext context, SalienceScore score) {
        if (signalStore == null) {
            return;
        }

        LearningSignal signal = LearningSignal.builder()
                .signalType(LearningSignal.SignalType.QUERY)
                .tenantId(context.tenantId())
                .source(LearningSignal.SignalSource.builder()
                        .plugin(SCORER_ID)
                        .collection(record.getCollectionName())
                        .build())
                .features(Map.of(
                        "recordType", record.getRecordType().name(),
                        "collectionName", record.getCollectionName(),
                        "scorerVersion", MODEL_VERSION
                ))
                .metrics(Map.of(
                        "salienceScore", score.getScore(),
                        "noveltyScore", score.getNoveltyScore(),
                        "deviationScore", score.getDeviationScore(),
                        "confidence", score.getConfidence()
                ))
                .build();

        signalStore.store(signal)
                .whenException(ex -> log.warn("Failed to store learning signal: {}", ex.getMessage()));
    }

    /**
     * Maps severity to a numeric weight for scoring.
     *
     * @param severity the anomaly severity
     * @return weight between 0.0 and 1.0
     */
    private double getSeverityWeight(AnomalyDetectionCapability.Severity severity) {
        return switch (severity) {
            case CRITICAL -> 1.0;
            case HIGH -> 0.8;
            case MEDIUM -> 0.5;
            case LOW -> 0.3;
            case INFO -> 0.1;
        };
    }

    /**
     * Baseline statistics for a tenant.
     */
    private record BaselineStats(
            double mean,
            double stdDev,
            Instant computedAt
    ) {}
}
