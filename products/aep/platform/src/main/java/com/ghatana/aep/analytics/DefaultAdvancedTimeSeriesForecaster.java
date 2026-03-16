/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Time series forecaster using simple linear regression (OLS) for trend + optional
 * moving-average residual smoothing.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Fit a least-squares line {@code y = a + b*x} to the historical data points.</li>
 *   <li>Project {@code horizon} equidistant future points along that line.</li>
 *   <li>Detect seasonality via autocorrelation at candidate periods.</li>
 * </ol>
 *
 * <p>Self-contained — no external ML library required.
 *
 * @doc.type class
 * @doc.purpose OLS linear-trend time series forecasting
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultAdvancedTimeSeriesForecaster implements AdvancedTimeSeriesForecaster {

    /** Interval between projected forecast points — 1 minute by default. */
    private static final long STEP_SECONDS = 60L;

    /** Minimum data points required to compute a trend. */
    private static final int MIN_POINTS = 2;

    @Override
    public List<AnalyticsEngine.ForecastPoint> forecast(AnalyticsEngine.TimeSeriesData data, int horizon) {
        if (data == null || data.size() < MIN_POINTS || horizon <= 0) {
            return List.of();
        }

        List<AnalyticsEngine.DataPoint> points = data.getPoints();
        int n = points.size();

        // Convert timestamps to seconds-since-epoch for regression
        double[] x = new double[n];
        double[] y = new double[n];
        long baseEpoch = points.get(0).getTimestamp().getEpochSecond();

        for (int i = 0; i < n; i++) {
            x[i] = points.get(i).getTimestamp().getEpochSecond() - baseEpoch;
            y[i] = points.get(i).getValue();
        }

        double[] coefficients = olsRegression(x, y);
        double slope     = coefficients[0]; // b
        double intercept = coefficients[1]; // a

        // Project future points using the last data-point timestamp as origin
        long lastEpoch = points.get(n - 1).getTimestamp().getEpochSecond();
        List<AnalyticsEngine.ForecastPoint> result = new ArrayList<>(horizon);
        for (int i = 1; i <= horizon; i++) {
            long futureEpoch = lastEpoch + (long) i * STEP_SECONDS;
            double futureX   = futureEpoch - baseEpoch;
            double futureY   = intercept + slope * futureX;
            result.add(new AnalyticsEngine.ForecastPoint(
                    Instant.ofEpochSecond(futureEpoch),
                    futureY));
        }
        return result;
    }

    @Override
    public int detectSeasonality(AnalyticsEngine.TimeSeriesData data) {
        if (data == null || data.size() < 4) return -1;

        List<AnalyticsEngine.DataPoint> points = data.getPoints();
        double[] y = points.stream().mapToDouble(AnalyticsEngine.DataPoint::getValue).toArray();

        // Detrend before autocorrelation
        double mean = mean(y);
        double[] detrended = new double[y.length];
        for (int i = 0; i < y.length; i++) detrended[i] = y[i] - mean;

        // Try candidate periods 2..n/3; return the period with highest autocorrelation
        int n         = y.length;
        int bestPeriod = -1;
        double bestAc  = 0.4; // minimum autocorrelation to be considered seasonal

        for (int lag = 2; lag <= n / 3; lag++) {
            double ac = autocorrelation(detrended, lag);
            if (ac > bestAc) {
                bestAc     = ac;
                bestPeriod = lag;
            }
        }
        return bestPeriod;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Ordinary least squares regression: returns [slope, intercept].
     */
    static double[] olsRegression(double[] x, double[] y) {
        int n    = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX  += x[i];
            sumY  += y[i];
            sumXY += x[i] * y[i];
            sumXX += x[i] * x[i];
        }
        double denom = n * sumXX - sumX * sumX;
        if (Math.abs(denom) < 1e-10) {
            return new double[]{0.0, sumY / n}; // zero slope → return mean
        }
        double slope     = (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;
        return new double[]{slope, intercept};
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double d : v) s += d;
        return s / v.length;
    }

    /**
     * Normalized autocorrelation at the given lag.
     */
    private static double autocorrelation(double[] v, int lag) {
        int n = v.length - lag;
        if (n <= 0) return 0;
        double cov = 0, var = 0;
        for (int i = 0; i < n; i++) {
            cov += v[i] * v[i + lag];
            var += v[i] * v[i];
        }
        return var < 1e-10 ? 0 : cov / var;
    }
}
