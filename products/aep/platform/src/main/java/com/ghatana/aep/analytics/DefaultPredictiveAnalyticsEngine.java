/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trend-based predictive analytics engine.
 *
 * <p>Maintains a per-(tenant, eventType) sliding window of observed event counts
 * per time bucket and fits a simple linear trend.  The resulting trend slope is
 * used to derive a confidence score and qualitative prediction description.
 *
 * <h3>Confidence scoring</h3>
 * <ul>
 *   <li>&lt; 5 observations → {@code 0.0} (not enough data)</li>
 *   <li>Strong upward slope (slope / mean &gt; 0.5) → ≥ 0.85</li>
 *   <li>Strong downward slope → 0.20–0.40</li>
 *   <li>Stable trend → 0.60–0.75</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Trend-based event volume prediction
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultPredictiveAnalyticsEngine implements PredictiveAnalyticsEngine {

    private static final int WINDOW_SIZE = 60; // buckets (observations)
    private static final int MIN_OBSERVATIONS = 5;

    // Key: "tenantId:eventType" → sliding window of observation counts
    private final ConcurrentHashMap<String, Deque<Double>> windows = new ConcurrentHashMap<>();

    /**
     * Records one count observation for a tenant + event type.
     * Call this after each event is processed to keep the model current.
     *
     * @param tenantId  tenant identifier
     * @param eventType event type name
     * @param count     observation value (typically 1.0 per call)
     */
    public void observe(String tenantId, String eventType, double count) {
        String key = tenantId + ":" + eventType;
        Deque<Double> w = windows.computeIfAbsent(key, k -> new LinkedList<>());
        synchronized (w) {
            w.addLast(count);
            if (w.size() > WINDOW_SIZE) w.pollFirst();
        }
    }

    @Override
    public PredictionSummary predict(String eventType, long horizon) {
        if (eventType == null) {
            return new PredictionSummary(null, 0.0, "No event type specified");
        }

        // Find the window with the highest observation count matching this event type
        // (tenant-agnostic lookup via suffix match)
        double[] observations = windowForType(eventType);
        if (observations.length < MIN_OBSERVATIONS) {
            return new PredictionSummary(eventType, 0.0,
                    "Insufficient data (" + observations.length + " observations; need " + MIN_OBSERVATIONS + ")");
        }

        double mean      = mean(observations);
        double slope     = linearSlope(observations);
        double relSlope  = mean > 0 ? slope / mean : 0;

        double confidence;
        String description;

        if (Math.abs(relSlope) < 0.05) {
            confidence  = 0.65;
            description = String.format("Stable trend (slope/mean=%.3f); volume expected to remain around %.1f",
                    relSlope, mean);
        } else if (relSlope >= 0.5) {
            confidence  = 0.88;
            description = String.format("Strong upward trend (slope/mean=%.3f); volume likely to increase significantly",
                    relSlope);
        } else if (relSlope > 0) {
            confidence  = 0.72;
            description = String.format("Moderate upward trend (slope/mean=%.3f)", relSlope);
        } else if (relSlope <= -0.5) {
            confidence  = 0.25;
            description = String.format("Strong downward trend (slope/mean=%.3f); volume likely declining", relSlope);
        } else {
            confidence  = 0.45;
            description = String.format("Moderate downward trend (slope/mean=%.3f)", relSlope);
        }

        return new PredictionSummary(eventType, Math.min(1.0, confidence), description);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private double[] windowForType(String eventType) {
        for (java.util.Map.Entry<String, Deque<Double>> entry : windows.entrySet()) {
            if (entry.getKey().endsWith(":" + eventType)) {
                Deque<Double> w = entry.getValue();
                synchronized (w) {
                    return w.stream().mapToDouble(Double::doubleValue).toArray();
                }
            }
        }
        return new double[0];
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double d : v) s += d;
        return s / v.length;
    }

    private static double linearSlope(double[] y) {
        int n = y.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX  += i;
            sumY  += y[i];
            sumXY += i * y[i];
            sumXX += (double) i * i;
        }
        double denom = n * sumXX - sumX * sumX;
        return Math.abs(denom) < 1e-10 ? 0 : (n * sumXY - sumX * sumY) / denom;
    }
}

