/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.forecasting;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Forecasting strategy that backtests the built-in forecasting engines against a
 * holdout window, then uses the best-scoring engine for the final prediction.
 *
 * @doc.type class
 * @doc.purpose Adaptive forecasting strategy that selects the best local model from recent history
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class AdaptiveForecastingEngine implements ForecastingEngine {

    private static final int DEFAULT_MIN_HOLDOUT_POINTS = 2;

    private final NaiveForecastingEngine naiveEngine;
    private final LinearTrendForecastingEngine linearEngine;
    private final StatisticalForecastingEngine statisticalEngine;
    private final OnlineRegressionForecastingEngine learnedEngine;
    private final int minHoldoutPoints;

    public AdaptiveForecastingEngine() {
        this(
            new NaiveForecastingEngine(),
            new LinearTrendForecastingEngine(),
            new StatisticalForecastingEngine(),
            new OnlineRegressionForecastingEngine(),
            DEFAULT_MIN_HOLDOUT_POINTS
        );
    }

    AdaptiveForecastingEngine(
            NaiveForecastingEngine naiveEngine,
            LinearTrendForecastingEngine linearEngine,
            StatisticalForecastingEngine statisticalEngine,
            OnlineRegressionForecastingEngine learnedEngine,
            int minHoldoutPoints) {
        this.naiveEngine = Objects.requireNonNull(naiveEngine, "naiveEngine");
        this.linearEngine = Objects.requireNonNull(linearEngine, "linearEngine");
        this.statisticalEngine = Objects.requireNonNull(statisticalEngine, "statisticalEngine");
        this.learnedEngine = Objects.requireNonNull(learnedEngine, "learnedEngine");
        if (minHoldoutPoints < 1) {
            throw new IllegalArgumentException("minHoldoutPoints must be >= 1");
        }
        this.minHoldoutPoints = minHoldoutPoints;
    }

    @Override
    public Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data) {
        List<AepEngine.DataPoint> points = data.points();
        if (points.size() < 4) {
            return linearEngine.forecast(tenantId, data)
                .map(forecast -> withMetadata(forecast, "insufficient-history"));
        }

        int holdoutSize = Math.max(minHoldoutPoints, Math.min(3, points.size() / 3));
        if (points.size() - holdoutSize < 2) {
            holdoutSize = Math.max(1, points.size() - 2);
        }

        List<AepEngine.DataPoint> trainingPoints = points.subList(0, points.size() - holdoutSize);
        List<AepEngine.DataPoint> holdoutPoints = points.subList(points.size() - holdoutSize, points.size());
        AepEngine.TimeSeriesData trainingSeries = new AepEngine.TimeSeriesData(data.metric(), trainingPoints);

        return Promises.toList(
                naiveEngine.forecast(tenantId, trainingSeries),
                linearEngine.forecast(tenantId, trainingSeries),
                statisticalEngine.forecast(tenantId, trainingSeries),
                learnedEngine.forecast(tenantId, trainingSeries)
            )
            .then(forecasts -> {
                AepEngine.Forecast naiveForecast = (AepEngine.Forecast) forecasts.get(0);
                AepEngine.Forecast linearForecast = (AepEngine.Forecast) forecasts.get(1);
                AepEngine.Forecast statisticalForecast = (AepEngine.Forecast) forecasts.get(2);
                AepEngine.Forecast learnedForecast = (AepEngine.Forecast) forecasts.get(3);
                Map<String, Double> errors = new LinkedHashMap<>();
                errors.put(naiveEngine.algorithmName(), rmse(naiveForecast.predictions(), holdoutPoints));
                errors.put(linearEngine.algorithmName(), rmse(linearForecast.predictions(), holdoutPoints));
                errors.put(statisticalEngine.algorithmName(), rmse(statisticalForecast.predictions(), holdoutPoints));
                errors.put(learnedEngine.algorithmName(), rmse(learnedForecast.predictions(), holdoutPoints));

                ForecastingEngine selected = selectBestEngine(errors);
                double selectedError = errors.get(selected.algorithmName());
                return selected.forecast(tenantId, data)
                    .map(forecast -> withMetadata(forecast, selected.algorithmName(), selectedError, errors));
            });
    }

    @Override
    public String algorithmName() {
        return "adaptive";
    }

    private ForecastingEngine selectBestEngine(Map<String, Double> errors) {
        String bestAlgorithm = errors.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(linearEngine.algorithmName());

        if (bestAlgorithm.equals(naiveEngine.algorithmName())) {
            return naiveEngine;
        }
        if (bestAlgorithm.equals(learnedEngine.algorithmName())) {
            return learnedEngine;
        }
        if (bestAlgorithm.equals(statisticalEngine.algorithmName())) {
            return statisticalEngine;
        }
        return linearEngine;
    }

    private static AepEngine.Forecast withMetadata(AepEngine.Forecast forecast, String selectionReason) {
        Map<String, Object> metadata = new LinkedHashMap<>(forecast.metadata());
        metadata.put("algorithm", "adaptive");
        metadata.put("selectedAlgorithm", selectionReason);
        return new AepEngine.Forecast(forecast.metric(), forecast.predictions(), forecast.confidence(), metadata);
    }

    private static AepEngine.Forecast withMetadata(
            AepEngine.Forecast forecast,
            String selectedAlgorithm,
            double selectedRmse,
            Map<String, Double> candidateErrors) {
        Map<String, Object> metadata = new LinkedHashMap<>(forecast.metadata());
        metadata.put("algorithm", "adaptive");
        metadata.put("selectedAlgorithm", selectedAlgorithm);
        metadata.put("selectedRmse", selectedRmse);
        metadata.put("candidateRmse", Map.copyOf(candidateErrors));
        return new AepEngine.Forecast(forecast.metric(), forecast.predictions(), forecast.confidence(), metadata);
    }

    private static double rmse(List<AepEngine.DataPoint> predicted, List<AepEngine.DataPoint> actual) {
        int count = Math.min(predicted.size(), actual.size());
        if (count == 0) {
            return Double.MAX_VALUE;
        }

        double sum = 0.0;
        for (int index = 0; index < count; index++) {
            double delta = predicted.get(index).value() - actual.get(index).value();
            sum += delta * delta;
        }
        return Math.sqrt(sum / count);
    }
}