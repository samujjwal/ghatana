/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.util.Map;

/**
 * Intelligent predictive alerting interface for AnalyticsEngine.
 *
 * <p>Generates proactive alerts based on predictive models that forecast
 * upcoming anomalies, threshold breaches, and capacity issues before
 * they occur.
 *
 * @doc.type interface
 * @doc.purpose Predictive alerting abstraction
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface IntelligentPredictiveAlerting {

    /**
     * Evaluates whether an alert should be raised based on current trends.
     *
     * @param metricName the metric to evaluate
     * @param currentValue current metric value
     * @param context additional context
     * @return alert result, or null if no alert warranted
     */
    default AlertResult evaluate(String metricName, double currentValue, Map<String, Object> context) {
        return null;
    }

    /**
     * Alert result record.
     */
    record AlertResult(
        String alertId,
        String metricName,
        String severity,
        String message,
        double predictedValue,
        java.time.Instant predictedTime
    ) {}
}
