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
import {
    ExecutionSchema,
    PipelineListResponseSchema,
    PipelineMutationRequestSchema,
    PipelineSchema,
    type Execution as BackendExecution,
    type Pipeline as BackendPipeline,
    type PipelineListResponse as BackendPipelineListResponse,
    type PipelineMutationRequest,
} from '../../contracts/schemas';

// ---------------------------------------------------------------------------
// Backend pipeline response shapes
// ---------------------------------------------------------------------------

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

function executionToWorkflowExecution(execution: BackendExecution): WorkflowExecution {
    return {
        id: execution.id,
        workflowId: execution.workflowId,
        status: execution.status,
        startedAt: execution.startedAt,
        completedAt: execution.completedAt,
        duration: execution.duration,
        nodeExecutions: [],
        error: execution.error,
        triggeredBy: 'manual',
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
 * registry). Execution and reporting now flow through Data Cloud runtime
 * plugins exposed by the launcher API.
 */
export const workflowsApi = {
    /**
     * List all workflows (pipelines).
     * GET /api/v1/pipelines
     */
    list: async (params?: WorkflowQueryParams): Promise<PaginatedResponse<Workflow>> => {
        const limit = params?.pageSize ?? 50;
        const rawResponse = await apiClient.get<BackendPipelineListResponse>('/pipelines', {
            params: { limit, ...(params?.status ? { status: params.status } : {}) },
        });
        const raw = PipelineListResponseSchema.parse(rawResponse);
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
        const rawResponse = await apiClient.get<BackendPipeline>(`/pipelines/${id}`);
        const raw = PipelineSchema.parse(rawResponse);
        return pipelineToWorkflow(raw);
    },

    /**
     * Create new workflow (pipeline).
     * POST /api/v1/pipelines
     */
    create: async (data: CreateWorkflowDto): Promise<Workflow> => {
        const request = PipelineMutationRequestSchema.parse(data) as PipelineMutationRequest;
        const rawResponse = await apiClient.post<BackendPipeline, PipelineMutationRequest>('/pipelines', request);
        const raw = PipelineSchema.parse(rawResponse);
        return pipelineToWorkflow(raw);
    },

    /**
     * Update workflow (pipeline).
     * PUT /api/v1/pipelines/:pipelineId
     */
    update: async (id: string, data: UpdateWorkflowDto): Promise<Workflow> => {
        const request = PipelineMutationRequestSchema.parse(data) as PipelineMutationRequest;
        const rawResponse = await apiClient.put<BackendPipeline, PipelineMutationRequest>(`/pipelines/${id}`, request);
        const raw = PipelineSchema.parse(rawResponse);
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
     * Execute workflow.
     * POST /api/v1/pipelines/:pipelineId/execute
     */
    execute: async (id: string, params?: Record<string, unknown>): Promise<WorkflowExecution> => {
        const rawResponse = await apiClient.post<BackendExecution, Record<string, unknown>>(
            `/pipelines/${id}/execute`,
            params ?? {}
        );
        const raw = ExecutionSchema.parse({
            ...rawResponse,
            id: rawResponse.id ?? (rawResponse as { executionId?: string }).executionId,
            workflowId: rawResponse.workflowId ?? id,
            startedAt: rawResponse.startedAt ?? new Date().toISOString(),
            status: String(rawResponse.status ?? 'pending').toLowerCase(),
        });
        return executionToWorkflowExecution(raw);
    },

    /**
     * Get workflow executions.
     * GET /api/v1/pipelines/:pipelineId/executions
     */
    getExecutions: async (_id: string, _params?: {
        page?: number;
        pageSize?: number;
        status?: WorkflowExecution['status'];
    }): Promise<PaginatedResponse<WorkflowExecution>> => {
        const rawResponse = await apiClient.get<PaginatedResponse<BackendExecution>>(`/pipelines/${_id}/executions`, {
            params: _params,
        });
        return {
            items: rawResponse.items.map((execution) => executionToWorkflowExecution(ExecutionSchema.parse(execution))),
            total: rawResponse.total,
            page: rawResponse.page,
            pageSize: rawResponse.pageSize,
            hasMore: rawResponse.hasMore,
        };
    },

    /**
     * Get execution by ID.
     * GET /api/v1/pipelines/:pipelineId/executions/:executionId
     */
    getExecution: async (_workflowId: string, _executionId: string): Promise<WorkflowExecution> => {
        const rawResponse = await apiClient.get<BackendExecution>(`/pipelines/${_workflowId}/executions/${_executionId}`);
        return executionToWorkflowExecution(ExecutionSchema.parse(rawResponse));
    },

    /**
     * Cancel execution.
     * POST /api/v1/pipelines/:pipelineId/executions/:executionId/cancel
     */
    cancelExecution: async (_workflowId: string, _executionId: string): Promise<WorkflowExecution> => {
        const rawResponse = await apiClient.post<BackendExecution>(`/pipelines/${_workflowId}/executions/${_executionId}/cancel`, {});
        return executionToWorkflowExecution(ExecutionSchema.parse(rawResponse));
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
