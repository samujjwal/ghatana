/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.util.List;

/**
 * Default implementation of {@link KPIAggregator}.
 * 
 * <p>Returns an empty KPI report. Production implementations should
 * aggregate actual metrics from monitoring systems.
 */
public final class DefaultKPIAggregator implements KPIAggregator {

    @Override
    public KPIReport calculateKPIs(String tenantId, AnalyticsEngine.TimeRange timeRange, List<String> kpiTypes) {
        return new KPIReport(tenantId);
    }
}
