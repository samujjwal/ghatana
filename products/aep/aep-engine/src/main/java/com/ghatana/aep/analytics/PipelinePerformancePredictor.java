/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Pipeline performance predictor for AEP (AEP-011.2).
 *
 * <p>Uses Holt double-exponential smoothing — the same algorithm already used by
 * {@link com.ghatana.aep.forecasting.StatisticalForecastingEngine} — to forecast
 * future pipeline throughput and latency from historical metric series.
 *
 * <p>This class provides the analytics-layer entry point; callers supply time-series
 * data directly (e.g., from {@link com.ghatana.aep.metrics.AepMetricsCollector}).
 *
 * @doc.type    class
 * @doc.purpose Pipeline performance predictor using Holt exponential smoothing
 * @doc.layer   product
 * @doc.pattern Strategy, Analytics
 */
public final class PipelinePerformancePredictor {

    private static final Logger LOG = LoggerFactory.getLogger(PipelinePerformancePredictor.class);

    private static final double DEFAULT_ALPHA = 0.3;
    private static final double DEFAULT_BETA  = 0.1;

    private final double alpha;
    private final double beta;
    private final Executor executor;

    private PipelinePerformancePredictor(Builder builder) {
        this.alpha    = builder.alpha;
        this.beta     = builder.beta;
        this.executor = builder.executor;
    }

    // ── Prediction ─────────────────────────────────────────────────────────────

    /**
     * Forecasts {@code horizon} steps ahead from the supplied time-series.
     *
     * <p>CPU-bound computation is wrapped in {@link Promise#ofBlocking} to avoid
     * blocking the ActiveJ event loop.
     *
     * @param tenantId   tenant requesting the forecast
     * @param metricName human-readable metric label (e.g., "throughput_ops_per_sec")
     * @param series     ordered historical observations (oldest first); must have &ge;2 values
     * @param horizon    number of future steps to forecast (&gt;0)
     * @return promise of {@link PredictionResult}; never {@code null}
     */
    public Promise<PredictionResult> predict(String tenantId, String metricName,
                                              List<Double> series, int horizon) {
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        Objects.requireNonNull(metricName, "metricName must not be null");
        Objects.requireNonNull(series,     "series must not be null");
        if (horizon <= 0) throw new IllegalArgumentException("horizon must be positive");
        if (series.size() < 2)
            return Promise.ofException(new IllegalArgumentException(
                    "At least 2 data points required for prediction"));

        return Promise.ofBlocking(executor, () -> doPredict(tenantId, metricName, series, horizon));
    }

    private PredictionResult doPredict(String tenantId, String metricName,
                                        List<Double> series, int horizon) {
        // Holt double-exponential smoothing
        double level = series.get(0);
        double trend = series.get(1) - series.get(0);

        double sumSqError = 0.0;

        for (int i = 1; i < series.size(); i++) {
            double y = series.get(i);
            double prevLevel = level;
            level = alpha * y + (1 - alpha) * (prevLevel + trend);
            trend = beta  * (level - prevLevel) + (1 - beta) * trend;
            double predicted = prevLevel + trend;
            sumSqError += (y - predicted) * (y - predicted);
        }

        double rmse = Math.sqrt(sumSqError / (series.size() - 1));
        double mean = series.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double confidence = mean == 0 ? 0.5
                : Math.max(0.5, Math.min(0.99, 1.0 - rmse / Math.abs(mean) - 0.02 * (horizon - 1)));

        double[] forecastValues = new double[horizon];
        for (int h = 1; h <= horizon; h++) {
            forecastValues[h - 1] = level + h * trend;
        }

        LOG.debug("Prediction tenant={} metric={} horizon={} confidence={:.2f}",
                tenantId, metricName, horizon, confidence);

        return new PredictionResult(tenantId, metricName, Instant.now(),
                series.size(), forecastValues, confidence, rmse);
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Prediction result.
     *
     * @param tenantId       tenant identifier
     * @param metricName     metric that was forecast
     * @param generatedAt    when the forecast was produced
     * @param trainingPoints number of historical data points used
     * @param forecastValues predicted values for each horizon step
     * @param confidence     estimated forecast confidence [0.5, 0.99]
     * @param rmse           root-mean-squared error on training data
     */
    public record PredictionResult(
            String tenantId,
            String metricName,
            Instant generatedAt,
            int trainingPoints,
            double[] forecastValues,
            double confidence,
            double rmse
    ) {
        /** Returns the one-step-ahead forecast. */
        public double nextStepForecast() {
            return forecastValues.length > 0 ? forecastValues[0] : Double.NaN;
        }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link PipelinePerformancePredictor}.
     */
    public static final class Builder {
        private double alpha   = DEFAULT_ALPHA;
        private double beta    = DEFAULT_BETA;
        private Executor executor = Executors.newVirtualThreadPerTaskExecutor();

        private Builder() {}

        public Builder alpha(double alpha) {
            if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be in (0, 1)");
            this.alpha = alpha;
            return this;
        }

        public Builder beta(double beta) {
            if (beta <= 0 || beta >= 1) throw new IllegalArgumentException("beta must be in (0, 1)");
            this.beta = beta;
            return this;
        }

        /** Injectable executor for tests. */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor must not be null");
            return this;
        }

        public PipelinePerformancePredictor build() { return new PipelinePerformancePredictor(this); }
    }
}

