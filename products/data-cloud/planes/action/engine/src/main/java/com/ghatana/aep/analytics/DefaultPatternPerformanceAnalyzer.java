package com.ghatana.aep.analytics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default in-process pattern performance analyzer.
 *
 * @doc.type class
 * @doc.purpose Track precision, recall, F1, and error-rate metrics for pattern execution
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class DefaultPatternPerformanceAnalyzer {

    private final Map<String, PatternStats> statsByPattern = new LinkedHashMap<>();

    public void recordExecution(String patternId, boolean matched, boolean groundTruthPositive) {
        PatternStats stats = statsByPattern.computeIfAbsent(patternId, ignored -> new PatternStats());
        stats.executions += 1;
        if (matched && groundTruthPositive) {
            stats.truePositives += 1;
        } else if (matched) {
            stats.falsePositives += 1;
        } else if (groundTruthPositive) {
            stats.falseNegatives += 1;
        }
    }

    public void recordError(String patternId) {
        PatternStats stats = statsByPattern.computeIfAbsent(patternId, ignored -> new PatternStats());
        stats.errors += 1;
    }

    public Map<String, Double> analyzePerformance(String patternId, AnalyticsEngine.TimeRange range) {
        if (patternId == null) {
            return Map.of();
        }

        PatternStats stats = statsByPattern.getOrDefault(patternId, new PatternStats());
        double tp = stats.truePositives;
        double fp = stats.falsePositives;
        double fn = stats.falseNegatives;
        double precision = tp + fp == 0.0 ? 0.0 : tp / (tp + fp);
        double recall = tp + fn == 0.0 ? 0.0 : tp / (tp + fn);
        double f1 = precision + recall == 0.0 ? 0.0 : (2.0 * precision * recall) / (precision + recall);
        double accuracy = stats.executions == 0 ? 0.0 : tp / stats.executions;
        double errorRate = stats.executions == 0 ? 0.0 : (double) stats.errors / stats.executions;

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("accuracy", accuracy);
        metrics.put("precision", precision);
        metrics.put("recall", recall);
        metrics.put("f1_score", f1);
        metrics.put("error_rate", errorRate);
        metrics.put("total_executions", (double) stats.executions);
        return metrics;
    }

    private static final class PatternStats {
        private long executions;
        private long truePositives;
        private long falsePositives;
        private long falseNegatives;
        private long errors;
    }
}