package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default trend-based predictive analytics engine.
 *
 * @doc.type class
 * @doc.purpose Estimate future event values and confidence from observed event trends
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultPredictiveAnalyticsEngine implements PredictiveAnalyticsEngine {

    private final Map<String, List<Double>> observationsByEventType = new LinkedHashMap<>();

    public void observe(String tenantId, String eventType, double value) {
        observationsByEventType.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(value);
    }

    public PredictionSummary predict(String eventType, long horizonSeconds) {
        List<Double> observations = observationsByEventType.getOrDefault(eventType, List.of());
        if (observations.size() < 5) {
            return new PredictionSummary(eventType, Instant.now(), 0.0, 0.0, observations.size(), horizonSeconds);
        }

        double[] x = new double[observations.size()];
        double[] y = new double[observations.size()];
        double mean = 0.0;
        for (int index = 0; index < observations.size(); index++) {
            x[index] = index;
            y[index] = observations.get(index);
            mean += y[index];
        }
        mean /= observations.size();
        double[] regression = DefaultAdvancedTimeSeriesForecaster.olsRegression(x, y);
        double slope = regression[0];
        double intercept = regression[1];
        double predictedValue = intercept + slope * (observations.size() - 1 + Math.max(1, horizonSeconds / 60.0));

        double relativeSlope = mean == 0.0 ? 0.0 : Math.abs(slope) / Math.abs(mean);
        double confidence;
        if (relativeSlope < 0.02) {
            confidence = 0.65;
        } else if (slope > 0.0) {
            confidence = relativeSlope >= 0.05 ? 0.85 : 0.75;
        } else {
            confidence = relativeSlope >= 0.05 ? 0.55 : 0.60;
        }

        return new PredictionSummary(eventType, Instant.now(), predictedValue, confidence, observations.size(), horizonSeconds);
    }
}