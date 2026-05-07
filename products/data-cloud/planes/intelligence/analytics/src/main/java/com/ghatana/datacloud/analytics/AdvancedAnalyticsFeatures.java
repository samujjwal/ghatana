/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Advanced analytics features for data analysis.
 *
 * <p>Provides advanced analytical operations:
 * <ul>
 *   <li>Percentile calculations</li>
 *   <li>Rolling averages</li>
 *   <li>Window functions</li>
 *   <li>Time series aggregation</li>
 *   <li>Histogram generation</li>
 *   <li>Trend analysis</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Advanced analytics features for data analysis
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class AdvancedAnalyticsFeatures {

    /**
     * Calculates percentile values from a list of numeric values.
     *
     * @param values list of numeric values
     * @param percentile percentile to calculate (0.0 to 1.0)
     * @return percentile value
     */
    public static double calculatePercentile(List<Double> values, double percentile) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Values list cannot be null or empty");
        }
        if (percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile must be between 0.0 and 1.0");
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        
        return sorted.get(index);
    }

    /**
     * Calculates rolling average over a window.
     *
     * @param values list of numeric values
     * @param windowSize window size for rolling average
     * @return list of rolling averages
     */
    public static List<Double> calculateRollingAverage(List<Double> values, int windowSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }

        List<Double> rollingAverages = new ArrayList<>();
        
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - windowSize + 1);
            int end = i + 1;
            
            List<Double> window = values.subList(start, end);
            double avg = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            rollingAverages.add(avg);
        }
        
        return rollingAverages;
    }

    /**
     * Generates a histogram from numeric values.
     *
     * @param values list of numeric values
     * @param binCount number of bins
     * @return histogram map (bin range -> count)
     */
    public static Map<String, Integer> generateHistogram(List<Double> values, int binCount) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        if (binCount <= 0) {
            throw new IllegalArgumentException("Bin count must be positive");
        }

        double min = Collections.min(values);
        double max = Collections.max(values);
        double binWidth = (max - min) / binCount;

        Map<String, Integer> histogram = new HashMap<>();

        for (Double value : values) {
            int binIndex = (int) ((value - min) / binWidth);
            binIndex = Math.min(binIndex, binCount - 1);
            
            double binStart = min + binIndex * binWidth;
            double binEnd = binStart + binWidth;
            String binKey = String.format("%.2f-%.2f", binStart, binEnd);
            
            histogram.merge(binKey, 1, Integer::sum);
        }

        return histogram;
    }

    /**
     * Calculates trend (linear regression slope) for time series data.
     *
     * @param values list of numeric values in time order
     * @return trend slope (positive = increasing, negative = decreasing)
     */
    public static double calculateTrend(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }

        int n = values.size();
        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumX2 = 0.0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += i * i;
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0) {
            return 0.0;
        }

        return (n * sumXY - sumX * sumY) / denominator;
    }

    /**
     * Performs time series aggregation by time window.
     *
     * @param timeSeriesData list of time-value pairs
     * @param windowSizeMs window size in milliseconds
     * @return aggregated time series data
     */
    public static List<Map<String, Object>> aggregateTimeSeries(
            List<Map<String, Object>> timeSeriesData,
            long windowSizeMs) {
        if (timeSeriesData == null || timeSeriesData.isEmpty()) {
            return List.of();
        }

        // Sort by timestamp
        List<Map<String, Object>> sorted = timeSeriesData.stream()
                .sorted(Comparator.comparing(m -> (Long) m.get("timestamp")))
                .collect(Collectors.toList());

        List<Map<String, Object>> aggregated = new ArrayList<>();
        List<Double> currentWindowValues = new ArrayList<>();
        long currentWindowStart = (Long) sorted.get(0).get("timestamp");

        for (Map<String, Object> dataPoint : sorted) {
            long timestamp = (Long) dataPoint.get("timestamp");
            double value = ((Number) dataPoint.get("value")).doubleValue();

            if (timestamp - currentWindowStart >= windowSizeMs) {
                // Emit aggregation for current window
                if (!currentWindowValues.isEmpty()) {
                    Map<String, Object> agg = new HashMap<>();
                    agg.put("timestamp", currentWindowStart);
                    agg.put("count", currentWindowValues.size());
                    agg.put("sum", currentWindowValues.stream().mapToDouble(Double::doubleValue).sum());
                    agg.put("avg", currentWindowValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
                    agg.put("min", Collections.min(currentWindowValues));
                    agg.put("max", Collections.max(currentWindowValues));
                    aggregated.add(agg);
                }
                
                // Start new window
                currentWindowStart = timestamp;
                currentWindowValues = new ArrayList<>();
            }

            currentWindowValues.add(value);
        }

        // Emit final window
        if (!currentWindowValues.isEmpty()) {
            Map<String, Object> agg = new HashMap<>();
            agg.put("timestamp", currentWindowStart);
            agg.put("count", currentWindowValues.size());
            agg.put("sum", currentWindowValues.stream().mapToDouble(Double::doubleValue).sum());
            agg.put("avg", currentWindowValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
            agg.put("min", Collections.min(currentWindowValues));
            agg.put("max", Collections.max(currentWindowValues));
            aggregated.add(agg);
        }

        return aggregated;
    }

    /**
     * Calculates moving standard deviation.
     *
     * @param values list of numeric values
     * @param windowSize window size
     * @return list of moving standard deviations
     */
    public static List<Double> calculateMovingStdDev(List<Double> values, int windowSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }

        List<Double> stdDevs = new ArrayList<>();
        
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - windowSize + 1);
            int end = i + 1;
            
            List<Double> window = values.subList(start, end);
            double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = window.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .average()
                    .orElse(0.0);
            stdDevs.add(Math.sqrt(variance));
        }
        
        return stdDevs;
    }

    /**
     * Calculates exponential moving average.
     *
     * @param values list of numeric values
     * @param alpha smoothing factor (0.0 to 1.0)
     * @return list of exponential moving averages
     */
    public static List<Double> calculateExponentialMovingAverage(List<Double> values, double alpha) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha must be between 0.0 and 1.0");
        }

        List<Double> ema = new ArrayList<>();
        double previousEma = values.get(0);
        ema.add(previousEma);

        for (int i = 1; i < values.size(); i++) {
            double currentEma = alpha * values.get(i) + (1 - alpha) * previousEma;
            ema.add(currentEma);
            previousEma = currentEma;
        }

        return ema;
    }

    /**
     * Calculates z-scores for a list of values.
     *
     * @param values list of numeric values
     * @return list of z-scores
     */
    public static List<Double> calculateZScores(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) {
            return values.stream().map(v -> 0.0).collect(Collectors.toList());
        }

        return values.stream()
                .map(v -> (v - mean) / stdDev)
                .collect(Collectors.toList());
    }
}
