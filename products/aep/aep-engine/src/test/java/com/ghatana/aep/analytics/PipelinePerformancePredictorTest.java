/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PipelinePerformancePredictor} (AEP-011.2). // GH-90000
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("PipelinePerformancePredictor — AEP-011.2")
class PipelinePerformancePredictorTest extends EventloopTestBase {

    private PipelinePerformancePredictor predictor;

    @BeforeEach
    void setUp() { // GH-90000
        predictor = PipelinePerformancePredictor.builder() // GH-90000
                .alpha(0.3) // GH-90000
                .beta(0.1) // GH-90000
                .executor(Executors.newSingleThreadExecutor()) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("predict returns a result with correct horizon length")
    void predictReturnsCorrectHorizon() { // GH-90000
        List<Double> series = List.of(100.0, 105.0, 110.0, 115.0, 120.0); // GH-90000
        PipelinePerformancePredictor.PredictionResult result = runPromise( // GH-90000
                () -> predictor.predict("tenant-1", "throughput", series, 5) // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.forecastValues()).hasSize(5); // GH-90000
        assertThat(result.trainingPoints()).isEqualTo(5); // GH-90000
        assertThat(result.tenantId()).isEqualTo("tenant-1");
        assertThat(result.metricName()).isEqualTo("throughput");
    }

    @Test
    @DisplayName("confidence is in [0.5, 0.99]")
    void confidenceInRange() { // GH-90000
        List<Double> series = List.of(10.0, 12.0, 14.0, 16.0, 18.0); // GH-90000
        PipelinePerformancePredictor.PredictionResult result = runPromise( // GH-90000
                () -> predictor.predict("t", "m", series, 3) // GH-90000
        );

        assertThat(result.confidence()).isBetween(0.5, 0.99); // GH-90000
    }

    @Test
    @DisplayName("nextStepForecast returns the first forecast value")
    void nextStepForecastMatchesFirstElement() { // GH-90000
        List<Double> series = List.of(10.0, 11.0, 12.0, 13.0, 14.0); // GH-90000
        PipelinePerformancePredictor.PredictionResult result = runPromise( // GH-90000
                () -> predictor.predict("t", "m", series, 2) // GH-90000
        );

        assertThat(result.nextStepForecast()).isEqualTo(result.forecastValues()[0]); // GH-90000
    }

    @Test
    @DisplayName("predict throws for fewer than 2 data points")
    void predictThrowsForFewerThan2Points() { // GH-90000
        // Promise.ofException — runPromise re-throws
        try {
            runPromise(() -> predictor.predict("t", "m", List.of(10.0), 1)); // GH-90000
        } catch (Exception e) { // GH-90000
            assertThat(e.getMessage()).containsIgnoringCase("2 data points");
            return;
        }
        throw new AssertionError("Expected an exception");
    }

    @Test
    @DisplayName("predict throws for non-positive horizon")
    void predictThrowsForNonPositiveHorizon() { // GH-90000
        // horizon <= 0 throws synchronously before a promise is created
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> predictor.predict("t", "m", List.of(10.0, 11.0), 0)) // GH-90000
        ).isInstanceOf(Exception.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects alpha out of (0, 1)")
    void builderRejectsInvalidAlpha() { // GH-90000
        assertThatThrownBy(() -> PipelinePerformancePredictor.builder().alpha(0.0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        assertThatThrownBy(() -> PipelinePerformancePredictor.builder().alpha(1.0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("growing series forecasts higher future values")
    void growingSeriesForecasesHigher() { // GH-90000
        List<Double> series = List.of(100.0, 110.0, 120.0, 130.0, 140.0); // GH-90000
        PipelinePerformancePredictor.PredictionResult result = runPromise( // GH-90000
                () -> predictor.predict("t", "m", series, 3) // GH-90000
        );

        // Each forecast step should be greater than the previous (for a growing series) // GH-90000
        assertThat(result.forecastValues()[2]).isGreaterThan(result.forecastValues()[0]); // GH-90000
    }
}
