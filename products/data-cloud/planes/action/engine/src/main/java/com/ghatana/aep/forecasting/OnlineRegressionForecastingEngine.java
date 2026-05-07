/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.forecasting;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Tenant-scoped forecasting engine that learns a linear model via online
 * gradient descent and warm-starts future forecasts from the last trained state.
 *
 * @doc.type class
 * @doc.purpose Learned forecasting strategy that incrementally trains a tenant-scoped regression model
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class OnlineRegressionForecastingEngine implements ForecastingEngine {

    public static final int DEFAULT_HORIZON = 5;
    public static final long DEFAULT_STEP_SECONDS = 3600L;
    public static final int DEFAULT_EPOCHS = 250;
    public static final double DEFAULT_LEARNING_RATE = 0.05;

    private final int horizonSteps;
    private final long stepSeconds;
    private final int epochs;
    private final double learningRate;
    private final Executor executor;
    private final ConcurrentMap<ModelKey, ModelState> modelStates;

    public OnlineRegressionForecastingEngine() {
        this(
            DEFAULT_HORIZON,
            DEFAULT_STEP_SECONDS,
            DEFAULT_EPOCHS,
            DEFAULT_LEARNING_RATE,
            createExecutor(),
            new ConcurrentHashMap<>()
        );
    }

    OnlineRegressionForecastingEngine(int horizonSteps,
                                      long stepSeconds,
                                      int epochs,
                                      double learningRate,
                                      Executor executor) {
        this(horizonSteps, stepSeconds, epochs, learningRate, executor, new ConcurrentHashMap<>());
    }

    private OnlineRegressionForecastingEngine(int horizonSteps,
                                              long stepSeconds,
                                              int epochs,
                                              double learningRate,
                                              Executor executor,
                                              ConcurrentMap<ModelKey, ModelState> modelStates) {
        if (horizonSteps < 1) {
            throw new IllegalArgumentException("horizonSteps must be >= 1");
        }
        if (stepSeconds < 1) {
            throw new IllegalArgumentException("stepSeconds must be >= 1");
        }
        if (epochs < 1) {
            throw new IllegalArgumentException("epochs must be >= 1");
        }
        if (learningRate <= 0 || learningRate >= 1) {
            throw new IllegalArgumentException("learningRate must be in (0,1)");
        }
        this.horizonSteps = horizonSteps;
        this.stepSeconds = stepSeconds;
        this.epochs = epochs;
        this.learningRate = learningRate;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.modelStates = Objects.requireNonNull(modelStates, "modelStates");
    }

    @Override
    public Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data) {
        List<AepEngine.DataPoint> points = data.points();
        if (points.isEmpty()) {
            return Promise.of(new AepEngine.Forecast(
                data.metric(), List.of(), 0.5, Map.of("algorithm", algorithmName())));
        }
        if (points.size() < 2) {
            return new NaiveForecastingEngine(horizonSteps, stepSeconds).forecast(tenantId, data);
        }

        ModelKey modelKey = new ModelKey(tenantId, data.metric());
        return Promise.ofBlocking(executor, () -> trainAndForecast(modelKey, data));
    }

    @Override
    public String algorithmName() {
        return "online-regression";
    }

    private AepEngine.Forecast trainAndForecast(ModelKey modelKey, AepEngine.TimeSeriesData data) {
        List<AepEngine.DataPoint> points = data.points();
        double[] xs = normalizedIndexes(points.size());
        double[] ys = points.stream().mapToDouble(AepEngine.DataPoint::value).toArray();

        ModelState previous = modelStates.get(modelKey);
        boolean warmStarted = previous != null;
        double slope = warmStarted ? previous.slope() : initialSlope(ys);
        double intercept = warmStarted ? previous.intercept() : ys[0];

        for (int epoch = 0; epoch < epochs; epoch++) {
            for (int index = 0; index < xs.length; index++) {
                double prediction = intercept + slope * xs[index];
                double error = prediction - ys[index];
                intercept -= learningRate * 2.0 * error;
                slope -= learningRate * 2.0 * error * xs[index];
            }
        }

        double rmse = rmse(xs, ys, intercept, slope);
        modelStates.put(modelKey, new ModelState(intercept, slope, rmse, points.size()));

        Instant lastTimestamp = points.get(points.size() - 1).timestamp();
        double scale = Math.max(1.0, points.size() - 1.0);
        List<AepEngine.DataPoint> predictions = new ArrayList<>(horizonSteps);
        for (int step = 1; step <= horizonSteps; step++) {
            double futureIndex = (points.size() - 1.0 + step) / scale;
            predictions.add(new AepEngine.DataPoint(
                lastTimestamp.plusSeconds((long) step * stepSeconds),
                intercept + slope * futureIndex
            ));
        }

        double meanAbs = meanAbsolute(ys);
        double confidence = meanAbs == 0.0
            ? 0.5
            : Math.max(0.5, Math.min(0.99, 1.0 - (rmse / meanAbs)));

        return new AepEngine.Forecast(
            data.metric(),
            predictions,
            confidence,
            Map.of(
                "algorithm", algorithmName(),
                "epochs", epochs,
                "learningRate", learningRate,
                "slope", slope,
                "intercept", intercept,
                "rmse", rmse,
                "warmStarted", warmStarted,
                "observations", points.size()
            )
        );
    }

    private static Executor createExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "online-regression-forecasting");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static double[] normalizedIndexes(int size) {
        double[] values = new double[size];
        double denominator = Math.max(1.0, size - 1.0);
        for (int index = 0; index < size; index++) {
            values[index] = index / denominator;
        }
        return values;
    }

    private static double initialSlope(double[] ys) {
        if (ys.length < 2) {
            return 0.0;
        }
        return ys[ys.length - 1] - ys[0];
    }

    private static double rmse(double[] xs, double[] ys, double intercept, double slope) {
        double sum = 0.0;
        for (int index = 0; index < xs.length; index++) {
            double error = (intercept + slope * xs[index]) - ys[index];
            sum += error * error;
        }
        return Math.sqrt(sum / xs.length);
    }

    private static double meanAbsolute(double[] ys) {
        double sum = 0.0;
        for (double value : ys) {
            sum += Math.abs(value);
        }
        return sum / ys.length;
    }

    private record ModelKey(String tenantId, String metric) {
    }

    private record ModelState(double intercept, double slope, double rmse, int observations) {
    }
}