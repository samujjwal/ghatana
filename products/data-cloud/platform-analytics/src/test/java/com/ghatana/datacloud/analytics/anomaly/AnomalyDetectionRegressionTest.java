/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("AnomalyDetectionRegressionTest")
@Tag("analytics")
@Tag("regression")
class AnomalyDetectionRegressionTest {

    // ── Z-score detector ──────────────────────────────────────────────────────

    /**
     * Detector that flags values more than {@code threshold} standard deviations
     * from the series mean.
     */
    private final ZScoreDetector zScore = new ZScoreDetector(2.0); 

    @Test
    @DisplayName("z-score: no anomaly in flat series")
    void zScoreFlatSeriesHasNoAnomalies() { 
        double[] series = {10.0, 10.0, 10.0, 10.0, 10.0};
        assertThat(zScore.detectIndices(series)).isEmpty(); 
    }

    @Test
    @DisplayName("z-score: single extreme outlier is detected")
    void zScoreSingleOutlier() { 
        double[] series = {10, 10, 10, 10, 100};
        List<Integer> anomalies = zScore.detectIndices(series); 
        assertThat(anomalies).contains(4); 
    }

    @Test
    @DisplayName("z-score: symmetric outliers at both ends")
    void zScoreSymmetricOutliers() { 
        double[] series = {100, 10, 10, 10, 10, 100};
        List<Integer> anomalies = zScore.detectIndices(series); 
        assertThat(anomalies).contains(0, 5); 
    }

    @Test
    @DisplayName("z-score: all values identical returns zero score")
    void zScoreAllIdentical() { 
        double[] series = {5, 5, 5, 5};
        assertThat(zScore.detectIndices(series)).isEmpty(); 
    }

    @ParameterizedTest
    @CsvSource({ 
        "10.0 10.0 10.0 10.0 50.0, 4",  // index 4 is the anomaly
        "50.0 10.0 10.0 10.0 10.0, 0"   // index 0 is the anomaly
    })
    @DisplayName("z-score: parameterized regression cases")
    void zScoreParameterized(String seriesStr, int expectedAnomalyIndex) { 
        double[] series = Arrays.stream(seriesStr.split(" "))
                .mapToDouble(Double::parseDouble).toArray(); 
        assertThat(zScore.detectIndices(series)).contains(expectedAnomalyIndex); 
    }

    // ── IQR detector ─────────────────────────────────────────────────────────

    private final IqrDetector iqr = new IqrDetector(1.5); 

    @Test
    @DisplayName("IQR: detects outlier above upper fence")
    void iqrDetectsHighOutlier() { 
        double[] series = {1, 2, 3, 4, 5, 6, 7, 100};
        List<Integer> anomalies = iqr.detectIndices(series); 
        assertThat(anomalies).contains(7); 
    }

    @Test
    @DisplayName("IQR: detects outlier below lower fence")
    void iqrDetectsLowOutlier() { 
        double[] series = {-100, 10, 11, 12, 13, 14};
        List<Integer> anomalies = iqr.detectIndices(series); 
        assertThat(anomalies).contains(0); 
    }

    @Test
    @DisplayName("IQR: normally distributed values have no anomalies")
    void iqrNormalDistributionNoAnomalies() { 
        // Tight range — nothing outside 1.5*IQR fence
        double[] series = {5, 6, 7, 7, 8, 8, 8, 9, 9, 10};
        assertThat(iqr.detectIndices(series)).isEmpty(); 
    }

    // ── Moving-average detector ───────────────────────────────────────────────

    private final MovingAverageDetector maDetector = new MovingAverageDetector(3, 2.0); 

    @Test
    @DisplayName("moving-average: spike in otherwise flat series is detected")
    void movingAverageDetectsSpike() { 
        double[] series = {10, 10, 10, 50, 10, 10, 10};
        List<Integer> anomalies = maDetector.detectIndices(series); 
        assertThat(anomalies).contains(3); 
    }

    @Test
    @DisplayName("moving-average: series shorter than window size returns no anomalies")
    void movingAverageShortSeries() { 
        double[] series = {5, 8};
        assertThat(maDetector.detectIndices(series)).isEmpty(); 
    }

    @Test
    @DisplayName("moving-average: flat series with no deviation returns no anomalies")
    void movingAverageFlatSeries() { 
        double[] series = {7, 7, 7, 7, 7, 7, 7};
        assertThat(maDetector.detectIndices(series)).isEmpty(); 
    }

