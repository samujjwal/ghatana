package com.ghatana.aep.expertinterface.analytics;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks quality of recommendations provided to experts.
 * Monitors precision, recall, and correlation with expert decisions.
 *
 * @doc.type class
 * @doc.purpose Measures how well recommendations align with expert decisions
 * @doc.layer core
 * @doc.pattern Service
 */
public class RecommendationQualityTracker {
    private static final Logger log = LoggerFactory.getLogger(RecommendationQualityTracker.class);

    private final Eventloop eventloop;
    private final Map<String, RecommendationHistory> recommendationHistories = new ConcurrentHashMap<>();
    private final Duration evaluationWindow;
    private final int minSampleSize;

    /**
     * Creates a RecommendationQualityTracker with specified parameters.
     *
     * @param evaluationWindow Time window for quality evaluation
     * @param minSampleSize Minimum samples for reliable metrics
     */
    public RecommendationQualityTracker(Duration evaluationWindow, int minSampleSize) {
        this(null, evaluationWindow, minSampleSize);
    }
    
    /**
     * Creates a RecommendationQualityTracker with Eventloop.
     */
    public RecommendationQualityTracker(Eventloop eventloop, Duration evaluationWindow, int minSampleSize) {
        if (evaluationWindow.isNegative() || evaluationWindow.isZero()) {
            throw new IllegalArgumentException("evaluationWindow must be positive");
        }
        if (minSampleSize <= 0) {
            throw new IllegalArgumentException("minSampleSize must be positive");
        }

        this.eventloop = eventloop;
        this.evaluationWindow = evaluationWindow;
        this.minSampleSize = minSampleSize;
    }

