/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Automated anomaly detector for AEP pipeline metrics (AEP-011.3).
 *
 * <p>Uses a statistical Z-score method: a data point is flagged as anomalous when
 * it deviates more than {@code zScoreThreshold} standard deviations from the rolling
 * mean.  Listeners are notified synchronously for each detected anomaly.
 *
 * <p>Target: automated identification of anomalies with observable alerts.
 *
 * @doc.type    class
 * @doc.purpose Z-score anomaly detector for AEP pipeline metrics
 * @doc.layer   product
 * @doc.pattern Observer, Detector
 */
public final class AepAnomalyDetector {

    private static final Logger LOG = LoggerFactory.getLogger(AepAnomalyDetector.class);

    private final double zScoreThreshold;
    private final int rollingWindowSize;

    private final CopyOnWriteArrayList<Consumer<AnomalyEvent>> listeners = new CopyOnWriteArrayList<>();
    private final List<AnomalyEvent> detectedAnomalies =
            Collections.synchronizedList(new ArrayList<>());

    // Rolling window for each series is managed externally via evaluate()

    private AepAnomalyDetector(Builder builder) {
        this.zScoreThreshold   = builder.zScoreThreshold;
        this.rollingWindowSize  = builder.rollingWindowSize;
    }

    // ── Listener registration ─────────────────────────────────────────────────

    /**
     * Registers an anomaly listener.
     *
     * @param listener called synchronously for each detected anomaly
     */
    public void addListener(Consumer<AnomalyEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    /**
     * Evaluates a new data point against the rolling history.
     *
     * @param seriesId   identifier for the metric series (e.g., "tenant:throughput")
     * @param history    recent historical values (oldest first, at least 2 values)
     * @param newValue   the latest observed value
     * @return anomaly event if the value is anomalous, {@code null} otherwise
     */
    public AnomalyEvent evaluate(String seriesId, List<Double> history, double newValue) {
        Objects.requireNonNull(seriesId, "seriesId must not be null");
        Objects.requireNonNull(history,  "history must not be null");

        if (history.size() < 2) return null; // not enough data

        // Use the tail of history up to rollingWindowSize
        List<Double> window = history.size() > rollingWindowSize
                ? history.subList(history.size() - rollingWindowSize, history.size())
                : history;

        double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = window.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) return null; // constant series — no detection possible

        double zScore = Math.abs(newValue - mean) / stdDev;

        if (zScore >= zScoreThreshold) {
            AnomalyEvent event = new AnomalyEvent(
                    seriesId, Instant.now(), newValue, mean, stdDev, zScore,
                    zScore > zScoreThreshold * 1.5
                            ? Severity.CRITICAL : Severity.WARNING,
                    "Value " + newValue + " deviates " + String.format("%.2f", zScore)
                            + " standard deviations from mean " + String.format("%.2f", mean)
            );
            detectedAnomalies.add(event);
            notifyListeners(event);
            LOG.warn("Anomaly detected seriesId={} value={} zScore={:.2f}", seriesId, newValue, zScore);
            return event;
        }

        return null;
    }

    private void notifyListeners(AnomalyEvent event) {
        for (Consumer<AnomalyEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.error("Anomaly listener threw exception: {}", e.getMessage(), e);
            }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns all anomalies detected since construction.
     *
     * @return immutable list of detected anomalies
     */
    public List<AnomalyEvent> detectedAnomalies() {
        return Collections.unmodifiableList(new ArrayList<>(detectedAnomalies));
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /** Anomaly severity level. */
    public enum Severity { WARNING, CRITICAL }

    /**
     * Detected anomaly event.
     *
     * @param seriesId    metric series identifier
     * @param detectedAt  when the anomaly was detected
     * @param observedValue the anomalous observed value
     * @param meanValue   rolling mean at detection time
     * @param stdDev      rolling standard deviation at detection time
     * @param zScore      computed Z-score (&ge; threshold)
     * @param severity    anomaly severity
     * @param description human-readable description
     */
    public record AnomalyEvent(
            String seriesId,
            Instant detectedAt,
            double observedValue,
            double meanValue,
            double stdDev,
            double zScore,
            Severity severity,
            String description
    ) {}

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link AepAnomalyDetector}.
     */
    public static final class Builder {
        private double zScoreThreshold = 3.0;
        private int rollingWindowSize   = 20;

        private Builder() {}

        /**
         * Z-score threshold above which a value is flagged as anomalous.
         *
         * @param threshold positive value (e.g., 3.0 for 3-sigma)
         * @return this builder
         */
        public Builder zScoreThreshold(double threshold) {
            if (threshold <= 0) throw new IllegalArgumentException("threshold must be positive");
            this.zScoreThreshold = threshold;
            return this;
        }

        /**
         * Number of recent values to use for mean/stddev computation.
         *
         * @param size positive integer
         * @return this builder
         */
        public Builder rollingWindowSize(int size) {
            if (size < 2) throw new IllegalArgumentException("rollingWindowSize must be >= 2");
            this.rollingWindowSize = size;
            return this;
        }

        public AepAnomalyDetector build() { return new AepAnomalyDetector(this); }
    }
}

