/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.forecasting;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;

/**
 * Strategy interface for time-series forecasting.
 *
 * <p>Implementations provide pluggable forecasting algorithms ranging from
 * naive baselines to statistical models (ARIMA, Exponential Smoothing, etc.).
 * All implementations MUST be stateless with respect to tenants — any state
 * required for a tenant-specific model should be keyed by {@code tenantId}.
 *
 * <h3>Threading</h3>
 * <p>Implementations are called on the ActiveJ Eventloop thread.
 * CPU-heavy algorithms MUST wrap their logic in {@code Promise.ofBlocking}
 * to avoid blocking the event loop.
 *
 * @doc.type interface
 * @doc.purpose Pluggable time-series forecasting strategy
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface ForecastingEngine {

    /**
     * Generate a forecast from the supplied time-series data.
     *
     * @param tenantId the tenant requesting the forecast; never {@code null}
     * @param data     the historical data points to forecast from; never {@code null}
     * @return a Promise that resolves to a {@link AepEngine.Forecast}, never {@code null}
     */
    Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data);

    /**
     * A human-readable name identifying this forecasting algorithm.
     *
     * @return algorithm name, e.g. {@code "naive"}, {@code "linear-trend"}, {@code "arima"}
     */
    default String algorithmName() {
        return getClass().getSimpleName();
    }
}
