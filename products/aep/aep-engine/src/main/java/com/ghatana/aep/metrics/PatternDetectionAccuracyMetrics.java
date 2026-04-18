/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.metrics;

import com.ghatana.aep.AepEngine;
import com.ghatana.platform.observability.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for pattern detection accuracy.
 * <p>
 * Tracks true positives, false positives, false negatives, and calculates
 * precision, recall, and F1 score per pattern type.
 *
 * @doc.type class
 * @doc.purpose Track pattern detection accuracy metrics
 * @doc.layer product
 * @doc.pattern Observer
 */
public class PatternDetectionAccuracyMetrics {

    private static final Logger logger = LoggerFactory.getLogger(PatternDetectionAccuracyMetrics.class);

    private final Metrics metrics;
    private final Map<String, PatternAccuracyStats> statsByPatternType = new ConcurrentHashMap<>();

    public PatternDetectionAccuracyMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Record a true positive detection.
     *
     * @param patternType type of pattern (e.g., THRESHOLD, ANOMALY)
     * @param patternId unique pattern identifier
     */
    public void recordTruePositive(String patternType, String patternId) {
        PatternAccuracyStats stats = statsByPatternType.computeIfAbsent(patternType, PatternAccuracyStats::new);
        stats.incrementTruePositives();
        metrics.counter("pattern.detection.true_positive", "type", patternType).increment();
        logger.debug("True positive: type={}, id={}", patternType, patternId);
    }

    /**
     * Record a false positive detection.
     *
     * @param patternType type of pattern
     * @param patternId unique pattern identifier
     */
    public void recordFalsePositive(String patternType, String patternId) {
        PatternAccuracyStats stats = statsByPatternType.computeIfAbsent(patternType, PatternAccuracyStats::new);
        stats.incrementFalsePositives();
        metrics.counter("pattern.detection.false_positive", "type", patternType).increment();
        logger.debug("False positive: type={}, id={}", patternType, patternId);
    }

    /**
     * Record a false negative (pattern should have matched but didn't).
     *
     * @param patternType type of pattern
     * @param patternId unique pattern identifier
     */
    public void recordFalseNegative(String patternType, String patternId) {
        PatternAccuracyStats stats = statsByPatternType.computeIfAbsent(patternType, PatternAccuracyStats::new);
        stats.incrementFalseNegatives();
        metrics.counter("pattern.detection.false_negative", "type", patternType).increment();
        logger.debug("False negative: type={}, id={}", patternType, patternId);
    }

    /**
     * Record a true negative (pattern correctly did not match).
     *
     * @param patternType type of pattern
     * @param patternId unique pattern identifier
     */
    public void recordTrueNegative(String patternType, String patternId) {
        PatternAccuracyStats stats = statsByPatternType.computeIfAbsent(patternType, PatternAccuracyStats::new);
        stats.incrementTrueNegatives();
        metrics.counter("pattern.detection.true_negative", "type", patternType).increment();
        logger.debug("True negative: type={}, id={}", patternType, patternId);
    }

    /**
     * Get statistics for a pattern type.
     *
     * @param patternType type of pattern
     * @return statistics for the pattern type
     */
    public PatternAccuracyStats getStats(String patternType) {
        return statsByPatternType.getOrDefault(patternType, new PatternAccuracyStats());
    }

    /**
     * Get precision for a pattern type.
     *
     * @param patternType type of pattern
     * @return precision (0.0 to 1.0)
     */
    public double getPrecision(String patternType) {
        PatternAccuracyStats stats = statsByPatternType.getOrDefault(patternType, new PatternAccuracyStats());
        return stats.precision();
    }

    /**
     * Get recall for a pattern type.
     *
     * @param patternType type of pattern
     * @return recall (0.0 to 1.0)
     */
    public double getRecall(String patternType) {
        PatternAccuracyStats stats = statsByPatternType.getOrDefault(patternType, new PatternAccuracyStats());
        return stats.recall();
    }

    /**
     * Get F1 score for a pattern type.
     *
     * @param patternType type of pattern
     * @return F1 score (0.0 to 1.0)
     */
    public double getF1Score(String patternType) {
        PatternAccuracyStats stats = statsByPatternType.getOrDefault(patternType, new PatternAccuracyStats());
        return stats.f1Score();
    }

    /**
     * Statistics for pattern detection accuracy.
     */
    public static class PatternAccuracyStats {
        private final AtomicLong truePositives = new AtomicLong(0);
        private final AtomicLong falsePositives = new AtomicLong(0);
        private final AtomicLong falseNegatives = new AtomicLong(0);
        private final AtomicLong trueNegatives = new AtomicLong(0);

        public PatternAccuracyStats() {}

        public void incrementTruePositives() { truePositives.incrementAndGet(); }
        public void incrementFalsePositives() { falsePositives.incrementAndGet(); }
        public void incrementFalseNegatives() { falseNegatives.incrementAndGet(); }
        public void incrementTrueNegatives() { trueNegatives.incrementAndGet(); }

        public long truePositives() { return truePositives.get(); }
        public long falsePositives() { return falsePositives.get(); }
        public long falseNegatives() { return falseNegatives.get(); }
        public long trueNegatives() { return trueNegatives.get(); }

        /**
         * Precision = TP / (TP + FP)
         */
        public double precision() {
            long tp = truePositives.get();
            long fp = falsePositives.get();
            if (tp + fp == 0) return 0.0;
            return (double) tp / (tp + fp);
        }

        /**
         * Recall = TP / (TP + FN)
         */
        public double recall() {
            long tp = truePositives.get();
            long fn = falseNegatives.get();
            if (tp + fn == 0) return 0.0;
            return (double) tp / (tp + fn);
        }

        /**
         * F1 Score = 2 * (precision * recall) / (precision + recall)
         */
        public double f1Score() {
            double precision = precision();
            double recall = recall();
            if (precision + recall == 0) return 0.0;
            return 2 * (precision * recall) / (precision + recall);
        }

        /**
         * Accuracy = (TP + TN) / (TP + TN + FP + FN)
         */
        public double accuracy() {
            long tp = truePositives.get();
            long tn = trueNegatives.get();
            long fp = falsePositives.get();
            long fn = falseNegatives.get();
            long total = tp + tn + fp + fn;
            if (total == 0) return 0.0;
            return (double) (tp + tn) / total;
        }
    }
}
