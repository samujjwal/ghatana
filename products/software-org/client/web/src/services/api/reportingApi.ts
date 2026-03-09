/**
 * Reporting API client.
 */

/**
 * Report interface.
 */
export interface Report {
    id: string;
    title: string;
    description: string;
    generatedAt: Date;
    generatedBy: string;
    format: 'pdf' | 'csv' | 'json' | 'html';
    status: 'draft' | 'published' | 'archived';
    sections: string[];
    metrics: {
        pageCount?: number;
        dataPoints?: number;
        timeSpan?: string;
    };
}

/**
 * Report metrics interface.
 */
export interface ReportMetrics {
    departmentName: string;
    velocity: number;
    cycleTime: number;
    deploymentFrequency: number;
    coverage: number;
    passRate: number;
    trend: 'up' | 'down' | 'stable';
}

/**
 * Reporting API client.
 *
 * <p><b>Purpose</b><br>
 * Provides report retrieval, generation, and export operations.
 *
 * <p><b>Methods</b><br>
 * - getReports: Get all reports
 * - getReportById: Get report details
 * - generateReport: Generate new report
 * - exportReport: Export report in format
 * - getReportMetrics: Get metrics for report data
 *
 * @doc.type service
 * @doc.purpose Reporting API operations
 * @doc.layer product
 * @doc.pattern API Client
 */
export const reportingApi = {
    /**
     * Get all reports.
     *
     * @param tenantId - Tenant ID
     * @param filters - Optional filters (status, format)
     * @returns Promise resolving to reports array
     */
    async getReports(
        tenantId: string,
        filters?: {
            status?: string;
            format?: string;
        }
    ): Promise<Report[]> {
        try {
            const params = new URLSearchParams({ tenantId });
            if (filters?.status) params.append('status', filters.status);
            if (filters?.format) params.append('format', filters.format);

            const response = await fetch(`/api/v1/reports?${params.toString()}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[reportingApi.getReports] API unavailable:', error);
            return [];
        }
    },

    /**
     * Get report by ID.
     *
     * @param reportId - Report ID
     * @param tenantId - Tenant ID
     * @returns Promise resolving to report details
     */
    async getReportById(reportId: string, tenantId: string): Promise<Report | null> {
        try {
            const response = await fetch(`/api/v1/reports/${reportId}?tenantId=${tenantId}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[reportingApi.getReportById] API unavailable:', error);
            return null;
        }
    },

    /**
     * Generate new report.
     *
     * @param title - Report title
     * @param dateRange - Date range for report
     * @param filters - Report filters
     * @param tenantId - Tenant ID
     * @returns Promise resolving to generated report
     */
    async generateReport(
        title: string,
        dateRange: { start: Date; end: Date },
        filters: Record<string, unknown>,
        tenantId: string
    ): Promise<Report | null> {
        try {
            const response = await fetch('/api/v1/reports/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    title,
                    dateRange,
                    filters,
                    tenantId,
                }),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[reportingApi.generateReport] API unavailable:', error);
            return null;
        }
    },

    /**
     * Export report in specified format.
     *
     * @param reportId - Report ID
     * @param format - Export format
     * @param tenantId - Tenant ID
     * @returns Promise resolving to export URL or blob
     */
    async exportReport(
        reportId: string,
        format: 'pdf' | 'csv' | 'json' | 'html' | 'excel',
        tenantId: string
    ): Promise<Blob | null> {
        try {
            const response = await fetch(
                `/api/v1/reports/${reportId}/export?format=${format}&tenantId=${tenantId}`
            );
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.blob();
        } catch (error) {
            console.warn('[reportingApi.exportReport] API unavailable:', error);
            return null;
        }
    },

    /**
     * Get metrics for report data.
     *
     * @param dateRange - Date range for metrics
     * @param departments - Department IDs to include
     * @param tenantId - Tenant ID
     * @returns Promise resolving to metrics array
     */
    async getReportMetrics(
        dateRange: { start: Date; end: Date },
        departments: string[],
        tenantId: string
    ): Promise<ReportMetrics[]> {
        try {
            const response = await fetch('/api/v1/reports/metrics', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ dateRange, departments, tenantId }),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.warn('[reportingApi.getReportMetrics] API unavailable:', error);
            return [];
        }
    },

    /**
     * Delete report.
     *
     * @param reportId - Report ID
     * @returns Promise resolving when deleted
     */
    async deleteReport(reportId: string): Promise<void> {
        try {
            const response = await fetch(`/api/v1/reports/${reportId}`, {
                method: 'DELETE',
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
        } catch (error) {
            console.warn('[reportingApi.deleteReport] API unavailable:', error);
            throw error;
        }
    },
};

export default reportingApi;
