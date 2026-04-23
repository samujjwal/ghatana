/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * {@link StatisticalForecastingEngine}, {@link OnlineRegressionForecastingEngine},
 * and {@link AdaptiveForecastingEngine}.
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

        private final NaiveForecastingEngine engine = new NaiveForecastingEngine(); // GH-90000

        @Test
        @DisplayName("returns empty predictions for empty input")
        void emptyInputReturnsEmpty() { // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("counter", List.of()); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).isEmpty(); // GH-90000
            assertThat(result.metric()).isEqualTo("counter");
        }

        @Test
        @DisplayName("generates 5 predictions from a single data point")
        void singlePointGeneratesFivePredictions() { // GH-90000
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 100.0); // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("r", List.of(p)); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).hasSize(5); // GH-90000
        }

        @Test
        @DisplayName("each step grows by 1% over last value")
        void growthRateIsCorrect() { // GH-90000
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 200.0); // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p)); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions().get(0).value()).isEqualTo(200.0 * 1.01); // GH-90000
            assertThat(result.predictions().get(1).value()).isEqualTo(200.0 * 1.02); // GH-90000
        }

        @Test
        @DisplayName("predictions are spaced stepSeconds apart (default 3600s)")
        void predictionsAreSpacedCorrectly() { // GH-90000
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 50.0); // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p)); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions().get(0).timestamp()).isEqualTo(T0.plusSeconds(3600)); // GH-90000
            assertThat(result.predictions().get(1).timestamp()).isEqualTo(T0.plusSeconds(7200)); // GH-90000
        }

        @Test
        @DisplayName("confidence is 0.75")
        void confidenceIsFixed() { // GH-90000
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 10.0); // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p)); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.confidence()).isEqualTo(0.75); // GH-90000
        }

        @Test
        @DisplayName("custom horizon and step are respected")
        void customHorizonAndStep() { // GH-90000
            NaiveForecastingEngine custom = new NaiveForecastingEngine(3, 60L); // GH-90000
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 10.0); // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p)); // GH-90000
            AepEngine.Forecast result = runPromise(() -> custom.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).hasSize(3); // GH-90000
            assertThat(result.predictions().get(0).timestamp()).isEqualTo(T0.plusSeconds(60)); // GH-90000
        }

        @Test
        @DisplayName("algorithm name is 'naive'")
        void algorithmName() { // GH-90000
            assertThat(engine.algorithmName()).isEqualTo("naive");
        }

        @Test
        @DisplayName("invalid horizon throws IllegalArgumentException")
        void invalidHorizonThrows() { // GH-90000
            assertThatThrownBy(() -> new NaiveForecastingEngine(0, 60L)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("LinearTrendForecastingEngine")
    class LinearTrendTests {

        private final LinearTrendForecastingEngine engine = new LinearTrendForecastingEngine(); // GH-90000

        @Test
        @DisplayName("returns empty predictions for empty input")
        void emptyInputReturnsEmpty() { // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of()); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("single-point series falls back to naive")
        void singlePointFallsBackToNaive() { // GH-90000
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 100.0); // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p)); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).hasSize(5); // GH-90000
            assertThat(result.predictions().get(0).value()).isEqualTo(100.0 * 1.01); // GH-90000
        }

        @Test
        @DisplayName("perfectly linear series predicts next value correctly")
        void perfectLinearSeries() { // GH-90000
            // Points: t0=0, y=10; t1=3600, y=20; t2=7200, y=30  -> slope = 10/3600 per sec
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                 10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 20.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 30.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            // Next step after T0+7200 = T0+10800 should predict y=40
            assertThat(result.predictions().get(0).value()) // GH-90000
                .isCloseTo(40.0, org.assertj.core.data.Offset.offset(0.01)); // GH-90000
        }

        @Test
        @DisplayName("confidence is in [0.5, 0.99] for non-trivial series")
        void confidenceInRange() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                   10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 12.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 11.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.confidence()).isBetween(0.5, 0.99); // GH-90000
        }

        @Test
        @DisplayName("metadata includes algorithm and slope")
        void metadataContainsAlgorithm() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                   5.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 10.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.metadata()).containsKey("algorithm");
            assertThat(result.metadata()).containsKey("slope");
        }

        @Test
        @DisplayName("algorithm name is 'linear-trend'")
        void algorithmName() { // GH-90000
            assertThat(engine.algorithmName()).isEqualTo("linear-trend");
        }

        @Test
        @DisplayName("generates correct number of predictions")
        void generatesCorrectHorizon() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                   10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 15.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).hasSize(5); // GH-90000
        }
    }

    @Nested
    @DisplayName("StatisticalForecastingEngine")
    class StatisticalTests {

        // Use a same-thread executor so runPromise() can resolve ofBlocking() synchronously // GH-90000
        private final StatisticalForecastingEngine engine = new StatisticalForecastingEngine( // GH-90000
            0.3, 0.1, 5, 3600L, Executors.newSingleThreadExecutor()); // GH-90000

        @Test
        @DisplayName("algorithm name is 'exponential-smoothing'")
        void algorithmName() { // GH-90000
            assertThat(engine.algorithmName()).isEqualTo("exponential-smoothing");
        }

        @Test
        @DisplayName("falls back to naive for single-point series")
        void singlePoint_fallsBackToNaive() { // GH-90000
            AepEngine.DataPoint p = new AepEngine.DataPoint(T0, 100.0); // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of(p)); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).hasSize(5); // GH-90000
            // Naive: last_value * (1 + 0.01 * i) // GH-90000
            assertThat(result.predictions().get(0).value()).isEqualTo(101.0); // GH-90000
        }

        @Test
        @DisplayName("falls back to naive for empty series")
        void emptyInput_fallsBackToNaive() { // GH-90000
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", List.of()); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("generates 5 predictions for a valid series")
        void validSeries_generatesHorizonPredictions() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                   10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 12.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 14.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions()).hasSize(5); // GH-90000
        }

        @Test
        @DisplayName("predictions extend from the last timestamp")
        void predictionsAnchoredAtLastTimestamp() { // GH-90000
            Instant last = T0.plusSeconds(7200); // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                   10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 12.0), // GH-90000
                new AepEngine.DataPoint(last,                  14.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.predictions().get(0).timestamp()) // GH-90000
                .isEqualTo(last.plusSeconds(3600)); // GH-90000
        }

        @Test
        @DisplayName("confidence is in [0.5, 0.99] for a smooth trend")
        void confidence_inExpectedRange() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                   100.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 105.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 110.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(10800), 115.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("cpu_usage", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.confidence()) // GH-90000
                .as("confidence should be in [0.5, 0.99]")
                .isBetween(0.5, 0.99); // GH-90000
        }

        @Test
        @DisplayName("upward trend in data produces predictions above last observed value")
        void upwardTrend_predictionsAboveLastValue() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                    10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600),  20.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200),  30.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(10800), 40.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            double lastValue = 40.0;
            assertThat(result.predictions().get(0).value()) // GH-90000
                .as("first prediction should be above last observed for upward trend")
                .isGreaterThan(lastValue); // GH-90000
        }

        @Test
        @DisplayName("metadata contains algorithm, alpha, beta, and finalLevel")
        void metadata_containsExpectedKeys() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0,                   5.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 10.0) // GH-90000
            );
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData("m", pts); // GH-90000
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", data)); // GH-90000
            assertThat(result.metadata()) // GH-90000
                .containsKey("algorithm")
                .containsKey("alpha")
                .containsKey("beta")
                .containsKey("finalLevel")
                .containsKey("finalTrend");
        }

        @Test
        @DisplayName("invalid alpha (0.0) throws IllegalArgumentException")
        void invalidAlpha_throws() { // GH-90000
            assertThatThrownBy(() -> new StatisticalForecastingEngine(0.0, 0.1, 5, 3600L)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("alpha");
        }

        @Test
        @DisplayName("invalid beta (1.0) throws IllegalArgumentException")
        void invalidBeta_throws() { // GH-90000
            assertThatThrownBy(() -> new StatisticalForecastingEngine(0.3, 1.0, 5, 3600L)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("beta");
        }
    }

    @Nested
    @DisplayName("AdaptiveForecastingEngine")
    class AdaptiveTests {

        private final AdaptiveForecastingEngine engine = new AdaptiveForecastingEngine( // GH-90000
            new NaiveForecastingEngine(3, 3600L), // GH-90000
            new LinearTrendForecastingEngine(3, 3600L), // GH-90000
            new StatisticalForecastingEngine(0.3, 0.1, 3, 3600L, Executors.newSingleThreadExecutor()), // GH-90000
            new OnlineRegressionForecastingEngine(3, 3600L, 200, 0.05, Runnable::run), // GH-90000
            2
        );

        @Test
        @DisplayName("selects a non-naive model for a stable linear trend")
        void selectsBestModelForTrend() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0, 10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 20.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 30.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(10800), 40.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(14400), 50.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(18000), 60.0) // GH-90000
            );
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", new AepEngine.TimeSeriesData("m", pts))); // GH-90000

            assertThat(result.metadata()).containsEntry("algorithm", "adaptive"); // GH-90000
            assertThat(result.metadata()).containsKey("selectedAlgorithm");
            assertThat(result.metadata()).containsKey("candidateRmse");
            assertThat(result.metadata().get("selectedAlgorithm")).isNotEqualTo("naive");
        }

        @Test
        @DisplayName("falls back to linear selection when history is too short")
        void shortHistoryFallsBack() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0, 10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 12.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 14.0) // GH-90000
            );
            AepEngine.Forecast result = runPromise(() -> engine.forecast("t1", new AepEngine.TimeSeriesData("m", pts))); // GH-90000

            assertThat(result.metadata()).containsEntry("algorithm", "adaptive"); // GH-90000
            assertThat(result.metadata()).containsEntry("selectedAlgorithm", "insufficient-history"); // GH-90000
        }
    }

    @Nested
    @DisplayName("OnlineRegressionForecastingEngine")
    class OnlineRegressionTests {

        private final OnlineRegressionForecastingEngine engine = new OnlineRegressionForecastingEngine( // GH-90000
            3,
            3600L,
            200,
            0.05,
            Runnable::run
        );

        @Test
        @DisplayName("learns an upward trend from historical series")
        void learnsUpwardTrend() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0, 15.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 25.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 35.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(10800), 45.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(14400), 55.0) // GH-90000
            );

            AepEngine.Forecast result = runPromise(() -> engine.forecast("tenant-a", new AepEngine.TimeSeriesData("m", pts))); // GH-90000

            assertThat(result.metadata()).containsEntry("algorithm", "online-regression"); // GH-90000
            assertThat(result.metadata()).containsEntry("warmStarted", false); // GH-90000
            assertThat((Double) result.metadata().get("slope")).isPositive();
            assertThat(result.predictions()).hasSize(3); // GH-90000
            assertThat(result.predictions().get(2).value()).isGreaterThan(result.predictions().get(0).value()); // GH-90000
        }

        @Test
        @DisplayName("reuses learned state for the same tenant and metric")
        void reusesWarmState() { // GH-90000
            List<AepEngine.DataPoint> pts = List.of( // GH-90000
                new AepEngine.DataPoint(T0, 10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 20.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 30.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(10800), 40.0) // GH-90000
            );

            runPromise(() -> engine.forecast("tenant-a", new AepEngine.TimeSeriesData("throughput", pts))); // GH-90000
            AepEngine.Forecast second = runPromise(() -> engine.forecast("tenant-a", new AepEngine.TimeSeriesData("throughput", pts))); // GH-90000

            assertThat(second.metadata()).containsEntry("warmStarted", true); // GH-90000
        }
    }
}
