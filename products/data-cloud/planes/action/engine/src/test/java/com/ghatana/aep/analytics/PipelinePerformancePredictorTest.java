/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Unit tests for {@link PipelinePerformancePredictor} (AEP-011.2). 
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("PipelinePerformancePredictor — AEP-011.2")
class PipelinePerformancePredictorTest extends EventloopTestBase {

    private PipelinePerformancePredictor predictor;

    @BeforeEach
    void setUp() { 
        predictor = PipelinePerformancePredictor.builder() 
                .alpha(0.3) 
                .beta(0.1) 
                .executor(Executors.newSingleThreadExecutor()) 
                .build(); 
    }

    @Test
    @DisplayName("predict returns a result with correct horizon length")
    void predictReturnsCorrectHorizon() { 
        List<Double> series = List.of(100.0, 105.0, 110.0, 115.0, 120.0); 
        PipelinePerformancePredictor.PredictionResult result = runPromise( 
                () -> predictor.predict("tenant-1", "throughput", series, 5) 
        );

        assertThat(result).isNotNull(); 
        assertThat(result.forecastValues()).hasSize(5); 
        assertThat(result.trainingPoints()).isEqualTo(5); 
        assertThat(result.tenantId()).isEqualTo("tenant-1");
        assertThat(result.metricName()).isEqualTo("throughput");
    }

    @Test
    @DisplayName("confidence is in [0.5, 0.99]")
    void confidenceInRange() { 
        List<Double> series = List.of(10.0, 12.0, 14.0, 16.0, 18.0); 
        PipelinePerformancePredictor.PredictionResult result = runPromise( 
                () -> predictor.predict("t", "m", series, 3) 
        );

        assertThat(result.confidence()).isBetween(0.5, 0.99); 
    }

    @Test
    @DisplayName("nextStepForecast returns the first forecast value")
    void nextStepForecastMatchesFirstElement() { 
        List<Double> series = List.of(10.0, 11.0, 12.0, 13.0, 14.0); 
        PipelinePerformancePredictor.PredictionResult result = runPromise( 
                () -> predictor.predict("t", "m", series, 2) 
        );

        assertThat(result.nextStepForecast()).isEqualTo(result.forecastValues()[0]); 
    }

    @Test
    @DisplayName("predict throws for fewer than 2 data points")
    void predictThrowsForFewerThan2Points() { 
        // Promise.ofException — runPromise re-throws
        try {
            runPromise(() -> predictor.predict("t", "m", List.of(10.0), 1)); 
        } catch (Exception e) { 
            assertThat(e.getMessage()).containsIgnoringCase("2 data points");
            return;
        }
        throw new AssertionError("Expected an exception");
    }

    @Test
    @DisplayName("predict throws for non-positive horizon")
    void predictThrowsForNonPositiveHorizon() { 
        // horizon <= 0 throws synchronously before a promise is created
        assertThatThrownBy(() -> 
            runPromise(() -> predictor.predict("t", "m", List.of(10.0, 11.0), 0)) 
        ).isInstanceOf(Exception.class); 
    }

    @Test
    @DisplayName("Builder rejects alpha out of (0, 1)")
    void builderRejectsInvalidAlpha() { 
        assertThatThrownBy(() -> PipelinePerformancePredictor.builder().alpha(0.0)) 
                .isInstanceOf(IllegalArgumentException.class); 
        assertThatThrownBy(() -> PipelinePerformancePredictor.builder().alpha(1.0)) 
                .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    @DisplayName("growing series forecasts higher future values")
    void growingSeriesForecasesHigher() { 
        List<Double> series = List.of(100.0, 110.0, 120.0, 130.0, 140.0); 
        PipelinePerformancePredictor.PredictionResult result = runPromise( 
                () -> predictor.predict("t", "m", series, 3) 
        );

        // Each forecast step should be greater than the previous (for a growing series) 
        assertThat(result.forecastValues()[2]).isGreaterThan(result.forecastValues()[0]); 
    }
}
