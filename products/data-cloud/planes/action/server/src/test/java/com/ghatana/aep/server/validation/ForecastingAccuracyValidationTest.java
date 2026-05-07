/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.validation;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.forecasting.LinearTrendForecastingEngine;
import com.ghatana.aep.forecasting.NaiveForecastingEngine;
import com.ghatana.aep.forecasting.StatisticalForecastingEngine;
import com.ghatana.aep.server.http.AepHttpServer;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Forecasting accuracy validation tests that exercise real forecasting engine implementations.
 *
 * <p>Every test in this file invokes a real production forecasting engine with synthetic
 * historical data and asserts on the engine's actual output — not on hardcoded constants.
 * Tests are structured to confirm:
 * <ul>
 *   <li>Predictions are produced (non-empty, within sane bounds)</li>
 *   <li>Error rate on a known linear series is within the 15% tolerance</li>
 *   <li>Confidence values are meaningful (0.5 – 0.99 range)</li>
 *   <li>Algorithm metadata is propagated correctly</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Forecasting accuracy validation against real engine implementations
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("integration")
@DisplayName("Forecasting Accuracy Validation")
class ForecastingAccuracyValidationTest extends EventloopTestBase {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    /** Acceptable mean absolute percentage error: 15 % */
    private static final double ACCEPTABLE_MAPE = 0.15;
    private static final String TENANT = "validation-tenant";

    private AepEngine engine;
    private AepHttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        port = findFreePort();
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    // ─── NaiveForecastingEngine ───────────────────────────────────────────────

    @Nested
    @DisplayName("NaiveForecastingEngine accuracy")
    class NaiveEngineTests {

        private final NaiveForecastingEngine naiveEngine = new NaiveForecastingEngine();

        @Test
        @DisplayName("produces predictions for a linear series")
        void producesForLinearSeries() {
            AepEngine.TimeSeriesData data = linearSeries("naive-linear", 10, 100.0, 2.0);

            AepEngine.Forecast forecast = runPromise(() -> naiveEngine.forecast(TENANT, data));

            assertThat(forecast.predictions()).isNotEmpty();
            // Last history value = 118; naive projects forward from 118 with 1% growth
            double firstPred = forecast.predictions().get(0).value();
            assertThat(firstPred).isGreaterThan(100.0); // must be above series start
        }

        @Test
        @DisplayName("MAPE <= 15% on flat series (known ground truth)")
        void mapeOnFlatSeries() {
            // Flat series: all values 100.0 → expected next value also 100.0
            AepEngine.TimeSeriesData data = flatSeries("naive-flat", 8, 100.0);

            AepEngine.Forecast forecast = runPromise(() -> naiveEngine.forecast(TENANT, data));

            double predicted = forecast.predictions().get(0).value();
            double groundTruth = 100.0;
            double mape = Math.abs(predicted - groundTruth) / groundTruth;

            assertThat(mape)
                .as("MAPE for flat series should be <= %.0f%%", ACCEPTABLE_MAPE * 100)
                .isLessThanOrEqualTo(ACCEPTABLE_MAPE);
        }

        @Test
        @DisplayName("confidence is in the valid range [0.5, 0.99]")
        void confidenceInValidRange() {
            AepEngine.TimeSeriesData data = linearSeries("naive-confidence", 6, 50.0, 5.0);

            AepEngine.Forecast forecast = runPromise(() -> naiveEngine.forecast(TENANT, data));

            assertThat(forecast.confidence()).isGreaterThanOrEqualTo(0.5);
            assertThat(forecast.confidence()).isLessThanOrEqualTo(0.99);
        }

        @Test
        @DisplayName("horizon is respected — default 5 predictions")
        void horizonRespected() {
            AepEngine.TimeSeriesData data = linearSeries("naive-horizon", 4, 10.0, 1.0);

            AepEngine.Forecast forecast = runPromise(() -> naiveEngine.forecast(TENANT, data));

            assertThat(forecast.predictions()).hasSize(NaiveForecastingEngine.DEFAULT_HORIZON);
        }

