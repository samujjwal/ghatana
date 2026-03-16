/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import com.ghatana.datacloud.spi.EventView;

import java.util.ArrayList;
import java.util.Deque;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Statistical real-time anomaly detection using a z-score algorithm over a
 * sliding baseline window.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Maintain a per-{@code (tenant, eventType)} sliding window of the last
 *       {@value #WINDOW_SIZE} event numeric payload values (field
 *       {@value #VALUE_FIELD}) or event-count increments.</li>
 *   <li>When a new event arrives, compute the z-score of its value against the
 *       window's mean/stddev.</li>
 *   <li>If the z-score exceeds {@value #Z_SCORE_THRESHOLD} the event is flagged
 *       as anomalous.</li>
 * </ol>
 *
 * <p>Thread-safe: all mutable state lives in {@link ConcurrentHashMap} with
 * per-entry {@code synchronized} blocks on the {@link Deque}.
 *
 * @doc.type class
 * @doc.purpose Statistical z-score anomaly detection over a sliding event window
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultRealTimeAnomalyDetectionEngine implements RealTimeAnomalyDetectionEngine {

    /** Default sliding window size for baseline computation. */
    static final int WINDOW_SIZE = 100;

    /** Z-score threshold above which an event is considered anomalous. */
    static final double Z_SCORE_THRESHOLD = 3.0;

    /** Payload field used as the numeric signal; falls back to event count. */
    static final String VALUE_FIELD = "value";

    /** Minimum window entries before raising anomalies (warm-up phase). */
    static final int MIN_WINDOW_FOR_DETECTION = 10;

    // Key: "tenantId:eventType"  →  sliding window of recent numeric values
    private final ConcurrentHashMap<String, Deque<Double>> windows = new ConcurrentHashMap<>();

    @Override
    public List<AnalyticsEngine.AnomalyResult> detect(EventView event) {
        if (event == null) return List.of();

        String key = windowKey(event);
        double value = extractValue(event);

        List<AnalyticsEngine.AnomalyResult> anomalies = new ArrayList<>();
        Deque<Double> window = windows.computeIfAbsent(key, k -> new LinkedList<>());

        synchronized (window) {
            // warm-up: add without checking
            if (window.size() < MIN_WINDOW_FOR_DETECTION) {
                window.addLast(value);
                if (window.size() > WINDOW_SIZE) window.pollFirst();
                return List.of();
            }

            DoubleSummaryStatistics stats = window.stream()
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();
            double mean = stats.getAverage();
            double stddev = stdDev(window, mean);

            if (stddev > 0) {
                double zScore = Math.abs((value - mean) / stddev);
                if (zScore >= Z_SCORE_THRESHOLD) {
                    anomalies.add(new AnalyticsEngine.AnomalyResult(
                            UUID.randomUUID().toString(),
                            event.getEventTypeName(),
                            Math.min(1.0, zScore / (Z_SCORE_THRESHOLD * 2)), // normalize [0,1]
                            String.format("Z-score %.2f exceeds threshold %.1f (value=%.3f mean=%.3f σ=%.3f)",
                                    zScore, Z_SCORE_THRESHOLD, value, mean, stddev),
                            event.getCreatedAt() != null ? event.getCreatedAt() : java.time.Instant.now()
                    ));
                }
            }

            // slide window
            window.addLast(value);
            if (window.size() > WINDOW_SIZE) window.pollFirst();
        }

        return anomalies;
    }

    @Override
    public void updateBaseline(List<EventView> events) {
        if (events == null || events.isEmpty()) return;
        for (EventView event : events) {
            String key = windowKey(event);
            double value = extractValue(event);
            Deque<Double> window = windows.computeIfAbsent(key, k -> new LinkedList<>());
            synchronized (window) {
                window.addLast(value);
                if (window.size() > WINDOW_SIZE) window.pollFirst();
            }
        }
    }

    /** Returns the number of active baseline windows (for testing/observability). */
    public int activeWindowCount() {
        return windows.size();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String windowKey(EventView event) {
        String tenant = event.getTenantId() != null ? event.getTenantId() : "unknown";
        String type   = event.getEventTypeName() != null ? event.getEventTypeName() : "unknown";
        return tenant + ":" + type;
    }

    private static double extractValue(EventView event) {
        Map<String, Object> data = event.getData();
        if (data != null) {
            Object raw = data.get(VALUE_FIELD);
            if (raw instanceof Number n) return n.doubleValue();
            if (raw instanceof String s) {
                try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
            }
        }
        // Fall back to the number of data fields as a simple cardinality signal
        return data != null ? data.size() : 1.0;
    }

    private static double stdDev(Deque<Double> window, double mean) {
        double sumSq = 0;
        for (double v : window) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / window.size());
    }
}
