/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

/**
 * Predictive analytics engine interface for AnalyticsEngine.
 *
 * <p>Provides ML-based prediction capabilities for the analytics engine facade.
 * The full implementation resides in
 * {@code com.ghatana.aep.expertinterface.analytics.PredictiveAnalyticsEngine}.
 *
 * @doc.type interface
 * @doc.purpose Analytics engine prediction abstraction
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface PredictiveAnalyticsEngine {

    /**
     * Generates predictions for the given event type.
     *
     * @param eventType the event type to predict
     * @param horizon   prediction horizon in seconds
     * @return prediction result
     */
    default PredictionSummary predict(String eventType, long horizon) {
        return new PredictionSummary(eventType, 0.0, "No prediction available");
    }

    /**
     * Prediction summary record.
     */
    record PredictionSummary(String eventType, double confidence, String description) {}
}
