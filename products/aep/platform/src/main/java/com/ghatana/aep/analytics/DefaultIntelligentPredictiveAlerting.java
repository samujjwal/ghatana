/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Threshold + trend-based predictive alerting engine.
 *
 * <p>For each metric, maintains a sliding window of recent observations.
 * When {@link #evaluate} is called:
 * <ol>
 *   <li>Records the current value in the window.</li>
 *   <li>Computes a linear trend (slope) over the window.</li>
 *   <li>Projects the value {@value #LOOKAHEAD_STEPS} steps into the future.</li>
 *   <li>If the projected value would cross a configured threshold, or if the
 *       current value already exceeds the hard-limit threshold, returns an
 *       {@link AlertResult}.</li>
 * </ol>
 *
 * <h3>Default thresholds</h3>
 * Callers can supply {@code "threshold"} and {@code "hardLimit"} in the
 * {@code context} map to override the per-metric defaults.
 *
 * @doc.type class
 * @doc.purpose Trend-projection predictive alerting for operational metrics
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultIntelligentPredictiveAlerting implements IntelligentPredictiveAlerting {

    static final int    WINDOW_SIZE      = 30;   // observations
    static final int    LOOKAHEAD_STEPS  = 10;   // project N steps ahead
    static final double DEFAULT_THRESHOLD = 0.80; // 80% of hard limit by default

    // key: metricName  →  sliding window of values
    private final ConcurrentHashMap<String, Deque<Double>> windows = new ConcurrentHashMap<>();

    @Override
    public AlertResult evaluate(String metricName, double currentValue, Map<String, Object> context) {
        if (metricName == null) return null;

        // Record current observation
        Deque<Double> window = windows.computeIfAbsent(metricName, k -> new LinkedList<>());
        synchronized (window) {
            window.addLast(currentValue);
            if (window.size() > WINDOW_SIZE) window.pollFirst();
        }

        double threshold = extractDouble(context, "threshold", DEFAULT_THRESHOLD);
        double hardLimit = extractDouble(context, "hardLimit",  1.0);
        double warnLevel = threshold * hardLimit;

        // Immediate hard-limit breach
        if (currentValue >= hardLimit) {
            return new AlertResult(
                    UUID.randomUUID().toString(), metricName, "CRITICAL",
                    String.format("Metric '%s' breached hard limit: %.4f >= %.4f",
                            metricName, currentValue, hardLimit),
                    currentValue,
                    Instant.now());
        }

        // Trend-based lookahead
        double[] obs;
        synchronized (window) {
            obs = window.stream().mapToDouble(Double::doubleValue).toArray();
        }

        if (obs.length < 3) return null; // not enough data for trend

        double slope = linearSlope(obs);
        double projected = currentValue + slope * LOOKAHEAD_STEPS;

        if (projected >= hardLimit) {
            return new AlertResult(
                    UUID.randomUUID().toString(), metricName, "HIGH",
                    String.format("Metric '%s' projected to breach hard limit in ~%d steps: projected=%.4f (current=%.4f slope=%.4f)",
                            metricName, LOOKAHEAD_STEPS, projected, currentValue, slope),
                    projected,
                    Instant.now().plusSeconds(LOOKAHEAD_STEPS * 60L));
        }

        if (projected >= warnLevel || currentValue >= warnLevel) {
            return new AlertResult(
                    UUID.randomUUID().toString(), metricName, "MEDIUM",
                    String.format("Metric '%s' approaching threshold %.1f%% of limit: current=%.4f projected=%.4f",
                            metricName, threshold * 100, currentValue, projected),
                    projected,
                    Instant.now().plusSeconds(LOOKAHEAD_STEPS * 60L));
        }

        return null; // no alert warranted
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static double extractDouble(Map<String, Object> ctx, String key, double defaultVal) {
        if (ctx == null) return defaultVal;
        Object v = ctx.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
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
