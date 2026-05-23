package com.ghatana.digitalmarketing.application.reporting;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Service interface for reporting and dashboard workflows.
 *
 * @doc.type class
 * @doc.purpose Defines operations for generating reports and dashboard data (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ReportingService {

    /**
     * Generate a report.
     *
     * @param ctx     operation context
     * @param request report generation request
     * @return the generated report
     */
    Promise<Report> generateReport(DmOperationContext ctx, GenerateReportRequest request);

    /**
     * Fetch a report by ID.
     *
     * @param ctx      operation context
     * @param reportId report ID
     * @return the report
     */
    Promise<Report> getReport(DmOperationContext ctx, String reportId);

    /**
     * Get dashboard data.
     *
     * @param ctx          operation context
     * @param dashboardId dashboard ID
     * @return dashboard data
     */
    Promise<DashboardData> getDashboardData(DmOperationContext ctx, String dashboardId);

    /**
     * Refresh dashboard data.
     *
     * @param ctx          operation context
     * @param dashboardId dashboard ID
     * @return refreshed dashboard data
     */
    Promise<DashboardData> refreshDashboard(DmOperationContext ctx, String dashboardId);

    // ── Request types ─────────────────────────────────────────────────────────

    record GenerateReportRequest(
        String reportType,
        String startDate,
        String endDate,
        Map<String, String> filters
    ) {
        public GenerateReportRequest {
            // Validation logic
        }
    }

    record Report(
        String reportId,
        String reportType,
        String generatedAt,
        Map<String, Object> data,
        ReportStatus status
    ) {}

    record DashboardData(
        String dashboardId,
        String refreshedAt,
        Map<String, Object> metrics,
        Map<String, Object> charts
    ) {}

    enum ReportStatus {
        PENDING,
        GENERATING,
        COMPLETED,
        FAILED
    }
}
