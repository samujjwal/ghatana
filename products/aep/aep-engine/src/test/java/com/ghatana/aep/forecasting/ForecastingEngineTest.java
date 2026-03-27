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
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link NaiveForecastingEngine}, {@link LinearTrendForecastingEngine},
 * and {@link StatisticalForecastingEngine}.
 *
 * @doc.type test
 * @doc.purpose Verify forecasting strategy implementations
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("ForecastingEngine")
class ForecastingEngineTest extends EventloopTestBase {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Nested
    @DisplayName("NaiveForecastingEngine")
    class NaiveTests {

        private final NaiveForecastingEngine engine = new NaiveForecastingEngine();

        @Test
        @DisplayName("returns empty predictions for empty input")
        void emptyInputReturnsEmpty() {
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("counter", List.of());
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions()).isEmpty();
            assertThat(result.metric()).isEqualTo("counter");
        }

        @Test
        @DisplayName("generates 5 predictions from a single data point")
        void singlePointGeneratesFivePredictions() {
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 100.0);
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("r", List.of(p));
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions()).hasSize(5);
        }

        @Test
        @DisplayName("each step grows by 1% over last value")
        void growthRateIsCorrect() {
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 200.0);
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p));
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions().get(0).value()).isEqualTo(200.0 * 1.01);
            assertThat(result.predictions().get(1).value()).isEqualTo(200.0 * 1.02);
        }

        @Test
        @DisplayName("predictions are spaced stepSeconds apart (default 3600s)")
        void predictionsAreSpacedCorrectly() {
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 50.0);
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p));
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions().get(0).timestamp()).isEqualTo(T0.plusSeconds(3600));
            assertThat(result.predictions().get(1).timestamp()).isEqualTo(T0.plusSeconds(7200));
        }

        @Test
        @DisplayName("confidence is 0.75")
        void confidenceIsFixed() {
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 10.0);
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p));
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.confidence()).isEqualTo(0.75);
        }

        @Test
        @DisplayName("custom horizon and step are respected")
        void customHorizonAndStep() {
            NaiveForecastingEngine custom = new NaiveForecastingEngine(3, 60L);
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 10.0);
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p));
            AepEngine.Forecast result = runPromise(() -> custom.forecast("t1", data));
            assertThat(result.predictions()).hasSize(3);
            assertThat(result.predictions().get(0).timestamp()).isEqualTo(T0.plusSeconds(60));
        }

        @Test
        @DisplayName("algorithm name is 'naive'")
        void algorithmName() {
            assertThat(engine.algorithmName()).isEqualTo("naive");
        }

        @Test
        @DisplayName("invalid horizon throws IllegalArgumentException")
        void invalidHorizonThrows() {
            assertThatThrownBy(() -> new NaiveForecastingEngine(0, 60L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("LinearTrendForecastingEngine")
    class LinearTrendTests {

        private final LinearTrendForecastingEngine engine = new LinearTrendForecastingEngine();

        @Test
        @DisplayName("returns empty predictions for empty input")
        void emptyInputReturnsEmpty() {
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of());
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions()).isEmpty();
        }

        @Test
        @DisplayName("single-point series falls back to naive")
        void singlePointFallsBackToNaive() {
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 100.0);
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p));
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions()).hasSize(5);
            assertThat(result.predictions().get(0).value()).isEqualTo(100.0 * 1.01);
        }

        @Test
        @DisplayName("perfectly linear series predicts next value correctly")
        void perfectLinearSeries() {
            // Points: t0=0, y=10; t1=3600, y=20; t2=7200, y=30  -> slope = 10/3600 per sec
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                 10.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 20.0),
                new AepEngine.DataPoint(T0.plusSeconds(7200), 30.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            // Next step after T0+7200 = T0+10800 should predict y=40
            assertThat(result.predictions().get(0).value())
                .isCloseTo(40.0, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("confidence is in [0.5, 0.99] for non-trivial series")
        void confidenceInRange() {
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                   10.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 12.0),
                new AepEngine.DataPoint(T0.plusSeconds(7200), 11.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.confidence()).isBetween(0.5, 0.99);
        }

        @Test
        @DisplayName("metadata includes algorithm and slope")
        void metadataContainsAlgorithm() {
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                   5.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 10.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.metadata()).containsKey("algorithm");
            assertThat(result.metadata()).containsKey("slope");
        }

        @Test
        @DisplayName("algorithm name is 'linear-trend'")
        void algorithmName() {
            assertThat(engine.algorithmName()).isEqualTo("linear-trend");
        }

        @Test
        @DisplayName("generates correct number of predictions")
        void generatesCorrectHorizon() {
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                   10.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 15.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("StatisticalForecastingEngine")
    class StatisticalTests {

        // Use a same-thread executor so runPromise() can resolve ofBlocking() synchronously
        private final StatisticalForecastingEngine engine = new StatisticalForecastingEngine(
            0.3, 0.1, 5, 3600L, Executors.newSingleThreadExecutor());

        @Test
        @DisplayName("algorithm name is 'exponential-smoothing'")
        void algorithmName() {
            assertThat(engine.algorithmName()).isEqualTo("exponential-smoothing");
        }

        @Test
        @DisplayName("falls back to naive for single-point series")
        void singlePoint_fallsBackToNaive() {
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 100.0);
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p));
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions()).hasSize(5);
            // Naive: last_value * (1 + 0.01 * i)
            assertThat(result.predictions().get(0).value()).isEqualTo(101.0);
        }

        @Test
        @DisplayName("falls back to naive for empty series")
        void emptyInput_fallsBackToNaive() {
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of());
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions()).isEmpty();
        }

        @Test
        @DisplayName("generates 5 predictions for a valid series")
        void validSeries_generatesHorizonPredictions() {
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                   10.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 12.0),
                new AepEngine.DataPoint(T0.plusSeconds(7200), 14.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions()).hasSize(5);
        }

        @Test
        @DisplayName("predictions extend from the last timestamp")
        void predictionsAnchoredAtLastTimestamp() {
            Instant last = T0.plusSeconds(7200);
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                   10.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 12.0),
                new AepEngine.DataPoint(last,                  14.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.predictions().get(0).timestamp())
                .isEqualTo(last.plusSeconds(3600));
        }

        @Test
        @DisplayName("confidence is in [0.5, 0.99] for a smooth trend")
        void confidence_inExpectedRange() {
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                   100.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 105.0),
                new AepEngine.DataPoint(T0.plusSeconds(7200), 110.0),
                new AepEngine.DataPoint(T0.plusSeconds(10800), 115.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("cpu_usage", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.confidence())
                .as("confidence should be in [0.5, 0.99]")
                .isBetween(0.5, 0.99);
        }

        @Test
        @DisplayName("upward trend in data produces predictions above last observed value")
        void upwardTrend_predictionsAboveLastValue() {
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                    10.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600),  20.0),
                new AepEngine.DataPoint(T0.plusSeconds(7200),  30.0),
                new AepEngine.DataPoint(T0.plusSeconds(10800), 40.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            double lastValue = 40.0;
            assertThat(result.predictions().get(0).value())
                .as("first prediction should be above last observed for upward trend")
                .isGreaterThan(lastValue);
        }

        @Test
        @DisplayName("metadata contains algorithm, alpha, beta, and finalLevel")
        void metadata_containsExpectedKeys() {
            List<AepEngine.DataPoint> pts = List.of(
                new AepEngine.DataPoint(T0,                   5.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 10.0)
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts);
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data));
            assertThat(result.metadata())
                .containsKey("algorithm")
                .containsKey("alpha")
                .containsKey("beta")
                .containsKey("finalLevel")
                .containsKey("finalTrend");
        }

        @Test
        @DisplayName("invalid alpha (0.0) throws IllegalArgumentException")
        void invalidAlpha_throws() {
            assertThatThrownBy(() -> new StatisticalForecastingEngine(0.0, 0.1, 5, 3600L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alpha");
        }

        @Test
        @DisplayName("invalid beta (1.0) throws IllegalArgumentException")
        void invalidBeta_throws() {
            assertThatThrownBy(() -> new StatisticalForecastingEngine(0.3, 1.0, 5, 3600L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("beta");
        }
    }
}
