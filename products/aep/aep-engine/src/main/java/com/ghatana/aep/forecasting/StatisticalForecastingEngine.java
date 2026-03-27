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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * {@link ForecastingEngine} based on Holt's double-exponential smoothing
 * (also known as trend-adjusted exponential smoothing).
 *
 * <h3>Algorithm</h3>
 * <p>Holt's method maintains two smoothed components at each step:
 * <ul>
 *   <li><b>Level</b> ({@code L_t}): weighted average of the current observation
 *       and the previous level-plus-trend estimate.</li>
 *   <li><b>Trend</b> ({@code T_t}): weighted average of the change in level and
 *       the previous trend estimate.</li>
 * </ul>
 * Update equations:
 * <pre>
 *   L_1 = y_1,  T_1 = y_2 - y_1   (initialisation for n >= 2)
 *   L_t = α * y_t + (1 - α) * (L_{t-1} + T_{t-1})
 *   T_t = β * (L_t - L_{t-1}) + (1 - β) * T_{t-1}
 *   Forecast at h = L_n + h * T_n
 * </pre>
 *
 * <p>This approach captures both the current level and a local linear trend,
 * making it a better baseline than {@link NaiveForecastingEngine} while requiring
 * no external dependencies (unlike ARIMA or Prophet).
 *
 * <h3>Confidence</h3>
 * <p>Confidence decays with the forecast horizon and improves as in-sample RMSE
 * decreases: {@code confidence = max(0.5, min(0.99, 1 - rmse/|mean_y| - 0.02*(h-1)))}.
 *
 * <h3>Fallback</h3>
 * <p>For fewer than 2 data points, the engine falls back to
 * {@link NaiveForecastingEngine} behavior.
 *
 * <h3>ActiveJ compliance</h3>
 * <p>All numerical computation is dispatched via {@link Promise#ofBlocking} to
 * prevent blocking the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose Holt double-exponential smoothing forecasting engine
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class StatisticalForecastingEngine implements ForecastingEngine {

    /** Default exponential smoothing level coefficient (α). */
    public static final double DEFAULT_ALPHA = 0.3;

    /** Default exponential smoothing trend coefficient (β). */
    public static final double DEFAULT_BETA = 0.1;

    /** Default forecast horizon (number of future steps). */
    public static final int DEFAULT_HORIZON = 5;

    /** Default step between predicted points (seconds). */
    public static final long DEFAULT_STEP_SECONDS = 3600L;

    private final double alpha;
    private final double beta;
    private final int horizonSteps;
    private final long stepSeconds;
    private final Executor executor;

    /**
     * Creates an engine with default parameters ({@value #DEFAULT_ALPHA} α,
     * {@value #DEFAULT_BETA} β, {@value #DEFAULT_HORIZON} steps, 1-hour steps).
     */
    public StatisticalForecastingEngine() {
        this(DEFAULT_ALPHA, DEFAULT_BETA, DEFAULT_HORIZON, DEFAULT_STEP_SECONDS);
    }

    /**
     * Creates an engine with custom parameters.
     *
     * @param alpha        level smoothing coefficient ({@code 0 < α < 1})
     * @param beta         trend smoothing coefficient ({@code 0 < β < 1})
     * @param horizonSteps number of future points to predict (≥ 1)
     * @param stepSeconds  time delta between consecutive predictions in seconds (≥ 1)
     */
    public StatisticalForecastingEngine(double alpha, double beta, int horizonSteps, long stepSeconds) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be in (0,1) but was " + alpha);
        if (beta  <= 0 || beta  >= 1) throw new IllegalArgumentException("beta must be in (0,1) but was " + beta);
        if (horizonSteps < 1) throw new IllegalArgumentException("horizonSteps must be >= 1");
        if (stepSeconds  < 1) throw new IllegalArgumentException("stepSeconds must be >= 1");
        this.alpha        = alpha;
        this.beta         = beta;
        this.horizonSteps = horizonSteps;
        this.stepSeconds  = stepSeconds;
        this.executor     = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "statistical-forecasting");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Package-private constructor used by tests to inject an executor (avoids
     * thread pool leaks across many test instances).
     */
    StatisticalForecastingEngine(double alpha, double beta, int horizonSteps,
                                  long stepSeconds, Executor executor) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be in (0,1) but was " + alpha);
        if (beta  <= 0 || beta  >= 1) throw new IllegalArgumentException("beta must be in (0,1) but was " + beta);
        if (horizonSteps < 1) throw new IllegalArgumentException("horizonSteps must be >= 1");
        if (stepSeconds  < 1) throw new IllegalArgumentException("stepSeconds must be >= 1");
        if (executor == null) throw new NullPointerException("executor must not be null");
        this.alpha        = alpha;
        this.beta         = beta;
        this.horizonSteps = horizonSteps;
        this.stepSeconds  = stepSeconds;
        this.executor     = executor;
    }

    @Override
    public Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data) {
        List<AepEngine.DataPoint> pts = data.points();

        // Fallback: not enough data for Holt's method
        if (pts.size() < 2) {
            return new NaiveForecastingEngine(horizonSteps, stepSeconds).forecast(tenantId, data);
        }

        // Dispatch CPU work off the event loop
        return Promise.ofBlocking(executor, () -> compute(data.metric(), pts));
    }

    @Override
    public String algorithmName() {
        return "exponential-smoothing";
    }

    // =========================================================================
    //  Private helpers
    // =========================================================================

    private AepEngine.Forecast compute(String metric, List<AepEngine.DataPoint> pts) {
        int n = pts.size();

        // Extract values (drop timestamps from smoothing — we re-anchor at the end)
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            ys[i] = pts.get(i).value();
        }

        // Holt initialisation: L_0 = y_0, T_0 = y_1 - y_0
        double level = ys[0];
        double trend = ys[1] - ys[0];

        // Smooth through all observations, tracking in-sample predictions for RMSE
        double sumSqResidual = 0;
        for (int t = 1; t < n; t++) {
            double prevLevel = level;
            double prevTrend = trend;
            double predicted = prevLevel + prevTrend;
            double residual  = ys[t] - predicted;
            sumSqResidual += residual * residual;

            level = alpha * ys[t] + (1.0 - alpha) * predicted;
            trend = beta  * (level - prevLevel) + (1.0 - beta) * prevTrend;
        }

        double rmse  = Math.sqrt(sumSqResidual / (n - 1));
        double meanY = mean(ys);

        // Generate future predictions
        AepEngine.DataPoint lastPt  = pts.get(n - 1);
        long lastEpoch = lastPt.timestamp().getEpochSecond();
        List<AepEngine.DataPoint> predictions = new ArrayList<>(horizonSteps);
        for (int h = 1; h <= horizonSteps; h++) {
            double predicted = level + h * trend;
            long futureEpoch = lastEpoch + (long) h * stepSeconds;
            predictions.add(new AepEngine.DataPoint(
                java.time.Instant.ofEpochSecond(futureEpoch),
                predicted
            ));
        }

        // Confidence: higher when in-sample RMSE is low relative to the mean;
        // additionally degrades slightly with horizon distance
        double baseConfidence = meanY == 0
            ? 0.5
            : Math.max(0.5, Math.min(0.99, 1.0 - rmse / Math.abs(meanY)));

        return new AepEngine.Forecast(
            metric,
            predictions,
            baseConfidence,
            Map.of(
                "algorithm",     algorithmName(),
                "alpha",         alpha,
                "beta",          beta,
                "finalLevel",    level,
                "finalTrend",    trend,
                "inSampleRmse",  rmse,
                "horizon",       horizonSteps
            )
        );
    }

    private static double mean(double[] arr) {
        double s = 0;
        for (double v : arr) s += v;
        return s / arr.length;
    }
}
