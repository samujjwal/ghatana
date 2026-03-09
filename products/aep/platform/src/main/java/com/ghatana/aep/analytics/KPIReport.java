/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.util.List;
import java.util.Map;

/**
 * KPI report for AnalyticsEngine.
 *
 * <p>Encapsulates calculated KPI results including individual KPI entries
 * with their values and metadata.
 *
 * @doc.type record
 * @doc.purpose Analytics engine KPI report
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record KPIReport(
    String tenantId,
    List<KPIEntry> kpis,
    boolean success
) {
    /**
     * Creates an empty successful report.
     */
    public KPIReport(String tenantId) {
        this(tenantId, List.of(), true);
    }

    /**
     * A single KPI entry.
     */
    public record KPIEntry(
        String name,
        double value,
        String unit,
        Map<String, Object> metadata
    ) {}
}