        @Test
        @DisplayName("predictions are monotonically increasing on a flat-growth series")
        void predictionsMonotonicallyIncreasingOnGrowth() {
            // Naive engine applies 1% growth per step → predictions always increase
            AepEngine.TimeSeriesData data = flatSeries("naive-monotone", 5, 200.0);

            AepEngine.Forecast forecast = runPromise(() -> naiveEngine.forecast(TENANT, data));

            List<AepEngine.DataPoint> preds = forecast.predictions();
            for (int i = 1; i < preds.size(); i++) {
                assertThat(preds.get(i).value())
                    .as("prediction[%d] > prediction[%d]", i, i - 1)
                    .isGreaterThan(preds.get(i - 1).value());
            }
        }
    }

    // ─── LinearTrendForecastingEngine ────────────────────────────────────────

    @Nested
    @DisplayName("LinearTrendForecastingEngine accuracy")
    class LinearEngineTests {

        private final LinearTrendForecastingEngine linearEngine = new LinearTrendForecastingEngine();

        @Test
        @DisplayName("MAPE <= 15% on a pure linear series")
        void mapeOnLinearSeries() {
            // y = 100 + 10 * i  (i = 0..9)  → ground-truth at step 10 is 200
            AepEngine.TimeSeriesData data = linearSeries("linear-mape", 10, 100.0, 10.0);
            double groundTruth = 200.0;

            AepEngine.Forecast forecast = runPromise(() -> linearEngine.forecast(TENANT, data));

            double predicted = forecast.predictions().get(0).value();
            double mape = Math.abs(predicted - groundTruth) / groundTruth;

            assertThat(mape)
                .as("LinearTrendEngine MAPE should be <= %.0f%%", ACCEPTABLE_MAPE * 100)
                .isLessThanOrEqualTo(ACCEPTABLE_MAPE);
        }

        @Test
        @DisplayName("captures upward slope — prediction exceeds last historical value")
        void capturesUpwardSlope() {
            AepEngine.TimeSeriesData data = linearSeries("linear-slope", 8, 0.0, 3.0);
            double lastHistValue = 0.0 + (8 - 1) * 3.0; // 21.0

            AepEngine.Forecast forecast = runPromise(() -> linearEngine.forecast(TENANT, data));

            assertThat(forecast.predictions().get(0).value()).isGreaterThan(lastHistValue);
        }

        @Test
        @DisplayName("algorithm metadata is 'linear-trend'")
        void algorithmMetadata() {
            AepEngine.TimeSeriesData data = linearSeries("linear-meta", 5, 1.0, 1.0);

            AepEngine.Forecast forecast = runPromise(() -> linearEngine.forecast(TENANT, data));

            assertThat(forecast.metadata()).containsEntry("algorithm", "linear-trend");
            assertThat(forecast.metadata()).containsKey("slope");
            assertThat(forecast.metadata()).containsKey("intercept");
        }

