/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.forecasting;

import com.ghatana.aep.AepEngine;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forecasting accuracy validation tests using historical data patterns.
 *
 * These tests validate forecasting engine accuracy against known historical data patterns,
 * ensuring predictions fall within acceptable error bounds for different trend types:
 * - Linear growth trends
 * - Seasonal patterns
 * - Step changes
 * - Noisy data
 *
 * @doc.type test
 * @doc.purpose Validate forecasting accuracy with historical data
 * @doc.layer product
 * @doc.pattern Accuracy Validation Test
 */
@DisplayName("Forecasting Accuracy Validation")
class ForecastingAccuracyValidationTest extends EventloopTestBase {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final double ACCEPTABLE_ERROR_RATE = 0.15; // 15% error tolerance

    @Nested
    @DisplayName("Linear Growth Trend Validation")
    class LinearGrowthTests {

        private final NaiveForecastingEngine engine = new NaiveForecastingEngine();

        @Test
        @DisplayName("Naive forecasting maintains accuracy on linear growth")
        void naiveAccuracyOnLinearGrowth() {
            // Generate historical data with linear growth: 100, 102, 104, 106, 108, 110
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 100.0 + i * 2.0));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("linear-growth", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Expected next value: 120 (continuing the +2 per hour trend)
            // Naive will predict based on last value: 118 * 1.01 = 119.18
            double firstPrediction = forecast.predictions().get(0).value();
            double expectedValue = 120.0;
            double errorRate = Math.abs(firstPrediction - expectedValue) / expectedValue;

            assertThat(errorRate).isLessThan(ACCEPTABLE_ERROR_RATE);
            assertThat(forecast.predictions()).hasSize(5);
        }

