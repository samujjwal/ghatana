/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.util.List;

/**
 * KPI aggregator interface for AnalyticsEngine.
 *
 * <p>Calculates and aggregates Key Performance Indicators for the analytics
 * engine facade. The full aggregation implementation resides in
 * {@code com.ghatana.aep.expertinterface.analytics.KPIAggregator}.
 *
 * @doc.type interface
 * @doc.purpose Analytics engine KPI calculation abstraction
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface KPIAggregator {

    /**
     * Calculates KPIs for a tenant within a time range.
     *
     * @param tenantId  the tenant identifier
     * @param timeRange the time range
     * @param kpiTypes  the types of KPIs to calculate
     * @return a KPI report
     */
    KPIReport calculateKPIs(String tenantId, AnalyticsEngine.TimeRange timeRange, List<String> kpiTypes);
}
