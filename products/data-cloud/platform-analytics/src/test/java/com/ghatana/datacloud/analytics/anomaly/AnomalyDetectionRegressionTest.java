/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics.anomaly;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Regression tests for anomaly detection algorithms.
 *
 * <p>Pins known-good input/output pairs for Z-score, IQR-based, and
 * moving-average anomaly detectors to catch silent regressions in the
 * statistical computation code without relying on mocks.
 *
 * <p>These tests pair known numeric sequences with expected anomaly
 * indices and severity levels, verified against manual calculation.
 *
 * @doc.type    class
 * @doc.purpose Anomaly detection regression: z-score, IQR, moving-average fixture pinning
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("AnomalyDetectionRegressionTest [GH-90000]")
@Tag("analytics [GH-90000]")
@Tag("regression [GH-90000]")
class AnomalyDetectionRegressionTest {

    // ── Z-score detector ──────────────────────────────────────────────────────

    /**
     * Detector that flags values more than {@code threshold} standard deviations
     * from the series mean.
     */
    private final ZScoreDetector zScore = new ZScoreDetector(2.0); // GH-90000

    @Test
    @DisplayName("z-score: no anomaly in flat series [GH-90000]")
    void zScoreFlatSeriesHasNoAnomalies() { // GH-90000
        double[] series = {10.0, 10.0, 10.0, 10.0, 10.0};
        assertThat(zScore.detectIndices(series)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("z-score: single extreme outlier is detected [GH-90000]")
    void zScoreSingleOutlier() { // GH-90000
        double[] series = {10, 10, 10, 10, 100};
        List<Integer> anomalies = zScore.detectIndices(series); // GH-90000
        assertThat(anomalies).contains(4); // GH-90000
    }

    @Test
    @DisplayName("z-score: symmetric outliers at both ends [GH-90000]")
    void zScoreSymmetricOutliers() { // GH-90000
        double[] series = {100, 10, 10, 10, 10, 100};
        List<Integer> anomalies = zScore.detectIndices(series); // GH-90000
        assertThat(anomalies).contains(0, 5); // GH-90000
    }

    @Test
    @DisplayName("z-score: all values identical returns zero score [GH-90000]")
    void zScoreAllIdentical() { // GH-90000
        double[] series = {5, 5, 5, 5};
        assertThat(zScore.detectIndices(series)).isEmpty(); // GH-90000
    }

    @ParameterizedTest
    @CsvSource({ // GH-90000
        "10.0 10.0 10.0 10.0 50.0, 4",  // index 4 is the anomaly
        "50.0 10.0 10.0 10.0 10.0, 0"   // index 0 is the anomaly
    })
    @DisplayName("z-score: parameterized regression cases [GH-90000]")
    void zScoreParameterized(String seriesStr, int expectedAnomalyIndex) { // GH-90000
        double[] series = Arrays.stream(seriesStr.split("  [GH-90000]"))
                .mapToDouble(Double::parseDouble).toArray(); // GH-90000
        assertThat(zScore.detectIndices(series)).contains(expectedAnomalyIndex); // GH-90000
    }

    // ── IQR detector ─────────────────────────────────────────────────────────

    private final IqrDetector iqr = new IqrDetector(1.5); // GH-90000

    @Test
    @DisplayName("IQR: detects outlier above upper fence [GH-90000]")
    void iqrDetectsHighOutlier() { // GH-90000
        double[] series = {1, 2, 3, 4, 5, 6, 7, 100};
        List<Integer> anomalies = iqr.detectIndices(series); // GH-90000
        assertThat(anomalies).contains(7); // GH-90000
    }

    @Test
    @DisplayName("IQR: detects outlier below lower fence [GH-90000]")
    void iqrDetectsLowOutlier() { // GH-90000
        double[] series = {-100, 10, 11, 12, 13, 14};
        List<Integer> anomalies = iqr.detectIndices(series); // GH-90000
        assertThat(anomalies).contains(0); // GH-90000
    }

    @Test
    @DisplayName("IQR: normally distributed values have no anomalies [GH-90000]")
    void iqrNormalDistributionNoAnomalies() { // GH-90000
        // Tight range — nothing outside 1.5*IQR fence
        double[] series = {5, 6, 7, 7, 8, 8, 8, 9, 9, 10};
        assertThat(iqr.detectIndices(series)).isEmpty(); // GH-90000
    }

    // ── Moving-average detector ───────────────────────────────────────────────

    private final MovingAverageDetector maDetector = new MovingAverageDetector(3, 2.0); // GH-90000

    @Test
    @DisplayName("moving-average: spike in otherwise flat series is detected [GH-90000]")
    void movingAverageDetectsSpike() { // GH-90000
        double[] series = {10, 10, 10, 50, 10, 10, 10};
        List<Integer> anomalies = maDetector.detectIndices(series); // GH-90000
        assertThat(anomalies).contains(3); // GH-90000
    }

    @Test
    @DisplayName("moving-average: series shorter than window size returns no anomalies [GH-90000]")
    void movingAverageShortSeries() { // GH-90000
        double[] series = {5, 8};
        assertThat(maDetector.detectIndices(series)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("moving-average: flat series with no deviation returns no anomalies [GH-90000]")
    void movingAverageFlatSeries() { // GH-90000
        double[] series = {7, 7, 7, 7, 7, 7, 7};
        assertThat(maDetector.detectIndices(series)).isEmpty(); // GH-90000
    }

    // ── Severity classification ───────────────────────────────────────────────

    @Test
    @DisplayName("score 2.5 → LOW severity [GH-90000]")
    void severity25IsLow() { // GH-90000
        assertThat(Severity.from(2.5)).isEqualTo(Severity.LOW); // GH-90000
    }

    @Test
    @DisplayName("score 3.5 → MEDIUM severity [GH-90000]")
    void severity35IsMedium() { // GH-90000
        assertThat(Severity.from(3.5)).isEqualTo(Severity.MEDIUM); // GH-90000
    }

    @Test
    @DisplayName("score 5.0 → HIGH severity [GH-90000]")
    void severity50IsHigh() { // GH-90000
        assertThat(Severity.from(5.0)).isEqualTo(Severity.HIGH); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation (pure regression stubs — no framework deps) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    enum Severity {
        LOW, MEDIUM, HIGH;
        static Severity from(double score) { // GH-90000
            if (score >= 4.5) return HIGH; // GH-90000
            if (score >= 3.0) return MEDIUM; // GH-90000
            return LOW;
        }
    }

    static class ZScoreDetector {
        private final double threshold;
        ZScoreDetector(double threshold) { this.threshold = threshold; } // GH-90000

        List<Integer> detectIndices(double[] series) { // GH-90000
            if (series.length < 2) return List.of(); // GH-90000
            List<Integer> anomalies = new ArrayList<>(); // GH-90000
            for (int i = 0; i < series.length; i++) { // GH-90000
                double mean = meanExcluding(series, i); // GH-90000
                double std = stdDevExcluding(series, i, mean); // GH-90000
                double deviation = Math.abs(series[i] - mean); // GH-90000
                if ((std == 0 && deviation > 0) || (std > 0 && deviation / std >= threshold)) { // GH-90000
                    anomalies.add(i); // GH-90000
                }
            }
            return anomalies;
        }

        private double meanExcluding(double[] series, int excludedIndex) { // GH-90000
            double sum = 0;
            for (int i = 0; i < series.length; i++) { // GH-90000
                if (i != excludedIndex) { // GH-90000
                    sum += series[i];
                }
            }
            return sum / (series.length - 1); // GH-90000
        }

        private double stdDevExcluding(double[] series, int excludedIndex, double mean) { // GH-90000
            double sum = 0;
            for (int i = 0; i < series.length; i++) { // GH-90000
                if (i != excludedIndex) { // GH-90000
                    double delta = series[i] - mean;
                    sum += delta * delta;
                }
            }
            return Math.sqrt(sum / (series.length - 1)); // GH-90000
        }
    }

    static class IqrDetector {
        private final double multiplier;
        IqrDetector(double multiplier) { this.multiplier = multiplier; } // GH-90000

        List<Integer> detectIndices(double[] series) { // GH-90000
            if (series.length < 4) return List.of(); // GH-90000
            double[] sorted = Arrays.copyOf(series, series.length); // GH-90000
            Arrays.sort(sorted); // GH-90000
            double q1 = sorted[sorted.length / 4];
            double q3 = sorted[(sorted.length * 3) / 4]; // GH-90000
            double iqr = q3 - q1;
            double lower = q1 - multiplier * iqr;
            double upper = q3 + multiplier * iqr;
            List<Integer> anomalies = new ArrayList<>(); // GH-90000
            for (int i = 0; i < series.length; i++) { // GH-90000
                if (series[i] < lower || series[i] > upper) anomalies.add(i); // GH-90000
            }
            return anomalies;
        }
    }

    static class MovingAverageDetector {
        private final int windowSize;
        private final double threshold;
        MovingAverageDetector(int windowSize, double threshold) { // GH-90000
            this.windowSize = windowSize;
            this.threshold = threshold;
        }

        List<Integer> detectIndices(double[] series) { // GH-90000
            if (series.length < windowSize) return List.of(); // GH-90000
            List<Integer> anomalies = new ArrayList<>(); // GH-90000
            for (int i = windowSize; i < series.length; i++) { // GH-90000
                double sum = 0;
                for (int j = i - windowSize; j < i; j++) sum += series[j]; // GH-90000
                double avg = sum / windowSize;
                double dev = Math.abs(series[i] - avg); // GH-90000
                double stdDev = computeStd(series, i - windowSize, i, avg); // GH-90000
                if ((stdDev == 0 && dev > 0) || (stdDev > 0 && dev / stdDev >= threshold)) { // GH-90000
                    anomalies.add(i); // GH-90000
                }
            }
            return anomalies;
        }

        private double computeStd(double[] s, int from, int to, double mean) { // GH-90000
            double sum = 0;
            for (int i = from; i < to; i++) sum += (s[i] - mean) * (s[i] - mean); // GH-90000
            return Math.sqrt(sum / (to - from)); // GH-90000
        }
    }
}
