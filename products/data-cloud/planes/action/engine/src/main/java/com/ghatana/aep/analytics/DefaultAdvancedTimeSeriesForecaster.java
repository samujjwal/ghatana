package com.ghatana.aep.analytics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Default linear forecaster with lightweight seasonality detection.
 *
 * @doc.type class
 * @doc.purpose Forecast time-series values using OLS trend fitting and simple seasonality heuristics
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultAdvancedTimeSeriesForecaster {

    public List<AnalyticsEngine.ForecastPoint> forecast(AnalyticsEngine.TimeSeriesData series, int horizon) {
        if (series == null || horizon <= 0 || series.getPoints().isEmpty()) {
            return List.of();
        }

        List<AnalyticsEngine.DataPoint> points = series.getPoints();
        double[] x = new double[points.size()];
        double[] y = new double[points.size()];
        for (int index = 0; index < points.size(); index++) {
            x[index] = index;
            y[index] = points.get(index).getValue();
        }

        double[] regression = olsRegression(x, y);
        double slope = regression[0];
        double intercept = regression[1];

        Duration step = points.size() >= 2
            ? Duration.between(points.get(points.size() - 2).getTimestamp(), points.get(points.size() - 1).getTimestamp())
            : Duration.ofMinutes(1);
        if (step.isZero() || step.isNegative()) {
            step = Duration.ofMinutes(1);
        }

        Instant lastTimestamp = points.get(points.size() - 1).getTimestamp();
        List<AnalyticsEngine.ForecastPoint> forecast = new ArrayList<>();
        for (int index = 1; index <= horizon; index++) {
            double predictedValue = intercept + slope * (points.size() - 1 + index);
            forecast.add(new AnalyticsEngine.ForecastPoint(lastTimestamp.plus(step.multipliedBy(index)), predictedValue));
        }
        return forecast;
    }

    public int detectSeasonality(AnalyticsEngine.TimeSeriesData series) {
        if (series == null || series.getPoints().size() < 6) {
            return -1;
        }

        List<AnalyticsEngine.DataPoint> points = series.getPoints();
        int bestPeriod = -1;
        double bestCorrelation = 0.0;
        for (int lag = 2; lag <= points.size() / 2; lag++) {
            double correlation = lagCorrelation(points, lag);
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation;
                bestPeriod = lag;
            }
        }
        return bestCorrelation >= 0.7 ? bestPeriod : -1;
    }

    public static double[] olsRegression(double[] x, double[] y) {
        if (x.length != y.length || x.length == 0) {
            return new double[]{0.0, 0.0};
        }

        double meanX = 0.0;
        double meanY = 0.0;
        for (int index = 0; index < x.length; index++) {
            meanX += x[index];
            meanY += y[index];
        }
        meanX /= x.length;
        meanY /= y.length;

        double numerator = 0.0;
        double denominator = 0.0;
        for (int index = 0; index < x.length; index++) {
            numerator += (x[index] - meanX) * (y[index] - meanY);
            denominator += (x[index] - meanX) * (x[index] - meanX);
        }

        double slope = denominator == 0.0 ? 0.0 : numerator / denominator;
        double intercept = meanY - slope * meanX;
        return new double[]{slope, intercept};
    }

    private static double lagCorrelation(List<AnalyticsEngine.DataPoint> points, int lag) {
        int count = points.size() - lag;
        if (count < 2) {
            return 0.0;
        }

        double meanA = 0.0;
        double meanB = 0.0;
        for (int index = 0; index < count; index++) {
            meanA += points.get(index).getValue();
            meanB += points.get(index + lag).getValue();
        }
        meanA /= count;
        meanB /= count;

        double numerator = 0.0;
        double denominatorA = 0.0;
        double denominatorB = 0.0;
        for (int index = 0; index < count; index++) {
            double a = points.get(index).getValue() - meanA;
            double b = points.get(index + lag).getValue() - meanB;
            numerator += a * b;
            denominatorA += a * a;
            denominatorB += b * b;
        }

        if (denominatorA == 0.0 || denominatorB == 0.0) {
            return 0.0;
        }
        return numerator / Math.sqrt(denominatorA * denominatorB);
    }
}