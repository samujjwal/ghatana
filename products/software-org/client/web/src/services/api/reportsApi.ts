import { apiClient } from './index';

/**
 * Reporting and Analytics API (Day 7).
 *
 * <p><b>Purpose</b><br>
 * API methods for generating reports, fetching metrics, and analyzing incidents.
 *
 * <p><b>Endpoints</b><br>
 * - GET /reports: Report templates and history
 * - POST /reports/generate: Generate new report
 * - GET /metrics: System metrics
 * - GET /incidents: Incident statistics
 *
 * @doc.type service
 * @doc.purpose Reporting and Analytics API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export interface MetricPoint {
    timestamp: string;
    value: number;
    label?: string;
}

export interface MetricResponse {
    name: string;
    unit: string;
    points: MetricPoint[];
    summary?: {
        min: number;
        max: number;
        avg: number;
        p99: number;
    };
}

export interface IncidentStat {
    totalIncidents: number;
    openIncidents: number;
    resolvedIncidents: number;
    avgMTTR: number; // minutes
    avgMTTD: number; // minutes
    severityDistribution: Record<string, number>;
}

export interface ReportTemplate {
    id: string;
    name: string;
    description: string;
    category: 'executive' | 'technical' | 'security' | 'operational';
}

export interface GeneratedReport {
    id: string;
    name: string;
    format: 'pdf' | 'markdown' | 'json';
    generatedAt: string;
    downloadUrl: string;
    fileSize: number; // bytes
}

export const reportsApi = {
    /**
     * Get available report templates
     */
    async getReportTemplates() {
        const response = await apiClient.get<ReportTemplate[]>('/reports/templates');
        return response.data;
    },

    /**
     * Generate a new report
     */
    async generateReport(params: {
        templateId: string;
        timeRange: string;
        department?: string;
        format: 'pdf' | 'markdown' | 'json';
    }) {
        const response = await apiClient.post<GeneratedReport>('/reports/generate', params);
        return response.data;
    },

    /**
     * Get report history
     */
    async getReportHistory(limit: number = 10) {
        const response = await apiClient.get<GeneratedReport[]>('/reports/history', {
            params: { limit },
        });
        return response.data;
    },

    /**
     * Get system metrics for time range
     */
    async getMetrics(params?: {
        timeRange?: string;
        metrics?: string[]; // e.g., ['throughput', 'latency', 'error_rate']
    }) {
        const response = await apiClient.get<MetricResponse[]>('/metrics', { params });
        return response.data;
    },

    /**
     * Get incident statistics
     */
    async getIncidentStats(params?: { timeRange?: string; department?: string }) {
        const response = await apiClient.get<IncidentStat>('/incidents/stats', { params });
        return response.data;
    },

    /**
     * Get incident timeline (for incident report)
     */
    async getIncidentTimeline(incidentId: string) {
        const response = await apiClient.get<Array<{
            timestamp: string;
            action: string;
            actor: string;
            details?: string;
        }>>(`/incidents/${incidentId}/timeline`);
        return response.data;
    },

    /**
     * Share report via link
     */
    async shareReport(reportId: string, expiresIn?: string) {
        const response = await apiClient.post<{ shareUrl: string }>(
            `/reports/${reportId}/share`,
            { expiresIn }
        );
        return response.data;
    },
};
