/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("AdvancedAnalyticsFeatures Tests")
class AdvancedAnalyticsFeaturesTest {

    @Nested
    @DisplayName("Percentile Calculation")
    class PercentileTests {

        @Test
        @DisplayName("calculatePercentile with valid values returns correct percentile")
        void calculatePercentile_validValues_returnsCorrect() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);
            
            double p50 = AdvancedAnalyticsFeatures.calculatePercentile(values, 0.5);
            double p90 = AdvancedAnalyticsFeatures.calculatePercentile(values, 0.9);
            
            assertThat(p50).isEqualTo(5.0);
            assertThat(p90).isEqualTo(9.0);
        }

        @Test
        @DisplayName("calculatePercentile with null values throws exception")
        void calculatePercentile_nullValues_throws() {
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculatePercentile(null, 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("calculatePercentile with empty values throws exception")
        void calculatePercentile_emptyValues_throws() {
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculatePercentile(List.of(), 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("calculatePercentile with invalid percentile throws exception")
        void calculatePercentile_invalidPercentile_throws() {
            List<Double> values = List.of(1.0, 2.0, 3.0);
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculatePercentile(values, -0.1))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculatePercentile(values, 1.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("calculatePercentile with single value returns that value")
        void calculatePercentile_singleValue_returnsValue() {
            List<Double> values = List.of(42.0);
            
            double p50 = AdvancedAnalyticsFeatures.calculatePercentile(values, 0.5);
            
            assertThat(p50).isEqualTo(42.0);
        }
    }

    @Nested
    @DisplayName("Rolling Average")
    class RollingAverageTests {

        @Test
        @DisplayName("calculateRollingAverage with valid values returns correct averages")
        void calculateRollingAverage_validValues_returnsCorrect() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
            
            List<Double> rolling = AdvancedAnalyticsFeatures.calculateRollingAverage(values, 3);
            
            assertThat(rolling).hasSize(5);
            assertThat(rolling.get(0)).isEqualTo(1.0);
            assertThat(rolling.get(1)).isEqualTo(1.5);
            assertThat(rolling.get(2)).isEqualTo(2.0);
            assertThat(rolling.get(3)).isEqualTo(3.0);
            assertThat(rolling.get(4)).isEqualTo(4.0);
        }

        @Test
        @DisplayName("calculateRollingAverage with null values returns empty list")
        void calculateRollingAverage_nullValues_returnsEmpty() {
            List<Double> rolling = AdvancedAnalyticsFeatures.calculateRollingAverage(null, 3);
            assertThat(rolling).isEmpty();
        }

        @Test
        @DisplayName("calculateRollingAverage with empty values returns empty list")
        void calculateRollingAverage_emptyValues_returnsEmpty() {
            List<Double> rolling = AdvancedAnalyticsFeatures.calculateRollingAverage(List.of(), 3);
            assertThat(rolling).isEmpty();
        }

        @Test
        @DisplayName("calculateRollingAverage with invalid window size throws exception")
        void calculateRollingAverage_invalidWindow_throws() {
            List<Double> values = List.of(1.0, 2.0, 3.0);
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateRollingAverage(values, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateRollingAverage(values, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Histogram Generation")
    class HistogramTests {

        @Test
        @DisplayName("generateHistogram with valid values returns correct histogram")
        void generateHistogram_validValues_returnsCorrect() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);
            
            Map<String, Integer> histogram = AdvancedAnalyticsFeatures.generateHistogram(values, 5);
            
            assertThat(histogram).hasSize(5);
            assertThat(histogram.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
        }

        @Test
        @DisplayName("generateHistogram with null values returns empty map")
        void generateHistogram_nullValues_returnsEmpty() {
            Map<String, Integer> histogram = AdvancedAnalyticsFeatures.generateHistogram(null, 5);
            assertThat(histogram).isEmpty();
        }

        @Test
        @DisplayName("generateHistogram with empty values returns empty map")
        void generateHistogram_emptyValues_returnsEmpty() {
            Map<String, Integer> histogram = AdvancedAnalyticsFeatures.generateHistogram(List.of(), 5);
            assertThat(histogram).isEmpty();
        }

        @Test
        @DisplayName("generateHistogram with invalid bin count throws exception")
        void generateHistogram_invalidBinCount_throws() {
            List<Double> values = List.of(1.0, 2.0, 3.0);
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.generateHistogram(values, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Trend Calculation")
    class TrendTests {

        @Test
        @DisplayName("calculateTrend with increasing values returns positive slope")
        void calculateTrend_increasing_returnsPositive() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
            
            double trend = AdvancedAnalyticsFeatures.calculateTrend(values);
            
            assertThat(trend).isPositive();
        }

        @Test
        @DisplayName("calculateTrend with decreasing values returns negative slope")
        void calculateTrend_decreasing_returnsNegative() {
            List<Double> values = List.of(5.0, 4.0, 3.0, 2.0, 1.0);
            
            double trend = AdvancedAnalyticsFeatures.calculateTrend(values);
            
            assertThat(trend).isNegative();
        }

        @Test
        @DisplayName("calculateTrend with constant values returns zero slope")
        void calculateTrend_constant_returnsZero() {
            List<Double> values = List.of(5.0, 5.0, 5.0, 5.0);
            
            double trend = AdvancedAnalyticsFeatures.calculateTrend(values);
            
            assertThat(trend).isEqualTo(0.0);
        }

        @Test
        @DisplayName("calculateTrend with null values returns zero")
        void calculateTrend_null_returnsZero() {
            double trend = AdvancedAnalyticsFeatures.calculateTrend(null);
            assertThat(trend).isEqualTo(0.0);
        }

        @Test
        @DisplayName("calculateTrend with single value returns zero")
        void calculateTrend_singleValue_returnsZero() {
            double trend = AdvancedAnalyticsFeatures.calculateTrend(List.of(42.0));
            assertThat(trend).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Time Series Aggregation")
    class TimeSeriesAggregationTests {

        @Test
        @DisplayName("aggregateTimeSeries with valid data returns aggregated results")
        void aggregateTimeSeries_validData_returnsAggregated() {
            List<Map<String, Object>> data = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Map<String, Object> point = new HashMap<>();
                point.put("timestamp", (long) i * 1000);
                point.put("value", (double) i);
                data.add(point);
            }
            
            List<Map<String, Object>> aggregated = AdvancedAnalyticsFeatures.aggregateTimeSeries(data, 3000);
            
            assertThat(aggregated).isNotEmpty();
            assertThat(aggregated.get(0)).containsKeys("timestamp", "count", "sum", "avg", "min", "max");
        }

        @Test
        @DisplayName("aggregateTimeSeries with null data returns empty list")
        void aggregateTimeSeries_null_returnsEmpty() {
            List<Map<String, Object>> aggregated = AdvancedAnalyticsFeatures.aggregateTimeSeries(null, 3000);
            assertThat(aggregated).isEmpty();
        }

        @Test
        @DisplayName("aggregateTimeSeries with empty data returns empty list")
        void aggregateTimeSeries_empty_returnsEmpty() {
            List<Map<String, Object>> aggregated = AdvancedAnalyticsFeatures.aggregateTimeSeries(List.of(), 3000);
            assertThat(aggregated).isEmpty();
        }
    }

    @Nested
    @DisplayName("Moving Standard Deviation")
    class MovingStdDevTests {

        @Test
        @DisplayName("calculateMovingStdDev with valid values returns correct std devs")
        void calculateMovingStdDev_validValues_returnsCorrect() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
            
            List<Double> stdDevs = AdvancedAnalyticsFeatures.calculateMovingStdDev(values, 3);
            
            assertThat(stdDevs).hasSize(5);
            assertThat(stdDevs).allMatch(v -> v >= 0);
        }

        @Test
        @DisplayName("calculateMovingStdDev with null values returns empty list")
        void calculateMovingStdDev_null_returnsEmpty() {
            List<Double> stdDevs = AdvancedAnalyticsFeatures.calculateMovingStdDev(null, 3);
            assertThat(stdDevs).isEmpty();
        }

        @Test
        @DisplayName("calculateMovingStdDev with empty values returns empty list")
        void calculateMovingStdDev_empty_returnsEmpty() {
            List<Double> stdDevs = AdvancedAnalyticsFeatures.calculateMovingStdDev(List.of(), 3);
            assertThat(stdDevs).isEmpty();
        }

        @Test
        @DisplayName("calculateMovingStdDev with invalid window size throws exception")
        void calculateMovingStdDev_invalidWindow_throws() {
            List<Double> values = List.of(1.0, 2.0, 3.0);
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateMovingStdDev(values, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Exponential Moving Average")
    class EmaTests {

        @Test
        @DisplayName("calculateExponentialMovingAverage with valid values returns correct EMA")
        void calculateEma_validValues_returnsCorrect() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
            
            List<Double> ema = AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(values, 0.5);
            
            assertThat(ema).hasSize(5);
            assertThat(ema.get(0)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("calculateExponentialMovingAverage with null values returns empty list")
        void calculateEma_null_returnsEmpty() {
            List<Double> ema = AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(null, 0.5);
            assertThat(ema).isEmpty();
        }

        @Test
        @DisplayName("calculateExponentialMovingAverage with empty values returns empty list")
        void calculateEma_empty_returnsEmpty() {
            List<Double> ema = AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(List.of(), 0.5);
            assertThat(ema).isEmpty();
        }

        @Test
        @DisplayName("calculateExponentialMovingAverage with invalid alpha throws exception")
        void calculateEma_invalidAlpha_throws() {
            List<Double> values = List.of(1.0, 2.0, 3.0);
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(values, -0.1))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> AdvancedAnalyticsFeatures.calculateExponentialMovingAverage(values, 1.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Z-Score Calculation")
    class ZScoreTests {

        @Test
        @DisplayName("calculateZScores with valid values returns correct z-scores")
        void calculateZScores_validValues_returnsCorrect() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
            
            List<Double> zScores = AdvancedAnalyticsFeatures.calculateZScores(values);
            
            assertThat(zScores).hasSize(5);
            // Mean should be 3.0, std dev ~1.58
            // z-scores should be centered around 0
            double mean = zScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            assertThat(mean).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("calculateZScores with null values returns empty list")
        void calculateZScores_null_returnsEmpty() {
            List<Double> zScores = AdvancedAnalyticsFeatures.calculateZScores(null);
            assertThat(zScores).isEmpty();
        }

        @Test
        @DisplayName("calculateZScores with empty values returns empty list")
        void calculateZScores_empty_returnsEmpty() {
            List<Double> zScores = AdvancedAnalyticsFeatures.calculateZScores(List.of());
            assertThat(zScores).isEmpty();
        }

        @Test
        @DisplayName("calculateZScores with constant values returns all zeros")
        void calculateZScores_constant_returnsZeros() {
            List<Double> values = List.of(5.0, 5.0, 5.0, 5.0);
            
            List<Double> zScores = AdvancedAnalyticsFeatures.calculateZScores(values);
            
            assertThat(zScores).hasSize(4);
            assertThat(zScores).allMatch(v -> v == 0.0);
        }
    }
}
