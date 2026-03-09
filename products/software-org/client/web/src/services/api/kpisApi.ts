import { apiClient } from './index';

/**
 * KPI data access layer.
 *
 * <p><b>Purpose</b><br>
 * API methods for fetching organization-wide KPIs and metrics.
 * Provides typed responses matching backend DTO structure.
 *
 * <p><b>Endpoints</b><br>
 * - GET /kpis: Organization KPI summary
 * - GET /kpis/trends: KPI trends over time
 * - GET /kpis/departments: Per-department KPI breakdown
 *
 * @doc.type service
 * @doc.purpose KPI API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export interface KpiResponse {
    id: string;
    name: string;
    value: number;
    unit: string;
    trend: number; // percentage change
    target?: number;
    lastUpdated: string;
}

export interface KpiTrendResponse {
    timestamp: string;
    value: number;
}

export interface DepartmentKpiResponse {
    departmentId: string;
    departmentName: string;
    kpis: KpiResponse[];
}

// Resolve env once for this module to determine mock mode
// Note: Vite requires static access to import.meta.env properties for SSR compatibility
const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true' || import.meta.env.VITE_MOCK_API === 'true';

// Simple mock payload for getOrgKpis when running in mock mode. This avoids
// any network calls while still exercising the UI.
const mockOrgKpis: KpiResponse[] = [
    {
        id: 'deployments',
        name: 'Deployments',
        value: 156,
        unit: '/week',
        trend: 23,
        target: 150,
        lastUpdated: new Date().toISOString(),
    },
    {
        id: 'change-failure-rate',
        name: 'Change Failure Rate',
        value: 3.2,
        unit: '%',
        trend: -12,
        target: 5,
        lastUpdated: new Date().toISOString(),
    },
    {
        id: 'lead-time',
        name: 'Lead Time',
        value: 3.2,
        unit: 'h',
        trend: -45,
        target: 4,
        lastUpdated: new Date().toISOString(),
    },
    {
        id: 'mttr',
        name: 'MTTR',
        value: 12,
        unit: 'm',
        trend: -67,
        target: 15,
        lastUpdated: new Date().toISOString(),
    },
    {
        id: 'security-issues',
        name: 'Security Issues',
        value: 0,
        unit: 'critical',
        trend: 0,
        lastUpdated: new Date().toISOString(),
    },
    {
        id: 'cost-savings',
        name: 'Cost Savings',
        value: 2400,
        unit: 'USD/mo',
        trend: 30,
        lastUpdated: new Date().toISOString(),
    },
];

export const kpisApi = {
    /**
     * Get organization-wide KPI summary
     */
    async getOrgKpis(timeRange: string = '7d') {
        if (USE_MOCKS) {
            return mockOrgKpis;
        }

        const response = await apiClient.get<KpiResponse[]>('/kpis', {
            params: { timeRange },
        });
        return response.data;
    },

    /**
     * Get KPI trends over specified time range
     */
    async getKpiTrends(kpiId: string, timeRange: string = '7d') {
        const response = await apiClient.get<KpiTrendResponse[]>(`/kpis/${kpiId}/trends`, {
            params: { timeRange },
        });
        return response.data;
    },

    /**
     * Get KPI breakdown by department
     */
    async getDepartmentKpis(timeRange: string = '7d') {
        const response = await apiClient.get<DepartmentKpiResponse[]>('/kpis/departments', {
            params: { timeRange },
        });
        return response.data;
    },

    /**
     * Get AI-generated KPI narratives and insights
     */
    async getKpiNarratives(timeRange: string = '7d') {
        const response = await apiClient.get<{ insight: string; confidence: number }[]>('/kpis/narratives', {
            params: { timeRange },
        });
        return response.data;
    },
};
