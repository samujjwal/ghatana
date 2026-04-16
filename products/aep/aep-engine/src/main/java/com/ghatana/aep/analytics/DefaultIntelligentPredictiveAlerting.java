package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Default predictive alerting engine for trending metrics.
 *
 * @doc.type class
 * @doc.purpose Produce medium, high, and critical alerts from current and projected metric values
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultIntelligentPredictiveAlerting implements IntelligentPredictiveAlerting {

    private static final int HISTORY_LIMIT = 16;
    private final Map<String, Deque<Double>> historyByMetric = new LinkedHashMap<>();

    public AlertResult evaluate(String metricName, double currentValue, Map<String, Object> context) {
        if (metricName == null) {
            return null;
        }

        double hardLimit = toDouble(context != null ? context.get("hardLimit") : null, 100.0);
        double threshold = toDouble(context != null ? context.get("threshold") : null, 0.8);

        Deque<Double> history = historyByMetric.computeIfAbsent(metricName, ignored -> new ArrayDeque<>());
        double slope = estimateSlope(history, currentValue);

        history.addLast(currentValue);
        while (history.size() > HISTORY_LIMIT) {
            history.removeFirst();
        }

        if (currentValue >= hardLimit) {
            return alert(metricName, "CRITICAL", currentValue, hardLimit, 0);
        }

        double projectedValue = currentValue + (slope * 10);
        if (projectedValue >= hardLimit) {
            return alert(metricName, "HIGH", projectedValue, hardLimit, 600);
        }

        if (currentValue >= hardLimit * threshold) {
            return alert(metricName, "MEDIUM", currentValue, hardLimit, 900);
        }

        return null;
    }

    private static AlertResult alert(String metricName, String severity, double value, double hardLimit, int secondsAhead) {
        return new AlertResult(
            UUID.randomUUID().toString(),
            metricName,
            severity,
            "Metric '" + metricName + "' is at " + value + " against hard limit " + hardLimit,
            Instant.now().plusSeconds(secondsAhead));
    }

    private static double estimateSlope(Deque<Double> history, double currentValue) {
        if (history.isEmpty()) {
            return 0.0;
        }
        Double first = history.peekFirst();
        if (first == null) {
            return 0.0;
        }
        return (currentValue - first) / Math.max(1, history.size());
    }

    private static double toDouble(Object rawValue, double defaultValue) {
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        if (rawValue == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(rawValue));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}