/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.forecasting;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Naive {@link ForecastingEngine} that predicts future values by projecting the
 * last observed value forward with a fixed 1 % per-step growth rate.
 *
 * <p>This is the simplest possible baseline. It is suitable for smoke-testing and
 * environments where no historical data exist for a more sophisticated model.
 * Replace with {@link LinearTrendForecastingEngine} or an ARIMA-based engine
 * for production workloads.
 *
 * <h3>Algorithm</h3>
 * <pre>
 *   predicted[i] = last_value * (1 + 0.01 * i)   for i = 1..horizonSteps
 *   timestamp[i] = last_timestamp + i * stepSeconds
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Naive last-value-carry-forward forecasting baseline
 * @doc.layer product
 * @doc.pattern Strategy, NullObject
 */
public final class NaiveForecastingEngine implements ForecastingEngine {

    /** Default number of future steps to predict. */
    public static final int DEFAULT_HORIZON = 5;

    /** Default time delta between consecutive predictions (seconds). */
    public static final long DEFAULT_STEP_SECONDS = 3600L;

    private final int horizonSteps;
    private final long stepSeconds;

    /**
     * Create an engine using the default horizon ({@value #DEFAULT_HORIZON} steps)
     * and step size ({@value #DEFAULT_STEP_SECONDS} s).
     */
    public NaiveForecastingEngine() {
        this(DEFAULT_HORIZON, DEFAULT_STEP_SECONDS);
    }

    /**
     * Create an engine with a custom horizon and step size.
     *
     * @param horizonSteps number of future data points to predict; must be ≥ 1
     * @param stepSeconds  seconds between consecutive predictions; must be ≥ 1
     */
    public NaiveForecastingEngine(int horizonSteps, long stepSeconds) {
        if (horizonSteps < 1) throw new IllegalArgumentException("horizonSteps must be >= 1");
        if (stepSeconds < 1)  throw new IllegalArgumentException("stepSeconds must be >= 1");
        this.horizonSteps = horizonSteps;
        this.stepSeconds  = stepSeconds;
    }

    @Override
    public Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data) {
        if (data.points().isEmpty()) {
            return Promise.of(new AepEngine.Forecast(
                data.metric(), List.of(), 0.5, Map.of("algorithm", algorithmName())));
        }

        AepEngine.DataPoint last = data.points().get(data.points().size() - 1);
        List<AepEngine.DataPoint> predictions = new ArrayList<>(horizonSteps);
        for (int i = 1; i <= horizonSteps; i++) {
            predictions.add(new AepEngine.DataPoint(
                last.timestamp().plusSeconds((long) i * stepSeconds),
                last.value() * (1.0 + 0.01 * i)
            ));
        }

        return Promise.of(new AepEngine.Forecast(
            data.metric(),
            predictions,
            0.75,
            Map.of("algorithm", algorithmName(), "horizon", horizonSteps)
        ));
    }

    @Override
    public String algorithmName() {
        return "naive";
    }
}
