/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.forecasting;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link ForecastingEngine} that fits a simple linear trend (ordinary least-squares)
 * to the historical data and extrapolates forward.
 *
 * <h3>Algorithm</h3>
 * <p>Given N data points {@code (t_i, y_i)}, this engine computes the OLS regression
 * coefficients {@code (slope, intercept)} of {@code y} against normalized time
 * {@code t' = t_i - t_0} (avoiding large timestamp values that could reduce precision).
 * Future steps are predicted as {@code y_pred = intercept + slope * t'}.
 *
 * <p>Confidence is inversely proportional to the normalized residual sum of squares:
 * {@code confidence = max(0.5, 1 - RMSE/mean_y)} (clamped to [0.5, 0.99]).
 *
 * <h3>Fallback</h3>
 * <p>When fewer than 2 distinct time points exist, the engine falls back to
 * {@link NaiveForecastingEngine} behaviour (flat forecast from the last value).
 *
 * @doc.type class
 * @doc.purpose Linear-trend time-series forecasting (OLS regression)
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class LinearTrendForecastingEngine implements ForecastingEngine {

    /** Default forecast horizon. */
    public static final int DEFAULT_HORIZON = 5;

    /** Default step between predicted points in seconds. */
    public static final long DEFAULT_STEP_SECONDS = 3600L;

    private final int horizonSteps;
    private final long stepSeconds;

    /** Create with default horizon and step. */
    public LinearTrendForecastingEngine() {
        this(DEFAULT_HORIZON, DEFAULT_STEP_SECONDS);
    }

    /**
     * @param horizonSteps number of future data points to produce; ≥ 1
     * @param stepSeconds  seconds between consecutive predictions; ≥ 1
     */
    public LinearTrendForecastingEngine(int horizonSteps, long stepSeconds) {
        if (horizonSteps < 1) throw new IllegalArgumentException("horizonSteps must be >= 1");
        if (stepSeconds < 1)  throw new IllegalArgumentException("stepSeconds must be >= 1");
        this.horizonSteps = horizonSteps;
        this.stepSeconds  = stepSeconds;
    }

    @Override
    public Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data) {
        List<AepEngine.DataPoint> pts = data.points();

        if (pts.isEmpty()) {
            return Promise.of(new AepEngine.Forecast(
                data.metric(), List.of(), 0.5, Map.of("algorithm", algorithmName())));
        }
        if (pts.size() == 1) {
            // Fallback to naive for single-point series
            return new NaiveForecastingEngine(horizonSteps, stepSeconds)
                .forecast(tenantId, data);
        }

        // OLS: normalize time relative to first point to avoid precision issues
        long t0 = pts.get(0).timestamp().getEpochSecond();
        double[] xs = new double[pts.size()];
        double[] ys = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            xs[i] = pts.get(i).timestamp().getEpochSecond() - t0;
            ys[i] = pts.get(i).value();
        }

        double meanX = mean(xs);
        double meanY = mean(ys);
        double numerator   = 0;
        double denominator = 0;
        for (int i = 0; i < xs.length; i++) {
            numerator   += (xs[i] - meanX) * (ys[i] - meanY);
            denominator += (xs[i] - meanX) * (xs[i] - meanX);
        }

        double slope     = denominator == 0 ? 0 : numerator / denominator;
        double intercept = meanY - slope * meanX;

        // Generate predictions
        AepEngine.DataPoint lastPt = pts.get(pts.size() - 1);
        long lastEpoch = lastPt.timestamp().getEpochSecond();
        List<AepEngine.DataPoint> predictions = new ArrayList<>(horizonSteps);
        for (int i = 1; i <= horizonSteps; i++) {
            long futureEpoch = lastEpoch + (long) i * stepSeconds;
            double normalizedT = futureEpoch - t0;
            double predicted   = intercept + slope * normalizedT;
            predictions.add(new AepEngine.DataPoint(
                java.time.Instant.ofEpochSecond(futureEpoch),
                predicted
            ));
        }

        // Confidence: based on normalized RMSE
        double rmse = computeRmse(xs, ys, slope, intercept);
        double confidence = meanY == 0
            ? 0.5
            : Math.min(0.99, Math.max(0.5, 1.0 - rmse / Math.abs(meanY)));

        return Promise.of(new AepEngine.Forecast(
            data.metric(),
            predictions,
            confidence,
            Map.of(
                "algorithm", algorithmName(),
                "slope",     slope,
                "intercept", intercept,
                "horizon",   horizonSteps
            )
        ));
    }

    @Override
    public String algorithmName() {
        return "linear-trend";
    }

    // ---- helpers ----------------------------------------------------------------

    private static double mean(double[] arr) {
        double s = 0;
        for (double v : arr) s += v;
        return s / arr.length;
    }

    private static double computeRmse(double[] xs, double[] ys, double slope, double intercept) {
        double sum = 0;
        for (int i = 0; i < xs.length; i++) {
            double residual = ys[i] - (intercept + slope * xs[i]);
            sum += residual * residual;
        }
        return Math.sqrt(sum / xs.length);
    }
}
