/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.util.List;

/**
 * Advanced time series forecaster interface for AnalyticsEngine.
 *
 * <p>Provides time series forecasting with support for seasonality,
 * trend decomposition, and multi-step-ahead prediction.
 *
 * @doc.type interface
 * @doc.purpose Time series forecasting abstraction
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface AdvancedTimeSeriesForecaster {

    /**
     * Forecasts future values based on historical data.
     *
     * @param data     the historical time series data
     * @param horizon  number of points to forecast
     * @return list of forecast points
     */
    default List<AnalyticsEngine.ForecastPoint> forecast(AnalyticsEngine.TimeSeriesData data, int horizon) {
        return List.of();
    }

    /**
     * Detects seasonality in the data.
     *
     * @param data the time series data
     * @return detected period in data points, or -1 if no seasonality found
     */
    default int detectSeasonality(AnalyticsEngine.TimeSeriesData data) {
        return -1;
    }
}