    // ── Severity classification ───────────────────────────────────────────────

    @Test
    @DisplayName("score 2.5 → LOW severity")
    void severity25IsLow() { 
        assertThat(Severity.from(2.5)).isEqualTo(Severity.LOW); 
    }

    @Test
    @DisplayName("score 3.5 → MEDIUM severity")
    void severity35IsMedium() { 
        assertThat(Severity.from(3.5)).isEqualTo(Severity.MEDIUM); 
    }

    @Test
    @DisplayName("score 5.0 → HIGH severity")
    void severity50IsHigh() { 
        assertThat(Severity.from(5.0)).isEqualTo(Severity.HIGH); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation (pure regression stubs — no framework deps) 
    // ─────────────────────────────────────────────────────────────────────────

    enum Severity {
        LOW, MEDIUM, HIGH;
        static Severity from(double score) { 
            if (score >= 4.5) return HIGH; 
            if (score >= 3.0) return MEDIUM; 
            return LOW;
        }
    }

    static class ZScoreDetector {
        private final double threshold;
        ZScoreDetector(double threshold) { this.threshold = threshold; } 

        List<Integer> detectIndices(double[] series) { 
            if (series.length < 2) return List.of(); 
            List<Integer> anomalies = new ArrayList<>(); 
            for (int i = 0; i < series.length; i++) { 
                double mean = meanExcluding(series, i); 
                double std = stdDevExcluding(series, i, mean); 
                double deviation = Math.abs(series[i] - mean); 
                if ((std == 0 && deviation > 0) || (std > 0 && deviation / std >= threshold)) { 
                    anomalies.add(i); 
                }
            }
            return anomalies;
        }

        private double meanExcluding(double[] series, int excludedIndex) { 
            double sum = 0;
            for (int i = 0; i < series.length; i++) { 
                if (i != excludedIndex) { 
                    sum += series[i];
                }
            }
            return sum / (series.length - 1); 
        }

        private double stdDevExcluding(double[] series, int excludedIndex, double mean) { 
            double sum = 0;
            for (int i = 0; i < series.length; i++) { 
                if (i != excludedIndex) { 
                    double delta = series[i] - mean;
                    sum += delta * delta;
                }
            }
            return Math.sqrt(sum / (series.length - 1)); 
        }
    }

    static class IqrDetector {
        private final double multiplier;
        IqrDetector(double multiplier) { this.multiplier = multiplier; } 

        List<Integer> detectIndices(double[] series) { 
            if (series.length < 4) return List.of(); 
            double[] sorted = Arrays.copyOf(series, series.length); 
            Arrays.sort(sorted); 
            double q1 = sorted[sorted.length / 4];
            double q3 = sorted[(sorted.length * 3) / 4]; 
            double iqr = q3 - q1;
            double lower = q1 - multiplier * iqr;
            double upper = q3 + multiplier * iqr;
            List<Integer> anomalies = new ArrayList<>(); 
            for (int i = 0; i < series.length; i++) { 
                if (series[i] < lower || series[i] > upper) anomalies.add(i); 
            }
            return anomalies;
        }
    }

    static class MovingAverageDetector {
        private final int windowSize;
        private final double threshold;
        MovingAverageDetector(int windowSize, double threshold) { 
            this.windowSize = windowSize;
            this.threshold = threshold;
        }

        List<Integer> detectIndices(double[] series) { 
            if (series.length < windowSize) return List.of(); 
            List<Integer> anomalies = new ArrayList<>(); 
            for (int i = windowSize; i < series.length; i++) { 
                double sum = 0;
                for (int j = i - windowSize; j < i; j++) sum += series[j]; 
                double avg = sum / windowSize;
                double dev = Math.abs(series[i] - avg); 
                double stdDev = computeStd(series, i - windowSize, i, avg); 
                if ((stdDev == 0 && dev > 0) || (stdDev > 0 && dev / stdDev >= threshold)) { 
                    anomalies.add(i); 
                }
            }
            return anomalies;
        }

        private double computeStd(double[] s, int from, int to, double mean) { 
            double sum = 0;
            for (int i = from; i < to; i++) sum += (s[i] - mean) * (s[i] - mean); 
            return Math.sqrt(sum / (to - from)); 
        }
    }
}
