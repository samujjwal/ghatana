/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.util.Map;

/**
 * Pattern performance analyzer interface for AnalyticsEngine.
 *
 * <p>Provides pattern performance tracking for the analytics engine facade.
 * The full implementation resides in
 * {@code com.ghatana.aep.expertinterface.analytics.PatternPerformanceAnalyzer}.
 *
 * @doc.type interface
 * @doc.purpose Analytics engine pattern performance abstraction
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface PatternPerformanceAnalyzer {

    /**
     * Analyzes performance of a specific pattern.
     *
     * @param patternId the pattern identifier
     * @param timeRange the time range to analyze
     * @return performance metrics
     */
    default Map<String, Double> analyzePerformance(String patternId, AnalyticsEngine.TimeRange timeRange) {
        return Map.of(
            "accuracy", 0.0,
            "precision", 0.0,
            "recall", 0.0,
            "f1_score", 0.0
        );
    }
}
