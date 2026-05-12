/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

/**
 * Typed client for platform telemetry and analytics operations.
 * Handles communication with Data Cloud+AEP telemetry and analytics services.
 *
 * @doc.type interface
 * @doc.purpose Typed client for platform telemetry and analytics operations
 * @doc.layer product
 * @doc.pattern Client
 */
public interface PlatformTelemetryClient {

    /**
     * Records telemetry event (telemetry/analytics).
     *
     * @param event The telemetry event to record
     * @return true if successful, false otherwise
     */
    boolean recordTelemetry(PlatformTelemetry event);

    /**
     * Gets analytics data for a time range (telemetry/analytics).
     *
     * @param query The analytics query
     * @return PlatformAnalytics containing the analytics data
     */
    PlatformAnalytics getAnalytics(PlatformAnalytics.AnalyticsQuery query);
}
