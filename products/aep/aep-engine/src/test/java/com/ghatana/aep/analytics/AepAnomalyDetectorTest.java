/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Unit tests for {@link AepAnomalyDetector} (AEP-011.3). // GH-90000
 */
@DisplayName("AepAnomalyDetector — AEP-011.3")
class AepAnomalyDetectorTest {

    private AepAnomalyDetector detector;
    private List<AepAnomalyDetector.AnomalyEvent> captured;

    @BeforeEach
    void setUp() { // GH-90000
        captured  = new ArrayList<>(); // GH-90000
        detector  = AepAnomalyDetector.builder() // GH-90000
                .zScoreThreshold(3.0) // GH-90000
                .rollingWindowSize(10) // GH-90000
                .build(); // GH-90000
        detector.addListener(captured::add); // GH-90000
    }

    @Test
    @DisplayName("Normal value does not trigger anomaly")
    void normalValueNoAnomaly() { // GH-90000
        List<Double> history = List.of(10.0, 10.2, 9.8, 10.1, 10.0, 9.9, 10.3, 10.1, 9.7, 10.0); // GH-90000
        AepAnomalyDetector.AnomalyEvent result = detector.evaluate("series-1", history, 10.15); // GH-90000

        assertThat(result).isNull(); // GH-90000
        assertThat(captured).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Extreme outlier triggers WARNING anomaly")
    void extremeOutlierTriggersAnomaly() { // GH-90000
        List<Double> history = List.of(10.0, 10.2, 9.8, 10.1, 10.0, 9.9, 10.3, 10.1, 9.7, 10.0); // GH-90000
        AepAnomalyDetector.AnomalyEvent result = detector.evaluate("series-2", history, 50.0); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.zScore()).isGreaterThan(3.0); // GH-90000
        assertThat(captured).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("CRITICAL severity for very large deviations")
    void criticalSeverityForVeryLargeDeviation() { // GH-90000
        List<Double> history = List.of(10.0, 10.2, 9.8, 10.1, 10.0, 9.9, 10.3, 10.1, 9.7, 10.0); // GH-90000
        AepAnomalyDetector.AnomalyEvent result = detector.evaluate("series-3", history, 1000.0); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.severity()).isEqualTo(AepAnomalyDetector.Severity.CRITICAL); // GH-90000
    }

    @Test
    @DisplayName("Returns null when history has fewer than 2 values")
    void insufficientHistoryReturnsNull() { // GH-90000
        AepAnomalyDetector.AnomalyEvent result = detector.evaluate("s", List.of(10.0), 999.0); // GH-90000
        assertThat(result).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Returns null for constant series (stdDev = 0)")
    void constantSeriesReturnsNull() { // GH-90000
        List<Double> history = List.of(5.0, 5.0, 5.0, 5.0, 5.0); // GH-90000
        assertThat(detector.evaluate("constant", history, 999.0)).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Low relative deviation does not trigger anomaly in low-variance series")
    void lowRelativeDeviationReturnsNull() { // GH-90000
        List<Double> history = List.of(10.0, 10.1, 9.9, 10.0, 10.2, 9.8, 10.1, 10.0, 9.9, 10.3); // GH-90000
        assertThat(detector.evaluate("low-relative-delta", history, 9.9)).isNull(); // GH-90000
    }

    @Test
    @DisplayName("detectedAnomalies accumulates across multiple evaluations")
    void detectedAnomaliesAccumulate() { // GH-90000
        List<Double> history = List.of(10.0, 10.2, 9.8, 10.1, 10.0); // GH-90000
        detector.evaluate("s", history, 100.0); // GH-90000
        detector.evaluate("s", history, 200.0); // GH-90000

        assertThat(detector.detectedAnomalies()).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects non-positive zScoreThreshold")
    void builderRejectsZeroThreshold() { // GH-90000
        assertThatThrownBy(() -> AepAnomalyDetector.builder().zScoreThreshold(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects rollingWindowSize < 2")
    void builderRejectsWindowSizeLessThan2() { // GH-90000
        assertThatThrownBy(() -> AepAnomalyDetector.builder().rollingWindowSize(1)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects minRelativeDeviation outside [0,1]")
    void builderRejectsInvalidRelativeDeviation() { // GH-90000
        assertThatThrownBy(() -> AepAnomalyDetector.builder().minRelativeDeviation(1.5)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
}