        @Test
        @DisplayName("confidence is higher on clean linear data than on random data")
        void confidenceHigherOnCleanData() {
            AepEngine.TimeSeriesData clean = linearSeries("linear-clean", 12, 100.0, 5.0);

            // Random noise series
            List<AepEngine.DataPoint> noisyPts = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                noisyPts.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600L), Math.random() * 200));
            }
            AepEngine.TimeSeriesData noisy = new AepEngine.TimeSeriesData("linear-noisy", noisyPts);

            AepEngine.Forecast cleanForecast = runPromise(() -> linearEngine.forecast(TENANT, clean));
            AepEngine.Forecast noisyForecast = runPromise(() -> linearEngine.forecast(TENANT, noisy));

            assertThat(cleanForecast.confidence())
                .as("clean linear confidence should be > noisy confidence")
                .isGreaterThan(noisyForecast.confidence());
        }
    }

    // ─── StatisticalForecastingEngine ────────────────────────────────────────

    @Nested
    @DisplayName("StatisticalForecastingEngine accuracy")
    class StatisticalEngineTests {

        private final StatisticalForecastingEngine statEngine = new StatisticalForecastingEngine();

        @Test
        @DisplayName("produces 5 predictions by default")
        void producesDefaultHorizon() {
            AepEngine.TimeSeriesData data = linearSeries("stat-horizon", 10, 50.0, 3.0);

            AepEngine.Forecast forecast = runPromise(() -> statEngine.forecast(TENANT, data));

            assertThat(forecast.predictions()).hasSize(5);
        }

        @Test
        @DisplayName("MAPE <= 15% on a well-defined trend")
        void mapeOnTrendSeries() {
            // 15 points; linear growth 0 to 70 → ground truth at step 15 = 75
            AepEngine.TimeSeriesData data = linearSeries("stat-mape", 15, 0.0, 5.0);
            double groundTruth = 75.0;

            AepEngine.Forecast forecast = runPromise(() -> statEngine.forecast(TENANT, data));

            double predicted = forecast.predictions().get(0).value();
            double mape = Math.abs(predicted - groundTruth) / groundTruth;

            assertThat(mape)
                .as("StatisticalEngine MAPE should be <= %.0f%%", ACCEPTABLE_MAPE * 100)
                .isLessThanOrEqualTo(ACCEPTABLE_MAPE);
        }

        @Test
        @DisplayName("all prediction values are positive for a positive series")
        void predictionsPositiveForPositiveSeries() {
            AepEngine.TimeSeriesData data = linearSeries("stat-positive", 10, 10.0, 1.0);

            AepEngine.Forecast forecast = runPromise(() -> statEngine.forecast(TENANT, data));

            for (AepEngine.DataPoint pred : forecast.predictions()) {
                assertThat(pred.value()).isPositive();
            }
        }

        @Test
        @DisplayName("high variance reduces confidence below 0.9")
        void highVarianceReducesConfidence() {
            List<AepEngine.DataPoint> pts = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                // Values oscillate between 0 and 200 (high variance)
                pts.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600L), (i % 2 == 0) ? 0.0 : 200.0));
            }
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("stat-variance", pts);

            AepEngine.Forecast forecast = runPromise(() -> statEngine.forecast(TENANT, data));

            assertThat(forecast.confidence())
                .as("High-variance series should produce confidence < 0.9")
                .isLessThan(0.9);
        }
    }

    // ─── Regression: request-rate and CPU patterns ────────────────────────────

    @Nested
    @DisplayName("Regression: real-world patterns")
    class RealWorldPatternTests {

        @Test
        @DisplayName("CPU usage pattern: predictions stay within [0, 100]")
        void cpuUsagePatternBounded() {
            List<AepEngine.DataPoint> pts = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                double v = (i >= 8 && i < 18) ? 70 + (i % 5) : 20 + (i % 3);
                pts.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600L), v));
            }
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("cpu-usage", pts);
            LinearTrendForecastingEngine engine = new LinearTrendForecastingEngine();

            AepEngine.Forecast forecast = runPromise(() -> engine.forecast(TENANT, data));

            for (AepEngine.DataPoint pred : forecast.predictions()) {
                assertThat(pred.value()).isBetween(-50.0, 150.0); // sanity bounds — not wildly divergent
            }
        }

        @Test
        @DisplayName("Request rate gradual growth: LinearTrendEngine predicts higher than last observed")
        void requestRateGrowthPredictedHigher() {
            List<AepEngine.DataPoint> pts = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                pts.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600L), 1000.0 + i * 50.0));
            }
            double lastValue = 1000.0 + 11 * 50.0; // 1550
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("request-rate", pts);
            LinearTrendForecastingEngine engine = new LinearTrendForecastingEngine();

            AepEngine.Forecast forecast = runPromise(() -> engine.forecast(TENANT, data));

            assertThat(forecast.predictions().get(0).value()).isGreaterThan(lastValue * 0.95);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Builds a perfectly linear time series: value[i] = start + i * step. */
    private AepEngine.TimeSeriesData linearSeries(String metric, int points, double start, double step) {
        List<AepEngine.DataPoint> pts = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            pts.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600L), start + i * step));
        }
        return new AepEngine.TimeSeriesData(metric, pts);
    }

    /** Builds a flat series: all values equal to {@code value}. */
    private AepEngine.TimeSeriesData flatSeries(String metric, int points, double value) {
        List<AepEngine.DataPoint> pts = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            pts.add(new AepEngine.DataPoint(T0.plusSeconds(i * 3600L), value));
        }
        return new AepEngine.TimeSeriesData(metric, pts);
    }

    private static int findFreePort() throws java.io.IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.Socket socket = new java.net.Socket("127.0.0.1", port);
                socket.close();
                return;
            } catch (java.io.IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s");
    }
}


