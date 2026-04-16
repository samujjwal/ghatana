/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepAnomalyDetector} (AEP-011.3).
 */
@DisplayName("AepAnomalyDetector — AEP-011.3")
class AepAnomalyDetectorTest {

    private AepAnomalyDetector detector;
    private List<AepAnomalyDetector.AnomalyEvent> captured;

    @BeforeEach
    void setUp() {
        captured  = new ArrayList<>();
        detector  = AepAnomalyDetector.builder()
                .zScoreThreshold(3.0)
                .rollingWindowSize(10)
                .build();
        detector.addListener(captured::add);
    }

    @Test
    @DisplayName("Normal value does not trigger anomaly")
    void normalValueNoAnomaly() {
        List<Double> history = List.of(10.0, 10.2, 9.8, 10.1, 10.0, 9.9, 10.3, 10.1, 9.7, 10.0);
        AepAnomalyDetector.AnomalyEvent result = detector.evaluate("series-1", history, 10.15);

        assertThat(result).isNull();
        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("Extreme outlier triggers WARNING anomaly")
    void extremeOutlierTriggersAnomaly() {
        List<Double> history = List.of(10.0, 10.2, 9.8, 10.1, 10.0, 9.9, 10.3, 10.1, 9.7, 10.0);
        AepAnomalyDetector.AnomalyEvent result = detector.evaluate("series-2", history, 50.0);

        assertThat(result).isNotNull();
        assertThat(result.zScore()).isGreaterThan(3.0);
        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("CRITICAL severity for very large deviations")
    void criticalSeverityForVeryLargeDeviation() {
        List<Double> history = List.of(10.0, 10.2, 9.8, 10.1, 10.0, 9.9, 10.3, 10.1, 9.7, 10.0);
        AepAnomalyDetector.AnomalyEvent result = detector.evaluate("series-3", history, 1000.0);

        assertThat(result).isNotNull();
        assertThat(result.severity()).isEqualTo(AepAnomalyDetector.Severity.CRITICAL);
    }

    @Test
    @DisplayName("Returns null when history has fewer than 2 values")
    void insufficientHistoryReturnsNull() {
        AepAnomalyDetector.AnomalyEvent result = detector.evaluate("s", List.of(10.0), 999.0);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Returns null for constant series (stdDev = 0)")
    void constantSeriesReturnsNull() {
        List<Double> history = List.of(5.0, 5.0, 5.0, 5.0, 5.0);
        assertThat(detector.evaluate("constant", history, 999.0)).isNull();
    }

    @Test
    @DisplayName("Low relative deviation does not trigger anomaly in low-variance series")
    void lowRelativeDeviationReturnsNull() {
        List<Double> history = List.of(10.0, 10.1, 9.9, 10.0, 10.2, 9.8, 10.1, 10.0, 9.9, 10.3);
        assertThat(detector.evaluate("low-relative-delta", history, 9.9)).isNull();
    }

    @Test
    @DisplayName("detectedAnomalies accumulates across multiple evaluations")
    void detectedAnomaliesAccumulate() {
        List<Double> history = List.of(10.0, 10.2, 9.8, 10.1, 10.0);
        detector.evaluate("s", history, 100.0);
        detector.evaluate("s", history, 200.0);

        assertThat(detector.detectedAnomalies()).hasSize(2);
    }

    @Test
    @DisplayName("Builder rejects non-positive zScoreThreshold")
    void builderRejectsZeroThreshold() {
        assertThatThrownBy(() -> AepAnomalyDetector.builder().zScoreThreshold(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects rollingWindowSize < 2")
    void builderRejectsWindowSizeLessThan2() {
        assertThatThrownBy(() -> AepAnomalyDetector.builder().rollingWindowSize(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects minRelativeDeviation outside [0,1]")
    void builderRejectsInvalidRelativeDeviation() {
        assertThatThrownBy(() -> AepAnomalyDetector.builder().minRelativeDeviation(1.5))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
