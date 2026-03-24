/**
 * Workflows API
 *
 * Routes backed by /api/v1/pipelines (Option A,
 * DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 2).
 * The backend pipeline registry is the source of truth for workflow/pipeline
 * definitions.  Raw responses are transformed to the Workflow UI type via
 * {@link pipelineToWorkflow}.
 *
 * @doc.type service
 * @doc.purpose Workflows API endpoints
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

import { apiClient, PaginatedResponse } from './client';

// ---------------------------------------------------------------------------
// Backend pipeline response shapes
// ---------------------------------------------------------------------------

interface BackendPipeline {
    id: string;
    tenantId?: string;
    name?: string;
    description?: string;
    status?: string;
    nodes?: unknown[];
    edges?: unknown[];
    schedule?: string;
    tags?: string[];
    createdAt?: string;
    updatedAt?: string;
    createdBy?: string;
    lastExecutedAt?: string;
    [key: string]: unknown;
}

interface BackendPipelineListResponse {
    tenantId: string;
    pipelines: BackendPipeline[];
    count: number;
    timestamp: string;
}

// ---------------------------------------------------------------------------
// Transformation helpers
// ---------------------------------------------------------------------------

function pipelineToWorkflow(p: BackendPipeline): Workflow {
    return {
        id: p.id,
        name: String(p.name ?? ''),
        description: String(p.description ?? ''),
        status: (p.status ?? 'draft') as Workflow['status'],
        nodes: Array.isArray(p.nodes) ? (p.nodes as WorkflowNode[]) : [],
        edges: Array.isArray(p.edges) ? (p.edges as WorkflowEdge[]) : [],
        schedule: p.schedule,
        tags: Array.isArray(p.tags) ? p.tags : [],
        createdAt: String(p.createdAt ?? new Date().toISOString()),
        updatedAt: String(p.updatedAt ?? new Date().toISOString()),
        createdBy: String(p.createdBy ?? 'unknown'),
        lastExecutedAt: p.lastExecutedAt ? String(p.lastExecutedAt) : undefined,
    };
}
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
 *
 * All CRUD routes delegate to /api/v1/pipelines (the real backend pipeline
 * registry).  No execution sub-routes exist; execute/cancel are stubs for
 * forward-compatibility.
 */
export const workflowsApi = {
    /**
     * List all workflows (pipelines).
     * GET /api/v1/pipelines
     */
    list: async (params?: WorkflowQueryParams): Promise<PaginatedResponse<Workflow>> => {
        const limit = params?.pageSize ?? 50;
        const raw = await apiClient.get<BackendPipelineListResponse>('/pipelines', {
            params: { limit, ...(params?.status ? { status: params.status } : {}) },
        });
        const items = (raw.pipelines ?? []).map(pipelineToWorkflow);
        const page = params?.page ?? 1;
        const offset = (page - 1) * limit;
        return {
            items,
            total: raw.count ?? items.length,
            page,
            pageSize: limit,
            hasMore: offset + items.length < (raw.count ?? items.length),
        };
    },

    /**
     * Get workflow (pipeline) by ID.
     * GET /api/v1/pipelines/:pipelineId
     */
    get: async (id: string): Promise<Workflow> => {
        const raw = await apiClient.get<BackendPipeline>(`/pipelines/${id}`);
        return pipelineToWorkflow(raw);
    },

    /**
     * Create new workflow (pipeline).
     * POST /api/v1/pipelines
     */
    create: async (data: CreateWorkflowDto): Promise<Workflow> => {
        const raw = await apiClient.post<BackendPipeline, CreateWorkflowDto>('/pipelines', data);
        return pipelineToWorkflow(raw);
    },

    /**
     * Update workflow (pipeline).
     * PUT /api/v1/pipelines/:pipelineId
     */
    update: async (id: string, data: UpdateWorkflowDto): Promise<Workflow> => {
        const raw = await apiClient.put<BackendPipeline, UpdateWorkflowDto>(`/pipelines/${id}`, data);
        return pipelineToWorkflow(raw);
    },

    /**
     * Delete workflow (pipeline).
     * DELETE /api/v1/pipelines/:pipelineId
     */
    delete: async (id: string): Promise<void> => {
        return apiClient.delete(`/pipelines/${id}`);
    },

    /**
     * Execute workflow — stub; no dedicated execute endpoint in the backend.
     * Kept for forward-compatibility.
     * POST /api/v1/pipelines/:pipelineId/execute
     */
    execute: async (id: string, params?: Record<string, unknown>): Promise<WorkflowExecution> => {
        return apiClient.post<WorkflowExecution>(`/pipelines/${id}/execute`, params);
    },

    /**
     * Get workflow executions — no dedicated backend route; returns empty list.
     * Events can be queried via the events API if needed.
     */
    getExecutions: async (_id: string, _params?: {
        page?: number;
        pageSize?: number;
        status?: WorkflowExecution['status'];
    }): Promise<PaginatedResponse<WorkflowExecution>> => {
        return { items: [], total: 0, page: 1, pageSize: 20, hasMore: false };
    },

    /**
     * Get execution by ID — no dedicated backend route; stub.
     */
    getExecution: async (_workflowId: string, _executionId: string): Promise<WorkflowExecution> => {
        throw new Error('getExecution not supported by the current backend');
    },

    /**
     * Cancel execution — no dedicated backend route; stub.
     */
    cancelExecution: async (_workflowId: string, _executionId: string): Promise<WorkflowExecution> => {
        throw new Error('cancelExecution not supported by the current backend');
    },

    /**
     * Activate workflow — updates pipeline status to 'active'.
     * PUT /api/v1/pipelines/:pipelineId
     */
    activate: async (id: string): Promise<Workflow> => {
        const raw = await apiClient.put<BackendPipeline, Partial<UpdateWorkflowDto>>(
            `/pipelines/${id}`,
            { status: 'active' }
        );
        return pipelineToWorkflow(raw);
    },

    /**
     * Deactivate workflow — updates pipeline status to 'paused'.
     * PUT /api/v1/pipelines/:pipelineId
     */
    deactivate: async (id: string): Promise<Workflow> => {
        const raw = await apiClient.put<BackendPipeline, Partial<UpdateWorkflowDto>>(
            `/pipelines/${id}`,
            { status: 'paused' }
        );
        return pipelineToWorkflow(raw);
    },
};

export default workflowsApi;
