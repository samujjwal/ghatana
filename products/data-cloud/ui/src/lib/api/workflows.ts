/**
 * Workflows API
 * 
 * API endpoints for workflow management.
 * 
 * @doc.type service
 * @doc.purpose Workflows API endpoints
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

import { apiClient, PaginatedResponse } from './client';

/**
 * Workflow node
 */
export interface WorkflowNode {
    id: string;
    type: string;
    label: string;
    position: { x: number; y: number };
    data: Record<string, unknown>;
}

/**
 * Workflow edge
 */
export interface WorkflowEdge {
    id: string;
    source: string;
    target: string;
    label?: string;
}

/**
 * Workflow definition
 */
export interface Workflow {
    id: string;
    name: string;
    description: string;
    status: 'draft' | 'active' | 'paused' | 'archived';
    nodes: WorkflowNode[];
    edges: WorkflowEdge[];
    schedule?: string;
    tags: string[];
    createdAt: string;
    updatedAt: string;
    createdBy: string;
    lastExecutedAt?: string;
}

/**
 * Workflow execution
 */
export interface WorkflowExecution {
    id: string;
    workflowId: string;
    status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
    startedAt: string;
    completedAt?: string;
    duration?: number;
    nodeExecutions: {
        nodeId: string;
        status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
        startedAt?: string;
        completedAt?: string;
        error?: string;
        output?: Record<string, unknown>;
    }[];
    error?: string;
    triggeredBy: 'manual' | 'schedule' | 'event';
}

/**
 * Create workflow DTO
 */
export interface CreateWorkflowDto {
    name: string;
    description: string;
    nodes: WorkflowNode[];
    edges: WorkflowEdge[];
    schedule?: string;
    tags?: string[];
}

/**
 * Update workflow DTO
 */
export interface UpdateWorkflowDto {
    name?: string;
    description?: string;
    nodes?: WorkflowNode[];
    edges?: WorkflowEdge[];
    schedule?: string;
    tags?: string[];
    status?: Workflow['status'];
}

/**
 * Workflow query params
 */
export interface WorkflowQueryParams {
    page?: number;
    pageSize?: number;
    search?: string;
    status?: Workflow['status'];
    sortBy?: 'name' | 'createdAt' | 'updatedAt' | 'lastExecutedAt';
    sortOrder?: 'asc' | 'desc';
}

/**
 * Workflows API
 */
export const workflowsApi = {
    /**
     * List all workflows
     */
    list: async (params?: WorkflowQueryParams): Promise<PaginatedResponse<Workflow>> => {
        return apiClient.get<PaginatedResponse<Workflow>>('/workflows', { params });
    },

    /**
     * Get workflow by ID
     */
    get: async (id: string): Promise<Workflow> => {
        return apiClient.get<Workflow>(`/workflows/${id}`);
    },

    /**
     * Create new workflow
     */
    create: async (data: CreateWorkflowDto): Promise<Workflow> => {
        return apiClient.post<Workflow, CreateWorkflowDto>('/workflows', data);
    },

    /**
     * Update workflow
     */
    update: async (id: string, data: UpdateWorkflowDto): Promise<Workflow> => {
        return apiClient.put<Workflow, UpdateWorkflowDto>(`/workflows/${id}`, data);
    },

    /**
     * Delete workflow
     */
    delete: async (id: string): Promise<void> => {
        return apiClient.delete(`/workflows/${id}`);
    },

    /**
     * Execute workflow
     */
    execute: async (id: string, params?: Record<string, unknown>): Promise<WorkflowExecution> => {
        return apiClient.post<WorkflowExecution>(`/workflows/${id}/execute`, params);
    },

    /**
     * Get workflow executions
     */
    getExecutions: async (id: string, params?: {
        page?: number;
        pageSize?: number;
        status?: WorkflowExecution['status'];
    }): Promise<PaginatedResponse<WorkflowExecution>> => {
        return apiClient.get<PaginatedResponse<WorkflowExecution>>(`/workflows/${id}/executions`, { params });
    },

    /**
     * Get execution by ID
     */
    getExecution: async (workflowId: string, executionId: string): Promise<WorkflowExecution> => {
        return apiClient.get<WorkflowExecution>(`/workflows/${workflowId}/executions/${executionId}`);
    },

    /**
     * Cancel execution
     */
    cancelExecution: async (workflowId: string, executionId: string): Promise<WorkflowExecution> => {
        return apiClient.post<WorkflowExecution>(`/workflows/${workflowId}/executions/${executionId}/cancel`);
    },

    /**
     * Activate workflow
     */
    activate: async (id: string): Promise<Workflow> => {
        return apiClient.post<Workflow>(`/workflows/${id}/activate`);
    },

    /**
     * Deactivate workflow
     */
    deactivate: async (id: string): Promise<Workflow> => {
        return apiClient.post<Workflow>(`/workflows/${id}/deactivate`);
    },
};

export default workflowsApi;
