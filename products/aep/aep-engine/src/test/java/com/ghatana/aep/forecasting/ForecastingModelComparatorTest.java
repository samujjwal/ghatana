/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.forecasting;

import com.ghatana.aep.AepEngine;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ForecastingModelComparator")
class ForecastingModelComparatorTest extends EventloopTestBase {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("adaptive forecasting beats naive baseline on a stable linear trend")
    void adaptiveBeatsNaiveOnStableTrend() {
        ForecastingModelComparator comparator = new ForecastingModelComparator(
            new NaiveForecastingEngine(3, 3600L),
            new AdaptiveForecastingEngine(
                new NaiveForecastingEngine(3, 3600L),
                new LinearTrendForecastingEngine(3, 3600L),
                new StatisticalForecastingEngine(0.3, 0.1, 3, 3600L, Executors.newSingleThreadExecutor()),
                new OnlineRegressionForecastingEngine(3, 3600L, 200, 0.05, Runnable::run),
                2
            ),
            3
        );

        AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData(
            "throughput",
            List.of(
                new AepEngine.DataPoint(T0, 10.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 20.0),
                new AepEngine.DataPoint(T0.plusSeconds(7200), 30.0),
                new AepEngine.DataPoint(T0.plusSeconds(10800), 40.0),
                new AepEngine.DataPoint(T0.plusSeconds(14400), 50.0),
                new AepEngine.DataPoint(T0.plusSeconds(18000), 60.0),
                new AepEngine.DataPoint(T0.plusSeconds(21600), 70.0),
                new AepEngine.DataPoint(T0.plusSeconds(25200), 80.0)
            )
        );

        ForecastingModelComparator.ComparisonResult result = runPromise(() -> comparator.compare("tenant-a", data));

        assertThat(result.baselineAlgorithm()).isEqualTo("naive");
        assertThat(result.candidateAlgorithm()).isEqualTo("adaptive");
        assertThat(result.winner()).isEqualTo("adaptive");
        assertThat(result.candidateRmse()).isLessThan(result.baselineRmse());
        assertThat(result.asMetadata()).containsEntry("winner", "adaptive");
    }

    @Test
    @DisplayName("returns insufficient-history when holdout split cannot be formed")
    void returnsInsufficientHistoryForShortSeries() {
        ForecastingModelComparator comparator = new ForecastingModelComparator(
            new NaiveForecastingEngine(),
            new LinearTrendForecastingEngine(),
            3
        );

        AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData(
            "throughput",
            List.of(
                new AepEngine.DataPoint(T0, 10.0),
                new AepEngine.DataPoint(T0.plusSeconds(3600), 20.0),
                new AepEngine.DataPoint(T0.plusSeconds(7200), 30.0),
                new AepEngine.DataPoint(T0.plusSeconds(10800), 40.0)
            )
        );

        ForecastingModelComparator.ComparisonResult result = runPromise(() -> comparator.compare("tenant-a", data));

        assertThat(result.winner()).isEqualTo("insufficient-history");
    }
}