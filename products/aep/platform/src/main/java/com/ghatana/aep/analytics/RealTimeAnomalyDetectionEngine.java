/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import com.ghatana.datacloud.spi.EventView;

import java.util.List;

/**
 * Real-time anomaly detection engine interface for AnalyticsEngine.
 *
 * <p>Detects anomalous patterns in event streams in real-time using
 * statistical analysis and machine learning models.
 *
 * @doc.type interface
 * @doc.purpose Real-time anomaly detection abstraction
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface RealTimeAnomalyDetectionEngine {

    /**
     * Detects anomalies in a single event.
     *
     * @param event the event to analyze
     * @return list of detected anomalies
     */
    default List<AnalyticsEngine.AnomalyResult> detect(EventView event) {
        return List.of();
    }

    /**
     * Updates the anomaly detection model with new baseline data.
     *
     * @param events the baseline events
     */
    default void updateBaseline(List<EventView> events) {
        // no-op by default
    }
}
