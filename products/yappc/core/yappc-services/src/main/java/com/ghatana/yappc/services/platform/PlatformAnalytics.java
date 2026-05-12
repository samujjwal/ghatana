/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Platform analytics data.
 *
 * @doc.type record
 * @doc.purpose Represents analytics data from platform services
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record PlatformAnalytics(
    List<AnalyticsMetric> metrics,
    Map<String, Object> summary,
    Instant startTime,
    Instant endTime
) {
    public record AnalyticsMetric(
        String metricName,
        double value,
        Map<String, String> tags,
        Instant timestamp
    ) {}

    public record AnalyticsQuery(
        String metricName,
        Instant startTime,
        Instant endTime,
        Map<String, String> filters,
        Map<String, String> groupBy
    ) {}
}