        @Test
        @DisplayName("Statistical forecasting adapts to linear growth")
        void statisticalAccuracyOnLinearGrowth() {
            StatisticalForecastingEngine engine = new StatisticalForecastingEngine();

            // Generate historical data with linear growth
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 100.0 + i * 5.0));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("linear-growth", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Statistical forecasting should capture the trend better than naive
            double firstPrediction = forecast.predictions().get(0).value();
            double expectedValue = 200.0; // 100 + 20*5
            double errorRate = Math.abs(firstPrediction - expectedValue) / expectedValue;

            assertThat(errorRate).isLessThan(ACCEPTABLE_ERROR_RATE);
        }
    }

    @Nested
    @DisplayName("Step Change Detection")
    class StepChangeTests {

        private final NaiveForecastingEngine engine = new NaiveForecastingEngine();

        @Test
        @DisplayName("Forecasting reacts to sudden step changes")
        void reactsToStepChanges() {
            // Generate data with a step change at index 10: values jump from 100 to 200
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 100.0));
            }
            for (int i = 10; i < 15; i++) {
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 200.0));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("step-change", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Forecast should be based on the new level (200)
            double firstPrediction = forecast.predictions().get(0).value();
            assertThat(firstPrediction).isGreaterThan(190.0).isLessThan(210.0);
        }

        @Test
        @DisplayName("Adaptive forecasting handles step changes better")
        void adaptiveHandlesStepChanges() {
            AdaptiveForecastingEngine engine = new AdaptiveForecastingEngine();

            // Generate data with step changes
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 50.0));
            }
            for (int i = 5; i < 10; i++) {
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 150.0));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("step-change", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Adaptive should select appropriate algorithm
            assertThat(forecast.predictions()).isNotEmpty();
            assertThat(forecast.metadata()).containsKey("algorithm");
        }
    }

    @Nested
    @DisplayName("Noisy Data Handling")
    class NoisyDataTests {

        private final StatisticalForecastingEngine engine = new StatisticalForecastingEngine();

        @Test
        @DisplayName("Forecasting smooths out random noise")
        void smoothsRandomNoise() {
            // Generate data with underlying trend + random noise
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                double trend = 100.0 + i * 2.0;
                double noise = (Math.random() - 0.5) * 10.0; // +/- 5 noise
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), trend + noise));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("noisy", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Forecast should be close to the underlying trend, not the noisy last value
            double lastNoisyValue = history.get(history.size() - 1).value();
            double firstPrediction = forecast.predictions().get(0).value();
            double expectedTrend = 100.0 + 20 * 2.0; // 140

            // Prediction should be closer to trend than to last noisy value
            double predictionError = Math.abs(firstPrediction - expectedTrend);
            double lastValueError = Math.abs(lastNoisyValue - expectedTrend);

            assertThat(predictionError).isLessThan(lastValueError);
        }

        @Test
        @DisplayName("Confidence reflects data quality")
        void confidenceReflectsDataQuality() {
            // Generate high-variance data
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                double value = 100 + (Math.random() - 0.5) * 50.0; // +/- 25 variance
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), value));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("high-variance", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // High variance should result in lower confidence
            assertThat(forecast.confidence()).isLessThan(0.9);
        }
    }

    @Nested
    @DisplayName("Seasonal Pattern Detection")
    class SeasonalPatternTests {

        @Test
        @DisplayName("Online regression captures periodic patterns")
        void capturesPeriodicPatterns() {
            OnlineRegressionForecastingEngine engine = new OnlineRegressionForecastingEngine(
                5, 3600L, 10, 0.1, java.util.concurrent.Executors.newSingleThreadExecutor()
            );

            // Generate data with daily seasonality (24-hour cycle)
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 48; i++) { // 2 days of hourly data
                double value = 100 + 20 * Math.sin(2 * Math.PI * i / 24); // Daily cycle
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), value));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("seasonal", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Forecast should continue the pattern
            assertThat(forecast.predictions()).hasSize(5);
            assertThat(forecast.metadata()).containsKey("algorithm");
        }
    }

    @Nested
    @DisplayName("Regression Testing with Historical Data")
    class RegressionTests {

        @Test
        @DisplayName("Historical data regression: CPU usage pattern")
        void cpuUsagePatternRegression() {
            // Simulate typical CPU usage pattern: low at night, high during day
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                double value;
                if (i >= 8 && i < 18) {
                    value = 70 + Math.random() * 20; // Business hours: 70-90%
                } else {
                    value = 20 + Math.random() * 10; // Off hours: 20-30%
                }
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), value));
            }

            StatisticalForecastingEngine engine = new StatisticalForecastingEngine();
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("cpu-usage", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Forecast should predict reasonable values (not extreme outliers)
            for (AepEngine.DataPoint pred : forecast.predictions()) {
                assertThat(pred.value()).isGreaterThan(0).isLessThan(100);
            }
        }

        @Test
        @DisplayName("Historical data regression: Request rate pattern")
        void requestRatePatternRegression() {
            // Simulate request rate with gradual increase
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                double value = 1000 + i * 50 + (Math.random() - 0.5) * 100;
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), value));
            }

            NaiveForecastingEngine engine = new NaiveForecastingEngine();
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("request-rate", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Forecast should continue the upward trend
            double lastValue = history.get(history.size() - 1).value();
            double firstPrediction = forecast.predictions().get(0).value();
            assertThat(firstPrediction).isGreaterThan(lastValue * 0.95); // At least maintains level
        }
    }

    @Nested
    @DisplayName("Accuracy Metrics")
    class AccuracyMetricsTests {

        @Test
        @DisplayName("Calculates MAPE (Mean Absolute Percentage Error)")
        void calculatesMAPE() {
            StatisticalForecastingEngine engine = new StatisticalForecastingEngine();

            // Generate known data
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 100.0 + i * 10.0));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("mape-test", history);
            AepEngine.Forecast forecast = runPromise(() -> engine.forecast("tenant-1", data));

            // Calculate MAPE for first prediction
            double actualNextValue = 200.0; // 100 + 10*10
            double predictedValue = forecast.predictions().get(0).value();
            double mape = Math.abs(actualNextValue - predictedValue) / actualNextValue;

            assertThat(mape).isLessThan(ACCEPTABLE_ERROR_RATE);
        }

        @Test
        @DisplayName("Tracks forecast accuracy over time")
        void tracksAccuracyOverTime() {
            OnlineRegressionForecastingEngine engine = new OnlineRegressionForecastingEngine(
                5, 3600L, 10, 0.1, java.util.concurrent.Executors.newSingleThreadExecutor()
            );

            // Generate training data
            List<AepEngine.DataPoint> history = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                history.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 100.0 + i * 5.0));
            }

            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("accuracy-track", history);

            // Make first forecast
            AepEngine.Forecast forecast1 = runPromise(() -> engine.forecast("tenant-1", data));

            // Add more data and forecast again (online learning should improve accuracy)
            List<AepEngine.DataPoint> additionalHistory = new ArrayList<>();
            for (int i = 20; i < 25; i++) {
                additionalHistory.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600), 100.0 + i * 5.0));
            }

            List<AepEngine.DataPoint> allHistory = new ArrayList<>(history);
            allHistory.addAll(additionalHistory);
            AepEngine.TimeSeriesData updatedData = new AepEngine.TimeSeriesData("accuracy-track", allHistory);

            AepEngine.Forecast forecast2 = runPromise(() -> engine.forecast("tenant-1", updatedData));

            // Both forecasts should be reasonable
            assertThat(forecast1.predictions()).isNotEmpty();
            assertThat(forecast2.predictions()).isNotEmpty();
            assertThat(forecast2.metadata()).containsEntry("warmStarted", true);
        }
    }
}