    /**
     * Records a recommendation and its expert outcome.
     *
     * @param recommendationId Recommendation identifier
     * @param expertDecision Expert's decision (ACCEPTED/REJECTED/MODIFIED)
     * @param confidence Recommendation confidence score
     * @return Promise of recorded outcome
     */
    public Promise<RecommendationOutcome> recordOutcome(
            String recommendationId,
            ExpertDecision expertDecision,
            double confidence) {

        try {
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
            }

            RecommendationHistory history = recommendationHistories.computeIfAbsent(
                    recommendationId,
                    k -> new RecommendationHistory(recommendationId));

            RecommendationOutcome outcome = new RecommendationOutcome(
                    recommendationId,
                    expertDecision,
                    confidence,
                    Instant.now()
            );

            history.addOutcome(outcome);

            log.debug("Recorded outcome for recommendation {}: {} (confidence: {:.2f})",
                    recommendationId, expertDecision, confidence);

            return Promise.of(outcome);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Calculates precision and recall metrics.
     * Precision: % of recommendations that were accepted
     * Recall: % of accepted patterns that were recommended
     *
     * @return Promise of precision/recall metrics
     */
    public Promise<PrecisionRecallMetrics> calculatePrecisionRecall() {
        try {
            List<RecommendationOutcome> allOutcomes = getAllRecentOutcomes();

            if (allOutcomes.isEmpty()) {
                return Promise.of(PrecisionRecallMetrics.noData());
            }

            long recommended = allOutcomes.size();
            long accepted = allOutcomes.stream()
                    .filter(o -> o.expertDecision() == ExpertDecision.ACCEPTED)
                    .count();

            long modified = allOutcomes.stream()
                    .filter(o -> o.expertDecision() == ExpertDecision.MODIFIED)
                    .count();

            // Precision: accepted / (accepted + rejected)
            double precision = (double) accepted / recommended;

            // Recall: For simplicity, assume accepted recommendations / total possible
            // In practice, this would require knowledge of all patterns
            double recall = (double) accepted / (accepted + modified);

            // F1 score: harmonic mean of precision and recall
            double f1Score = 2 * (precision * recall) / (precision + recall);

            boolean reliable = allOutcomes.size() >= minSampleSize;

            log.info("Precision/Recall: P={:.2f}%, R={:.2f}%, F1={:.2f}% ({} samples, reliable: {})",
                    precision * 100, recall * 100, f1Score * 100, allOutcomes.size(), reliable);

            return Promise.of(new PrecisionRecallMetrics(
                    precision,
                    recall,
                    f1Score,
                    (int) accepted,
                    (int) modified,
                    (int) (recommended - accepted - modified),
                    allOutcomes.size(),
                    reliable
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Analyzes correlation between confidence and acceptance.
     * High correlation means confidence scores are well-calibrated.
     *
     * @return Promise of confidence correlation analysis
     */
    public Promise<ConfidenceCorrelation> analyzeConfidenceCorrelation() {
        try {
            List<RecommendationOutcome> allOutcomes = getAllRecentOutcomes();

            if (allOutcomes.size() < minSampleSize) {
                return Promise.of(ConfidenceCorrelation.insufficient(allOutcomes.size()));
            }

            // Group by confidence bins
            Map<ConfidenceBin, List<RecommendationOutcome>> binned = allOutcomes.stream()
                    .collect(Collectors.groupingBy(
                            o -> ConfidenceBin.fromConfidence(o.confidence())));

            // Calculate acceptance rate per bin
            Map<ConfidenceBin, Double> acceptanceRates = new HashMap<>();

            for (Map.Entry<ConfidenceBin, List<RecommendationOutcome>> entry : binned.entrySet()) {
                long accepted = entry.getValue().stream()
                        .filter(o -> o.expertDecision() == ExpertDecision.ACCEPTED)
                        .count();

                double rate = (double) accepted / entry.getValue().size();
                acceptanceRates.put(entry.getKey(), rate);
            }

            // Calculate correlation (simple: check if higher confidence = higher acceptance)
            double correlation = calculateCorrelationCoefficient(allOutcomes);

            boolean wellCalibrated = correlation > 0.7; // Strong positive correlation

            log.info("Confidence correlation: r={:.2f}, well-calibrated: {}", correlation, wellCalibrated);

            return Promise.of(new ConfidenceCorrelation(
                    correlation,
                    acceptanceRates,
                    wellCalibrated,
                    allOutcomes.size()
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Identifies recommendations with high confidence but low acceptance.
     *
     * @param confidenceThreshold Minimum confidence to consider
     * @return Promise of list of problematic recommendations
     */
    public Promise<List<ProblematicRecommendation>> findOverconfidentRecommendations(
            double confidenceThreshold) {

        try {
            if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
                throw new IllegalArgumentException("confidenceThreshold must be between 0.0 and 1.0");
            }

            List<RecommendationOutcome> allOutcomes = getAllRecentOutcomes();

            List<ProblematicRecommendation> problematic = allOutcomes.stream()
                    .filter(o -> o.confidence() >= confidenceThreshold)
                    .filter(o -> o.expertDecision() == ExpertDecision.REJECTED)
                    .map(o -> new ProblematicRecommendation(
                            o.recommendationId(),
                            o.confidence(),
                            o.expertDecision(),
                            "High confidence but rejected"
                    ))
                    .collect(Collectors.toList());

            log.warn("Found {} overconfident recommendations (threshold: {:.2f})",
                    problematic.size(), confidenceThreshold);

            return Promise.of(problematic);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Tracks recommendation quality trend over time.
     *
     * @return Promise of quality trend
     */
    public Promise<QualityTrend> trackQualityTrend() {
        try {
            List<RecommendationOutcome> allOutcomes = getAllRecentOutcomes();

            if (allOutcomes.size() < minSampleSize) {
                return Promise.of(QualityTrend.insufficient(allOutcomes.size()));
            }

            // Split into early and late periods
            int midpoint = allOutcomes.size() / 2;
            List<RecommendationOutcome> early = allOutcomes.subList(0, midpoint);
            List<RecommendationOutcome> late = allOutcomes.subList(midpoint, allOutcomes.size());

            double earlyAcceptance = calculateAcceptanceRate(early);
            double lateAcceptance = calculateAcceptanceRate(late);

            double change = lateAcceptance - earlyAcceptance;
            TrendDirection direction = determineTrendDirection(change);

            log.info("Quality trend: {} (early: {:.2f}%, late: {:.2f}%, change: {:+.2f}%)",
                    direction, earlyAcceptance * 100, lateAcceptance * 100, change * 100);

            return Promise.of(new QualityTrend(
                    direction,
                    earlyAcceptance,
                    lateAcceptance,
                    change,
                    allOutcomes.size()
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Gets overall recommendation statistics.
     *
     * @return Promise of overall statistics
     */
    public Promise<OverallStatistics> getOverallStatistics() {
        try {
            List<RecommendationOutcome> allOutcomes = getAllRecentOutcomes();

            if (allOutcomes.isEmpty()) {
                return Promise.of(OverallStatistics.noData());
            }

            long accepted = allOutcomes.stream()
                    .filter(o -> o.expertDecision() == ExpertDecision.ACCEPTED)
                    .count();

            long modified = allOutcomes.stream()
                    .filter(o -> o.expertDecision() == ExpertDecision.MODIFIED)
                    .count();

            long rejected = allOutcomes.stream()
                    .filter(o -> o.expertDecision() == ExpertDecision.REJECTED)
                    .count();

            double avgConfidence = allOutcomes.stream()
                    .mapToDouble(RecommendationOutcome::confidence)
                    .average()
                    .orElse(0.0);

            double acceptanceRate = (double) accepted / allOutcomes.size();

            log.info("Overall statistics: {:.2f}% acceptance, avg confidence: {:.2f}",
                    acceptanceRate * 100, avgConfidence);

            return Promise.of(new OverallStatistics(
                    allOutcomes.size(),
                    (int) accepted,
                    (int) modified,
                    (int) rejected,
                    acceptanceRate,
                    avgConfidence
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    // Helper methods

    private List<RecommendationOutcome> getAllRecentOutcomes() {
        Instant cutoff = Instant.now().minus(evaluationWindow);

        return recommendationHistories.values().stream()
                .flatMap(h -> h.getOutcomesInWindow(evaluationWindow).stream())
                .filter(o -> o.timestamp().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    private double calculateAcceptanceRate(List<RecommendationOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            return 0.0;
        }

        long accepted = outcomes.stream()
                .filter(o -> o.expertDecision() == ExpertDecision.ACCEPTED)
                .count();

        return (double) accepted / outcomes.size();
    }

    private double calculateCorrelationCoefficient(List<RecommendationOutcome> outcomes) {
        // Pearson correlation between confidence and acceptance (1 = accepted, 0 = not)
        double meanConfidence = outcomes.stream()
                .mapToDouble(RecommendationOutcome::confidence)
                .average()
                .orElse(0.0);

        double meanAcceptance = outcomes.stream()
                .mapToDouble(o -> o.expertDecision() == ExpertDecision.ACCEPTED ? 1.0 : 0.0)
                .average()
                .orElse(0.0);

        double numerator = 0.0;
        double denomConfidence = 0.0;
        double denomAcceptance = 0.0;

        for (RecommendationOutcome outcome : outcomes) {
            double confDiff = outcome.confidence() - meanConfidence;
            double accDiff = (outcome.expertDecision() == ExpertDecision.ACCEPTED ? 1.0 : 0.0) - meanAcceptance;

            numerator += confDiff * accDiff;
            denomConfidence += confDiff * confDiff;
            denomAcceptance += accDiff * accDiff;
        }

        double denominator = Math.sqrt(denomConfidence * denomAcceptance);

        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }

    private TrendDirection determineTrendDirection(double change) {
        if (Math.abs(change) < 0.05) {
            return TrendDirection.STABLE;
        }
        return change > 0 ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
    }

    // Inner classes

    private static class RecommendationHistory {
        private final String recommendationId;
        private final Deque<RecommendationOutcome> outcomes = new LinkedList<>();

        RecommendationHistory(String recommendationId) {
            this.recommendationId = recommendationId;
        }
        
        public Deque<RecommendationOutcome> outcomes() {
            return outcomes;
        }

        synchronized void addOutcome(RecommendationOutcome outcome) {
            outcomes.addLast(outcome);
        }

        synchronized List<RecommendationOutcome> getOutcomesInWindow(Duration window) {
            Instant cutoff = Instant.now().minus(window);

            return outcomes.stream()
                    .filter(o -> o.timestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
        }
    }

    // Enums and value objects

    public enum ExpertDecision {
        ACCEPTED,
        REJECTED,
        MODIFIED
    }

    public enum TrendDirection {
        IMPROVING,
        STABLE,
        DECLINING
    }

    public enum ConfidenceBin {
        LOW(0.0, 0.3),
        MEDIUM(0.3, 0.7),
        HIGH(0.7, 1.0);

        private final double min;
        private final double max;

        ConfidenceBin(double min, double max) {
            this.min = min;
            this.max = max;
        }

        static ConfidenceBin fromConfidence(double confidence) {
            for (ConfidenceBin bin : values()) {
                if (confidence >= bin.min && confidence <= bin.max) {
                    return bin;
                }
            }
            return HIGH;
        }
    }

    public record RecommendationOutcome(
            String recommendationId,
            ExpertDecision expertDecision,
            double confidence,
            Instant timestamp
    ) {}

    public record PrecisionRecallMetrics(
            double precision,
            double recall,
            double f1Score,
            int accepted,
            int modified,
            int rejected,
            int totalRecommendations,
            boolean reliable
    ) {
        public static PrecisionRecallMetrics noData() {
            return new PrecisionRecallMetrics(0.0, 0.0, 0.0, 0, 0, 0, 0, false);
        }
    }

    public record ConfidenceCorrelation(
            double correlationCoefficient,
            Map<ConfidenceBin, Double> acceptanceRatesByBin,
            boolean wellCalibrated,
            int sampleSize
    ) {
        public static ConfidenceCorrelation insufficient(int sampleSize) {
            return new ConfidenceCorrelation(0.0, Map.of(), false, sampleSize);
        }
    }

    public record ProblematicRecommendation(
            String recommendationId,
            double confidence,
            ExpertDecision expertDecision,
            String reason
    ) {}

    public record QualityTrend(
            TrendDirection direction,
            double earlyRate,
            double lateRate,
            double change,
            int sampleSize
    ) {
        public static QualityTrend insufficient(int sampleSize) {
            return new QualityTrend(TrendDirection.STABLE, 0.0, 0.0, 0.0, sampleSize);
        }
    }

    public record OverallStatistics(
            int totalRecommendations,
            int accepted,
            int modified,
            int rejected,
            double acceptanceRate,
            double averageConfidence
    ) {
        public static OverallStatistics noData() {
            return new OverallStatistics(0, 0, 0, 0, 0.0, 0.0);
        }
    }

    public record SystemMetrics(
            int totalRecommendations,
            int acceptedRecommendations,
            int rejectedRecommendations,
            int modifiedRecommendations,
            double averageConfidence
    ) {
        public static SystemMetrics noData() {
            return new SystemMetrics(0, 0, 0, 0, 0.0);
        }
    }

    
    /**
     * Gets the outcome for a specific recommendation.
     */
    public Promise<RecommendationOutcome> getOutcome(String recommendationId) {
        return Promise.ofBlocking(eventloop, () -> {
            RecommendationHistory history = recommendationHistories.get(recommendationId);
            if (history == null || history.outcomes().isEmpty()) {
                throw new IllegalArgumentException("No outcome found for recommendation: " + recommendationId);
            }
            RecommendationOutcome latest = history.outcomes().getLast();
            return latest;
        });
    }

    /**
     * Gets system-wide metrics.
     */
    public Promise<SystemMetrics> getSystemMetrics() {
        return Promise.ofBlocking(eventloop, () -> {
            int total = 0, accepted = 0, rejected = 0, modified = 0;
            double totalConfidence = 0.0;

            for (RecommendationHistory history : recommendationHistories.values()) {
                for (RecommendationOutcome outcome : history.outcomes()) {
                    total++;
                    totalConfidence += outcome.confidence();
                    switch (outcome.expertDecision()) {
                        case ACCEPTED -> accepted++;
                        case REJECTED -> rejected++;
                        case MODIFIED -> modified++;
                    }
                }
            }

            double avgConfidence = total > 0 ? totalConfidence / total : 0.0;
            return new SystemMetrics(total, accepted, rejected, modified, avgConfidence);
        });
    }
}
