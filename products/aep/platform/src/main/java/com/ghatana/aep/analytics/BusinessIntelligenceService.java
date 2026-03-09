/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

/**
 * Business Intelligence service interface for AnalyticsEngine.
 *
 * <p>Provides BI capabilities for the analytics engine facade.
 * This is the analytics-local contract; the full BI implementation
 * resides in {@code com.ghatana.aep.expertinterface.analytics.BusinessIntelligenceService}.
 *
 * @doc.type interface
 * @doc.purpose Analytics engine BI abstraction
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface BusinessIntelligenceService {

    /**
     * Generates a business intelligence summary for the given tenant and time range.
     *
     * @param tenantId  the tenant identifier
     * @param timeRange the time range to analyze
     * @return a summary report
     */
    default BISummary generateSummary(String tenantId, AnalyticsEngine.TimeRange timeRange) {
        return new BISummary(tenantId, "No BI data available", java.util.Map.of());
    }

    /**
     * BI summary record.
     */
    record BISummary(String tenantId, String summary, java.util.Map<String, Object> metrics) {}
}
