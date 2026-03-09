import { apiClient } from './index';

/**
 * Department data access layer (Days 1-3).
 *
 * <p><b>Purpose</b><br>
 * API methods for fetching department metadata, KPIs, events, and running playbooks.
 * Supports multi-tenancy with department isolation.
 *
 * <p><b>Endpoints</b><br>
 * - GET /departments: Department list
 * - GET /departments/:id: Department details
 * - GET /departments/:id/kpis: Department KPIs
 * - GET /departments/:id/events: Department events
 * - POST /departments/:id/playbooks/:playbookId: Run playbook
 *
 * @doc.type service
 * @doc.purpose Department API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export interface Department {
    id: string;
    name: string;
    description?: string;
    teams: number;
    deployments: number;
    health: 'healthy' | 'degraded' | 'unhealthy';
    mttr: number;
    leadTime: number;
    deploymentFrequency: number;
}

export interface DepartmentDetail extends Department {
    createdAt: string;
    updatedAt: string;
    owner: string;
    slack?: string;
    services: string[];
}

export interface DepartmentKpi {
    departmentId: string;
    timestamp: string;
    deploymentFrequency: number;
    leadTime: number;
    mttr: number;
    changeFailureRate: number;
    reliability: number;
}

export interface DepartmentEvent {
    id: string;
    departmentId: string;
    type: string;
    timestamp: string;
    source: string;
    description: string;
    severity: 'low' | 'medium' | 'high' | 'critical';
}

export interface PlaybookExecution {
    playbookId: string;
    departmentId: string;
    executionId: string;
    status: 'running' | 'completed' | 'failed';
    startTime: string;
    endTime?: string;
    results?: Record<string, any>;
    error?: string;
}

export const departmentApi = {
    /**
     * Get list of departments
     */
    async getDepartments(params?: { tenantId?: string; limit?: number; offset?: number }) {
        const response = await apiClient.get<Department[]>('/departments', { params });
        return response.data;
    },

    /**
     * Get department details
     */
    async getDepartment(departmentId: string) {
        const response = await apiClient.get<DepartmentDetail>(`/departments/${departmentId}`);
        return response.data;
    },

    /**
     * Get department KPIs
     */
    async getDepartmentKpis(departmentId: string, params?: { timeRange?: string }) {
        const response = await apiClient.get<DepartmentKpi[]>(
            `/departments/${departmentId}/kpis`,
            { params }
        );
        return response.data;
    },

    /**
     * Get department events
     */
    async getDepartmentEvents(departmentId: string, params?: { limit?: number; offset?: number }) {
        const response = await apiClient.get<DepartmentEvent[]>(
            `/departments/${departmentId}/events`,
            { params }
        );
        return response.data;
    },

    /**
     * Run playbook on department
     */
    async runPlaybook(
        departmentId: string,
        playbookId: string,
        payload?: Record<string, any>
    ) {
        const response = await apiClient.post<PlaybookExecution>(
            `/departments/${departmentId}/playbooks/${playbookId}`,
            payload || {}
        );
        return response.data;
    },
};
