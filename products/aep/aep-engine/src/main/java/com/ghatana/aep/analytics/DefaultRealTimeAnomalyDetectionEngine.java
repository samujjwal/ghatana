package com.ghatana.aep.analytics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default in-process anomaly detector backed by tenant-scoped rolling windows.
 *
 * @doc.type class
 * @doc.purpose Detect anomalous event values using rolling z-score windows
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultRealTimeAnomalyDetectionEngine {

    private static final int WINDOW_SIZE = 100;
    private static final int MIN_WINDOW_FOR_DETECTION = 10;
    private static final double Z_SCORE_THRESHOLD = 3.0;

    private final Map<String, Deque<Double>> windows = new LinkedHashMap<>();

    public List<AnalyticsEngine.AnomalyResult> detect(AnalyticsEngine.EventObservation event) {
        if (event == null) {
            return List.of();
        }

        Object rawValue = event.getData().get("value");
        if (!(rawValue instanceof Number number)) {
            return List.of();
        }

        String windowKey = event.getTenantId() + ":" + event.getEventType();
        Deque<Double> window = windows.computeIfAbsent(windowKey, key -> new ArrayDeque<>());
        double value = number.doubleValue();

        List<AnalyticsEngine.AnomalyResult> results = new ArrayList<>();
        if (window.size() >= MIN_WINDOW_FOR_DETECTION) {
            double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = window.stream()
                .mapToDouble(entry -> (entry - mean) * (entry - mean))
                .average()
                .orElse(0.0);
            double stdDev = Math.sqrt(variance);
            if (stdDev > 0.0) {
                double zScore = Math.abs(value - mean) / stdDev;
                if (zScore >= Z_SCORE_THRESHOLD) {
                    results.add(new AnalyticsEngine.AnomalyResult(
                        event.getEventType(),
                        zScore,
                        "z-score threshold exceeded"));
                }
            }
        }

        window.addLast(value);
        while (window.size() > WINDOW_SIZE) {
            window.removeFirst();
        }

        return results;
    }

    public void updateBaseline(List<AnalyticsEngine.EventObservation> events) {
        if (events == null) {
            return;
        }
        for (AnalyticsEngine.EventObservation event : events) {
            detect(event);
        }
    }

    public int activeWindowCount() {
        return windows.size();
    }
}