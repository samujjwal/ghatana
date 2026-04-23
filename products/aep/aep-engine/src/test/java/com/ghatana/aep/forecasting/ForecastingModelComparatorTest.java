/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void adaptiveBeatsNaiveOnStableTrend() { // GH-90000
        ForecastingModelComparator comparator = new ForecastingModelComparator( // GH-90000
            new NaiveForecastingEngine(3, 3600L), // GH-90000
            new AdaptiveForecastingEngine( // GH-90000
                new NaiveForecastingEngine(3, 3600L), // GH-90000
                new LinearTrendForecastingEngine(3, 3600L), // GH-90000
                new StatisticalForecastingEngine(0.3, 0.1, 3, 3600L, Executors.newSingleThreadExecutor()), // GH-90000
                new OnlineRegressionForecastingEngine(3, 3600L, 200, 0.05, Runnable::run), // GH-90000
                2
            ),
            3
        );

        AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData( // GH-90000
            "throughput",
            List.of( // GH-90000
                new AepEngine.DataPoint(T0, 10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 20.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 30.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(10800), 40.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(14400), 50.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(18000), 60.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(21600), 70.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(25200), 80.0) // GH-90000
            )
        );

        ForecastingModelComparator.ComparisonResult result = runPromise(() -> comparator.compare("tenant-a", data)); // GH-90000

        assertThat(result.baselineAlgorithm()).isEqualTo("naive");
        assertThat(result.candidateAlgorithm()).isEqualTo("adaptive");
        assertThat(result.winner()).isEqualTo("adaptive");
        assertThat(result.candidateRmse()).isLessThan(result.baselineRmse()); // GH-90000
        assertThat(result.asMetadata()).containsEntry("winner", "adaptive"); // GH-90000
    }

    @Test
    @DisplayName("returns insufficient-history when holdout split cannot be formed")
    void returnsInsufficientHistoryForShortSeries() { // GH-90000
        ForecastingModelComparator comparator = new ForecastingModelComparator( // GH-90000
            new NaiveForecastingEngine(), // GH-90000
            new LinearTrendForecastingEngine(), // GH-90000
            3
        );

        AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData( // GH-90000
            "throughput",
            List.of( // GH-90000
                new AepEngine.DataPoint(T0, 10.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(3600), 20.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(7200), 30.0), // GH-90000
                new AepEngine.DataPoint(T0.plusSeconds(10800), 40.0) // GH-90000
            )
        );

        ForecastingModelComparator.ComparisonResult result = runPromise(() -> comparator.compare("tenant-a", data)); // GH-90000

        assertThat(result.winner()).isEqualTo("insufficient-history");
    }
}