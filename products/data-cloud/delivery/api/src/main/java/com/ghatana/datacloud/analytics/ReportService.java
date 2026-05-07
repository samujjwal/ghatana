/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * ReportService interface.
 * Async API for report operations.
 *
 * @doc.type class
 * @doc.purpose Service interface for report operations
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ReportService {

    /**
     * Generate a new report from template.
     *
     * @param tenantId target tenant ID
     * @param template report specification
     * @return Promise of generated report (async)
     */
    Promise<Report> generateReport(String tenantId, ReportTemplate template);

    /**
     * Get a single report by ID (tenant-scoped).
     *
     * @param tenantId tenant ID
     * @param reportId report ID
     * @return Promise of report metadata
     */
    Promise<Report> getReport(String tenantId, String reportId);

    /**
     * List all reports for tenant.
     *
     * @param tenantId tenant ID
     * @return Promise of report list
     */
    Promise<List<Report>> listReports(String tenantId);

    /**
     * Update report settings.
     *
     * @param tenantId tenant ID
     * @param reportId report ID
     * @param updates map of field updates
     * @return Promise of updated report
     */
    Promise<Report> updateReport(String tenantId, String reportId, Map<String, String> updates);

    /**
     * Delete a report.
     *
     * @param tenantId tenant ID
     * @param reportId report ID
     * @return Promise of void (idempotent)
     */
    Promise<Void> deleteReport(String tenantId, String reportId);

    /**
     * Download report binary.
     *
     * @param tenantId tenant ID
     * @param reportId report ID
     * @param format output format (CSV, PDF, XLSX)
     * @return Promise of byte array
     */
    Promise<byte[]> downloadReport(String tenantId, String reportId, String format);
}
