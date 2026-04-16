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
 * Backtests a candidate forecasting engine against a baseline forecasting engine
 * on a holdout slice of recent observations.
 *
 * @doc.type class
 * @doc.purpose Compare forecasting strategies on holdout RMSE before rollout decisions
 * @doc.layer product
 * @doc.pattern Evaluator
 */
public final class ForecastingModelComparator {

    private final ForecastingEngine baselineEngine;
    private final ForecastingEngine candidateEngine;
    private final int holdoutPoints;

    public ForecastingModelComparator(ForecastingEngine baselineEngine,
                                      ForecastingEngine candidateEngine,
                                      int holdoutPoints) {
        this.baselineEngine = Objects.requireNonNull(baselineEngine, "baselineEngine");
        this.candidateEngine = Objects.requireNonNull(candidateEngine, "candidateEngine");
        if (holdoutPoints < 1) {
            throw new IllegalArgumentException("holdoutPoints must be >= 1");
        }
        this.holdoutPoints = holdoutPoints;
    }

    public Promise<ComparisonResult> compare(String tenantId, AepEngine.TimeSeriesData data) {
        List<AepEngine.DataPoint> points = data.points();
        if (points.size() <= holdoutPoints + 1) {
            return Promise.of(new ComparisonResult(
                baselineEngine.algorithmName(),
                candidateEngine.algorithmName(),
                Double.NaN,
                Double.NaN,
                "insufficient-history"
            ));
        }

        List<AepEngine.DataPoint> trainingPoints = points.subList(0, points.size() - holdoutPoints);
        List<AepEngine.DataPoint> holdout = points.subList(points.size() - holdoutPoints, points.size());
        AepEngine.TimeSeriesData trainingData = new AepEngine.TimeSeriesData(data.metric(), trainingPoints);

        return Promises.toList(
                baselineEngine.forecast(tenantId, trainingData),
                candidateEngine.forecast(tenantId, trainingData)
            )
            .map(results -> {
                AepEngine.Forecast baseline = (AepEngine.Forecast) results.get(0);
                AepEngine.Forecast candidate = (AepEngine.Forecast) results.get(1);
                double baselineRmse = rmse(baseline.predictions(), holdout);
                double candidateRmse = rmse(candidate.predictions(), holdout);
                return new ComparisonResult(
                    baselineEngine.algorithmName(),
                    candidateEngine.algorithmName(),
                    baselineRmse,
                    candidateRmse,
                    candidateRmse < baselineRmse ? candidateEngine.algorithmName() : baselineEngine.algorithmName()
                );
            });
    }

    private static double rmse(List<AepEngine.DataPoint> predicted, List<AepEngine.DataPoint> actual) {
        int count = Math.min(predicted.size(), actual.size());
        if (count == 0) {
            return Double.NaN;
        }

        double sum = 0.0;
        for (int index = 0; index < count; index++) {
            double delta = predicted.get(index).value() - actual.get(index).value();
            sum += delta * delta;
        }
        return Math.sqrt(sum / count);
    }

    public record ComparisonResult(
        String baselineAlgorithm,
        String candidateAlgorithm,
        double baselineRmse,
        double candidateRmse,
        String winner
    ) {
        public Map<String, Object> asMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("baselineAlgorithm", baselineAlgorithm);
            metadata.put("candidateAlgorithm", candidateAlgorithm);
            metadata.put("baselineRmse", baselineRmse);
            metadata.put("candidateRmse", candidateRmse);
            metadata.put("winner", winner);
            return metadata;
        }
    }
}