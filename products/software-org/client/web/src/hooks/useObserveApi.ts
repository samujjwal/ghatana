/**
 * Observe API Hooks
 *
 * React Query hooks for Observe API endpoints (Metrics, Reports, ML Observatory).
 * Covers Observe journeys V1-V3 from SOFTWARE_ORG_OBSERVE_IMPLEMENTATION_PLAN.md.
 *
 * @doc.type hooks
 * @doc.purpose Observe API data fetching and mutations
 * @doc.layer product
 * @doc.pattern React Query
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/services/api';

// =============================================================================
// Types
// =============================================================================

export interface MetricDataPoint {
    timestamp: string;
    value: number;
}

export interface MetricResponse {
    id: string;
    tenantId: string;
    name: string;
    description: string;
    category: string;
    value: string;
    target: string;
    trend: number;
    status: 'on-track' | 'at-risk' | 'off-track';
    unit: string;
    timeSeries: MetricDataPoint[];
    relatedIncidents: string[];
    relatedDeployments: string[];
    createdAt: string;
    updatedAt: string;
}

export interface ReportGenerateBody {
    tenantId: string;
    type: 'reliability' | 'change-management' | 'incident-summary';
    scope: string;
    startDate: string;
    endDate: string;
}

export interface ReportResponse {
    id: string;
    tenantId: string;
    name: string;
    type: 'reliability' | 'change-management' | 'incident-summary';
    scope: string;
    period: {
        start: string;
        end: string;
    };
    metrics: Record<string, unknown>;
    charts: Array<{
        type: string;
        data: unknown;
    }>;
    summary: string;
    createdAt: string;
}

export interface MLModelResponse {
    id: string;
    tenantId: string;
    name: string;
    version: string;
    status: 'healthy' | 'degraded' | 'failed';
    serviceId: string;
    lastDeployedAt: string;
    metrics: {
        accuracy?: number;
        precision?: number;
        recall?: number;
        f1Score?: number;
        drift?: number;
        latencyP50?: number;
        latencyP99?: number;
    };
    timeSeries: MetricDataPoint[];
    relatedIncidents: string[];
    createdAt: string;
    updatedAt: string;
}

// =============================================================================
// Metrics Hooks
// =============================================================================

/**
 * Fetch metrics for a tenant
 */
export function useMetrics(tenantId: string, category?: string, status?: string) {
    return useQuery({
        queryKey: ['observe', 'metrics', tenantId, category, status],
        queryFn: async () => {
            const params = new URLSearchParams({ tenantId });
            if (category) params.append('category', category);
            if (status) params.append('status', status);
            
            const response = await apiClient.get(`/observe/metrics?${params}`);
            return response.data as { data: MetricResponse[]; pagination: { page: number; pageSize: number; total: number } };
        },
    });
}

/**
 * Fetch a single metric by ID
 */
export function useMetric(metricId: string) {
    return useQuery({
        queryKey: ['observe', 'metrics', metricId],
        queryFn: async () => {
            const response = await apiClient.get(`/observe/metrics/${metricId}`);
            return response.data.data as MetricResponse;
        },
        enabled: !!metricId,
    });
}

// =============================================================================
// Reports Hooks
// =============================================================================

/**
 * Generate a new report
 */
export function useGenerateReport() {
    const queryClient = useQueryClient();
    
    return useMutation({
        mutationFn: async (body: ReportGenerateBody) => {
            const response = await apiClient.post('/observe/reports/generate', body);
            return response.data.data as ReportResponse;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['observe', 'reports'] });
        },
    });
}

/**
 * Fetch reports for a tenant
 */
export function useReports(tenantId: string) {
    return useQuery({
        queryKey: ['observe', 'reports', tenantId],
        queryFn: async () => {
            const response = await apiClient.get(`/observe/reports?tenantId=${tenantId}`);
            return response.data as { data: ReportResponse[]; pagination: { page: number; pageSize: number; total: number } };
        },
    });
}

// =============================================================================
// ML Observatory Hooks
// =============================================================================

/**
 * Fetch ML models for a tenant
 */
export function useMLModels(tenantId: string, status?: string) {
    return useQuery({
        queryKey: ['observe', 'ml', 'models', tenantId, status],
        queryFn: async () => {
            const params = new URLSearchParams({ tenantId });
            if (status) params.append('status', status);
            
            const response = await apiClient.get(`/observe/ml/models?${params}`);
            return response.data as { data: MLModelResponse[]; pagination: { page: number; pageSize: number; total: number } };
        },
    });
}

/**
 * Fetch a single ML model by ID
 */
export function useMLModel(modelId: string) {
    return useQuery({
        queryKey: ['observe', 'ml', 'models', modelId],
        queryFn: async () => {
            const response = await apiClient.get(`/observe/ml/models/${modelId}`);
            return response.data.data as MLModelResponse;
        },
        enabled: !!modelId,
    });
}
