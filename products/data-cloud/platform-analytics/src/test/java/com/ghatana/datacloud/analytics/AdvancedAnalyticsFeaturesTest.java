/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for AdvancedAnalyticsFeatures.
 *
 * @doc.type class
 * @doc.purpose Tests for advanced analytics features
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AdvancedAnalyticsFeatures Tests [GH-90000]")
class AdvancedAnalyticsFeaturesTest {

    @Nested
    @DisplayName("Percentile Calculation [GH-90000]")
    class PercentileTests {

        @Test
        @DisplayName("calculatePercentile with valid values returns correct percentile [GH-90000]")
        void calculatePercentile_validValues_returnsCorrect() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0); // GH-90000
            
            double p50 = AdvancedAnalyticsFeatures.calculatePercentile(values, 0.5); // GH-90000
            double p90 = AdvancedAnalyticsFeatures.calculatePercentile(values, 0.9); // GH-90000
            
            assertThat(p50).isEqualTo(5.0); // GH-90000
            assertThat(p90).isEqualTo(9.0); // GH-90000
        }

        @Test
        @DisplayName("calculatePercentile with null values throws exception [GH-90000]")
        void calculatePercentile_nullValues_throws() { // GH-90000
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculatePercentile(null, 0.5)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("cannot be null or empty [GH-90000]");
        }

        @Test
        @DisplayName("calculatePercentile with empty values throws exception [GH-90000]")
        void calculatePercentile_emptyValues_throws() { // GH-90000
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculatePercentile(List.of(), 0.5)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("cannot be null or empty [GH-90000]");
        }

        @Test
        @DisplayName("calculatePercentile with invalid percentile throws exception [GH-90000]")
        void calculatePercentile_invalidPercentile_throws() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0); // GH-90000
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculatePercentile(values, -0.1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculatePercentile(values, 1.1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("calculatePercentile with single value returns that value [GH-90000]")
        void calculatePercentile_singleValue_returnsValue() { // GH-90000
            List<Double> values = List.of(42.0); // GH-90000
            
            double p50 = AdvancedAnalyticsFeatures.calculatePercentile(values, 0.5); // GH-90000
            
            assertThat(p50).isEqualTo(42.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Rolling Average [GH-90000]")
    class RollingAverageTests {

        @Test
        @DisplayName("calculateRollingAverage with valid values returns correct averages [GH-90000]")
        void calculateRollingAverage_validValues_returnsCorrect() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0); // GH-90000
            
            List<Double> rolling = AdvancedAnalyticsFeatures.calculateRollingAverage(values, 3); // GH-90000
            
            assertThat(rolling).hasSize(5); // GH-90000
            assertThat(rolling.get(0)).isEqualTo(1.0); // GH-90000
            assertThat(rolling.get(1)).isEqualTo(1.5); // GH-90000
            assertThat(rolling.get(2)).isEqualTo(2.0); // GH-90000
            assertThat(rolling.get(3)).isEqualTo(3.0); // GH-90000
            assertThat(rolling.get(4)).isEqualTo(4.0); // GH-90000
        }

        @Test
        @DisplayName("calculateRollingAverage with null values returns empty list [GH-90000]")
        void calculateRollingAverage_nullValues_returnsEmpty() { // GH-90000
            List<Double> rolling = AdvancedAnalyticsFeatures.calculateRollingAverage(null, 3); // GH-90000
            assertThat(rolling).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculateRollingAverage with empty values returns empty list [GH-90000]")
        void calculateRollingAverage_emptyValues_returnsEmpty() { // GH-90000
            List<Double> rolling = AdvancedAnalyticsFeatures.calculateRollingAverage(List.of(), 3); // GH-90000
            assertThat(rolling).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculateRollingAverage with invalid window size throws exception [GH-90000]")
        void calculateRollingAverage_invalidWindow_throws() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0); // GH-90000
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateRollingAverage(values, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateRollingAverage(values, -1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Histogram Generation [GH-90000]")
    class HistogramTests {

        @Test
        @DisplayName("generateHistogram with valid values returns correct histogram [GH-90000]")
        void generateHistogram_validValues_returnsCorrect() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0); // GH-90000
            
            Map<String, Integer> histogram = AdvancedAnalyticsFeatures.generateHistogram(values, 5); // GH-90000
            
            assertThat(histogram).hasSize(5); // GH-90000
            assertThat(histogram.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("generateHistogram with null values returns empty map [GH-90000]")
        void generateHistogram_nullValues_returnsEmpty() { // GH-90000
            Map<String, Integer> histogram = AdvancedAnalyticsFeatures.generateHistogram(null, 5); // GH-90000
            assertThat(histogram).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("generateHistogram with empty values returns empty map [GH-90000]")
        void generateHistogram_emptyValues_returnsEmpty() { // GH-90000
            Map<String, Integer> histogram = AdvancedAnalyticsFeatures.generateHistogram(List.of(), 5); // GH-90000
            assertThat(histogram).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("generateHistogram with invalid bin count throws exception [GH-90000]")
        void generateHistogram_invalidBinCount_throws() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0); // GH-90000
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.generateHistogram(values, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Trend Calculation [GH-90000]")
    class TrendTests {

        @Test
        @DisplayName("calculateTrend with increasing values returns positive slope [GH-90000]")
        void calculateTrend_increasing_returnsPositive() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0); // GH-90000
            
            double trend = AdvancedAnalyticsFeatures.calculateTrend(values); // GH-90000
            
            assertThat(trend).isPositive(); // GH-90000
        }

        @Test
        @DisplayName("calculateTrend with decreasing values returns negative slope [GH-90000]")
        void calculateTrend_decreasing_returnsNegative() { // GH-90000
            List<Double> values = List.of(5.0, 4.0, 3.0, 2.0, 1.0); // GH-90000
            
            double trend = AdvancedAnalyticsFeatures.calculateTrend(values); // GH-90000
            
            assertThat(trend).isNegative(); // GH-90000
        }

        @Test
        @DisplayName("calculateTrend with constant values returns zero slope [GH-90000]")
        void calculateTrend_constant_returnsZero() { // GH-90000
            List<Double> values = List.of(5.0, 5.0, 5.0, 5.0); // GH-90000
            
            double trend = AdvancedAnalyticsFeatures.calculateTrend(values); // GH-90000
            
            assertThat(trend).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("calculateTrend with null values returns zero [GH-90000]")
        void calculateTrend_null_returnsZero() { // GH-90000
            double trend = AdvancedAnalyticsFeatures.calculateTrend(null); // GH-90000
            assertThat(trend).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("calculateTrend with single value returns zero [GH-90000]")
        void calculateTrend_singleValue_returnsZero() { // GH-90000
            double trend = AdvancedAnalyticsFeatures.calculateTrend(List.of(42.0)); // GH-90000
            assertThat(trend).isEqualTo(0.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Time Series Aggregation [GH-90000]")
    class TimeSeriesAggregationTests {

        @Test
        @DisplayName("aggregateTimeSeries with valid data returns aggregated results [GH-90000]")
        void aggregateTimeSeries_validData_returnsAggregated() { // GH-90000
            List<Map<String, Object>> data = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                Map<String, Object> point = new HashMap<>(); // GH-90000
                point.put("timestamp", (long) i * 1000); // GH-90000
                point.put("value", (double) i); // GH-90000
                data.add(point); // GH-90000
            }
            
            List<Map<String, Object>> aggregated = AdvancedAnalyticsFeatures.aggregateTimeSeries(data, 3000); // GH-90000
            
            assertThat(aggregated).isNotEmpty(); // GH-90000
            assertThat(aggregated.get(0)).containsKeys("timestamp", "count", "sum", "avg", "min", "max"); // GH-90000
        }

        @Test
        @DisplayName("aggregateTimeSeries with null data returns empty list [GH-90000]")
        void aggregateTimeSeries_null_returnsEmpty() { // GH-90000
            List<Map<String, Object>> aggregated = AdvancedAnalyticsFeatures.aggregateTimeSeries(null, 3000); // GH-90000
            assertThat(aggregated).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("aggregateTimeSeries with empty data returns empty list [GH-90000]")
        void aggregateTimeSeries_empty_returnsEmpty() { // GH-90000
            List<Map<String, Object>> aggregated = AdvancedAnalyticsFeatures.aggregateTimeSeries(List.of(), 3000); // GH-90000
            assertThat(aggregated).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Moving Standard Deviation [GH-90000]")
    class MovingStdDevTests {

        @Test
        @DisplayName("calculateMovingStdDev with valid values returns correct std devs [GH-90000]")
        void calculateMovingStdDev_validValues_returnsCorrect() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0); // GH-90000
            
            List<Double> stdDevs = AdvancedAnalyticsFeatures.calculateMovingStdDev(values, 3); // GH-90000
            
            assertThat(stdDevs).hasSize(5); // GH-90000
            assertThat(stdDevs).allMatch(v -> v >= 0); // GH-90000
        }

        @Test
        @DisplayName("calculateMovingStdDev with null values returns empty list [GH-90000]")
        void calculateMovingStdDev_null_returnsEmpty() { // GH-90000
            List<Double> stdDevs = AdvancedAnalyticsFeatures.calculateMovingStdDev(null, 3); // GH-90000
            assertThat(stdDevs).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculateMovingStdDev with empty values returns empty list [GH-90000]")
        void calculateMovingStdDev_empty_returnsEmpty() { // GH-90000
            List<Double> stdDevs = AdvancedAnalyticsFeatures.calculateMovingStdDev(List.of(), 3); // GH-90000
            assertThat(stdDevs).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculateMovingStdDev with invalid window size throws exception [GH-90000]")
        void calculateMovingStdDev_invalidWindow_throws() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0); // GH-90000
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateMovingStdDev(values, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Exponential Moving Average [GH-90000]")
    class EmaTests {

        @Test
        @DisplayName("calculateExponentialMovingAverage with valid values returns correct EMA [GH-90000]")
        void calculateEma_validValues_returnsCorrect() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0); // GH-90000
            
            List<Double> ema = AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(values, 0.5); // GH-90000
            
            assertThat(ema).hasSize(5); // GH-90000
            assertThat(ema.get(0)).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("calculateExponentialMovingAverage with null values returns empty list [GH-90000]")
        void calculateEma_null_returnsEmpty() { // GH-90000
            List<Double> ema = AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(null, 0.5); // GH-90000
            assertThat(ema).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculateExponentialMovingAverage with empty values returns empty list [GH-90000]")
        void calculateEma_empty_returnsEmpty() { // GH-90000
            List<Double> ema = AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(List.of(), 0.5); // GH-90000
            assertThat(ema).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculateExponentialMovingAverage with invalid alpha throws exception [GH-90000]")
        void calculateEma_invalidAlpha_throws() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0); // GH-90000
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(values, -0.1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(values, 1.1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Z-Score Calculation [GH-90000]")
    class ZScoreTests {

        @Test
        @DisplayName("calculateZScores with valid values returns correct z-scores [GH-90000]")
        void calculateZScores_validValues_returnsCorrect() { // GH-90000
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0); // GH-90000
            
            List<Double> zScores = AdvancedAnalyticsFeatures.calculateZScores(values); // GH-90000
            
            assertThat(zScores).hasSize(5); // GH-90000
            // Mean should be 3.0, std dev ~1.58
            // z-scores should be centered around 0
            double mean = zScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0); // GH-90000
            assertThat(mean).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01)); // GH-90000
        }

        @Test
        @DisplayName("calculateZScores with null values returns empty list [GH-90000]")
        void calculateZScores_null_returnsEmpty() { // GH-90000
            List<Double> zScores = AdvancedAnalyticsFeatures.calculateZScores(null); // GH-90000
            assertThat(zScores).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculateZScores with empty values returns empty list [GH-90000]")
        void calculateZScores_empty_returnsEmpty() { // GH-90000
            List<Double> zScores = AdvancedAnalyticsFeatures.calculateZScores(List.of()); // GH-90000
            assertThat(zScores).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculateZScores with constant values returns all zeros [GH-90000]")
        void calculateZScores_constant_returnsZeros() { // GH-90000
            List<Double> values = List.of(5.0, 5.0, 5.0, 5.0); // GH-90000
            
            List<Double> zScores = AdvancedAnalyticsFeatures.calculateZScores(values); // GH-90000
            
            assertThat(zScores).hasSize(4); // GH-90000
            assertThat(zScores).allMatch(v -> v == 0.0); // GH-90000
        }
    }
}
