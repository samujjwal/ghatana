/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-process pattern performance analyzer that tracks per-pattern execution
 * outcomes and derives accuracy, precision, recall, and F1 metrics.
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li>Call {@link #recordExecution(String, boolean, boolean)} after each
 *       pattern match attempt.</li>
 *   <li>Call {@link #analyzePerformance(String, AnalyticsEngine.TimeRange)} to
 *       retrieve the latest derived metrics.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Pattern execution accuracy and classification-metric tracking
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultPatternPerformanceAnalyzer implements PatternPerformanceAnalyzer {

    // Per-pattern execution counters
    private final ConcurrentHashMap<String, LongAdder> executions  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> truePos     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> falsePos    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> falseNeg    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> errors      = new ConcurrentHashMap<>();

    /**
     * Records one pattern execution result.
     *
     * @param patternId      pattern identifier
     * @param matched        whether the pattern produced a match
     * @param groundTruth    whether there was a true event (for precision/recall)
     */
    public void recordExecution(String patternId, boolean matched, boolean groundTruth) {
        if (patternId == null) return;
        executions.computeIfAbsent(patternId, k -> new LongAdder()).increment();
        if  (matched && groundTruth)  truePos .computeIfAbsent(patternId, k -> new LongAdder()).increment();
        if  (matched && !groundTruth) falsePos.computeIfAbsent(patternId, k -> new LongAdder()).increment();
        if (!matched && groundTruth)  falseNeg.computeIfAbsent(patternId, k -> new LongAdder()).increment();
    }

    /** Records an execution error. */
    public void recordError(String patternId) {
        if (patternId == null) return;
        errors.computeIfAbsent(patternId, k -> new LongAdder()).increment();
    }

    @Override
    public Map<String, Double> analyzePerformance(String patternId, AnalyticsEngine.TimeRange timeRange) {
        Map<String, Double> metrics = new HashMap<>();

        if (patternId == null) return metrics;

        long tp   = longValue(truePos,  patternId);
        long fp   = longValue(falsePos, patternId);
        long fn   = longValue(falseNeg, patternId);
        long exec = longValue(executions, patternId);
        long err  = longValue(errors, patternId);

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall    = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
        double f1        = (precision + recall) > 0
                ? 2.0 * precision * recall / (precision + recall) : 0.0;
        double accuracy  = exec > 0 ? (double) (tp + (exec - tp - fp - fn)) / exec : 0.0;
        double errorRate = exec > 0 ? (double) err / exec : 0.0;

        metrics.put("accuracy",     clamp(accuracy));
        metrics.put("precision",    clamp(precision));
        metrics.put("recall",       clamp(recall));
        metrics.put("f1_score",     clamp(f1));
        metrics.put("error_rate",   clamp(errorRate));
        metrics.put("total_executions", (double) exec);
        metrics.put("true_positives",   (double) tp);
        metrics.put("false_positives",  (double) fp);
        metrics.put("false_negatives",  (double) fn);
        return metrics;
    }

    private static long longValue(ConcurrentHashMap<String, LongAdder> map, String key) {
        LongAdder a = map.get(key);
        return a != null ? a.longValue() : 0L;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
