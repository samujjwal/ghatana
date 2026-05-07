/**
 * Pipeline API client wrapper.
 *
 * <p><b>Purpose</b><br>
 * Provides type-safe API client for pipeline operations.
 * Wraps auto-generated OpenAPI client with error handling and retry logic.
 *
 * <p><b>Architecture</b><br>
 * - Auto-generated from OpenAPI spec
 * - Error handling and retry logic
 * - Type-safe request/response handling
 * - Tenant context management
 *
 * @doc.type service
 * @doc.purpose Pipeline API client
 * @doc.layer frontend
 * @doc.pattern API Client
 */

import { apiClient } from './client';
import SessionBootstrap from '../auth/session';
import { WORKFLOW_CLIENT_BOUNDARY_MESSAGE } from '@/lib/runtime-boundaries';
import {
  PipelineListResponseSchema,
  PipelineSchema,
  type Pipeline,
  type PipelineListResponse,
} from '../../contracts/schemas';
import type {
  WorkflowDefinition,
  WorkflowExecution,
  CreateWorkflowRequest,
  UpdateWorkflowRequest,
  ExecuteWorkflowRequest,
  WorkflowListResponse,
  ExecutionCreatedResponse,
  SuggestionsResponse,
  TemplatesResponse,
  ValidateWorkflowResponse,
} from '../../features/workflow/types/workflow.types';

export const WORKFLOW_EXECUTION_SUPPORTED = true;
export { WORKFLOW_CLIENT_BOUNDARY_MESSAGE } from '@/lib/runtime-boundaries';
export const WORKFLOW_CONTEXT_BOUNDARY_MESSAGE =
  'Workflow requests require explicit tenant and collection context from the current session or launcher payload.';

/**
 * API client configuration.
 *
 * @doc.type interface
 */
export interface ApiClientConfig {
  tenantId?: string;
  userId?: string;
}

/**
 * Re-export ApiError from central client.
 */
export type { ApiError } from './client';

/**
 * Pipeline API client.
 *
 * Provides methods for pipeline CRUD operations, execution, and AI features.
 * Delegates HTTP calls to the shared apiClient.
 *
 * @doc.type class
 */
export class WorkflowApiClient {
  private tenantId: string;
  private userId: string;

  /**
   * Creates a new WorkflowApiClient.
   *
   * @param config the client configuration
   */
  constructor(config: ApiClientConfig = {}) {
    this.tenantId = config.tenantId ?? SessionBootstrap.getTenantId() ?? '';
    this.userId = config.userId || 'anonymous';
  }

  private resolveTenantId(): string {
    const explicitTenantId = this.tenantId.trim();
    if (explicitTenantId) {
      return explicitTenantId;
    }

    const sessionTenantId = SessionBootstrap.requireTenantId();
    this.tenantId = sessionTenantId;
    return sessionTenantId;
  }

  private getHeaders(): Record<string, string> {
    return {
      'X-Tenant-ID': this.resolveTenantId(),
      'X-User-ID': this.userId,
    };
  }

  private normalizeWorkflowStatus(status?: string): WorkflowDefinition['status'] {
    switch ((status ?? '').toUpperCase()) {
      case 'ACTIVE':
      case 'PUBLISHED':
        return 'PUBLISHED';
      case 'ARCHIVED':
      case 'PAUSED':
      case 'INACTIVE':
        return 'ARCHIVED';
      default:
        return 'DRAFT';
    }
  }

  private pipelineToWorkflowDefinition(pipeline: Pipeline, fallbackCollectionId?: string): WorkflowDefinition {
    const extras = pipeline as Record<string, unknown>;
    const rawCollectionId = typeof extras.collectionId === 'string' ? extras.collectionId.trim() : '';
    const normalizedFallbackCollectionId = fallbackCollectionId?.trim() ?? '';
    const collectionId = rawCollectionId || normalizedFallbackCollectionId;
    if (!collectionId) {
      throw new Error(WORKFLOW_CONTEXT_BOUNDARY_MESSAGE);
    }
    const variables = extras.variables;
    const triggers = extras.triggers;
    const version = extras.version;
    const updatedBy = extras.updatedBy;

    const status = this.normalizeWorkflowStatus(pipeline.status);

    return {
      id: pipeline.id,
      tenantId: pipeline.tenantId ?? this.resolveTenantId(),
      collectionId,
      name: pipeline.name ?? 'Untitled workflow',
      description: pipeline.description,
      status,
      version: typeof version === 'number' ? version : 1,
      active: status === 'PUBLISHED',
      nodes: (pipeline.nodes ?? []) as WorkflowDefinition['nodes'],
      edges: (pipeline.edges ?? []) as WorkflowDefinition['edges'],
      triggers: Array.isArray(triggers) ? (triggers as WorkflowDefinition['triggers']) : [],
      variables: variables && typeof variables === 'object' && !Array.isArray(variables)
        ? (variables as WorkflowDefinition['variables'])
        : {},
      tags: pipeline.tags,
      createdBy: pipeline.createdBy ?? this.userId,
      updatedBy: typeof updatedBy === 'string' ? updatedBy : (pipeline.createdBy ?? this.userId),
      createdAt: pipeline.createdAt ?? new Date().toISOString(),
      updatedAt: pipeline.updatedAt ?? pipeline.createdAt ?? new Date().toISOString(),
    };
  }

  /**
   * Sets the tenant ID for subsequent requests.
   *
   * @param tenantId the tenant ID
   */
  setTenantId(tenantId: string): void {
    this.tenantId = tenantId;
  }

  /**
   * Sets the user ID for subsequent requests.
   *
   * @param userId the user ID
   */
  setUserId(userId: string): void {
    this.userId = userId;
  }

  /**
    * Lists all pipeline entries for the workflow UI.
   *
   * @param collectionId the collection ID to filter by
   * @param page the page number (0-based)
   * @param pageSize the page size
    * @returns the pipeline list response for the workflow UI
   */
  async listWorkflows(
    collectionId: string,
    page: number = 0,
    pageSize: number = 50
  ): Promise<WorkflowListResponse> {
    const rawResponse = await apiClient.get<PipelineListResponse>('/pipelines', {
      params: { collectionId, limit: pageSize },
      headers: this.getHeaders(),
    });
    const response = PipelineListResponseSchema.parse(rawResponse);
    return {
      workflows: response.pipelines.map((pipeline) => this.pipelineToWorkflowDefinition(pipeline, collectionId)),
      total: response.count,
      page,
      pageSize,
    };
  }

  /**
   * Gets a workflow by ID.
   *
   * @param workflowId the workflow ID
   * @returns the workflow definition
   */
  async getWorkflow(workflowId: string): Promise<WorkflowDefinition> {
    const rawResponse = await apiClient.get<Pipeline>(`/pipelines/${workflowId}`, {
      headers: this.getHeaders(),
    });
    const response = PipelineSchema.parse(rawResponse);
    return this.pipelineToWorkflowDefinition(response);
  }

  /**
   * Creates a new workflow.
   *
  * @param request the create pipeline request
   * @returns the created workflow
   */
  async createWorkflow(request: CreateWorkflowRequest): Promise<WorkflowDefinition> {
    const rawResponse = await apiClient.post<Pipeline>('/pipelines', request, {
      headers: this.getHeaders(),
    });
    const response = PipelineSchema.parse(rawResponse);
    return this.pipelineToWorkflowDefinition(response, request.collectionId);
  }

  /**
   * Updates a workflow.
   *
   * @param workflowId the workflow ID
   * @param request the update workflow request
   * @returns the updated workflow
   */
  async updateWorkflow(
    workflowId: string,
    request: UpdateWorkflowRequest
  ): Promise<WorkflowDefinition> {
    const rawResponse = await apiClient.put<Pipeline>(
      `/pipelines/${workflowId}`,
      request,
      { headers: this.getHeaders() }
    );
    const response = PipelineSchema.parse(rawResponse);
    return this.pipelineToWorkflowDefinition(response);
  }

  /**
   * Deletes a workflow.
   *
   * @param workflowId the workflow ID
   */
  async deleteWorkflow(workflowId: string): Promise<void> {
    await apiClient.delete(`/pipelines/${workflowId}`, {
      headers: this.getHeaders(),
    });
  }

  /**
   * Executes a workflow.
   *
   * @param workflowId the workflow ID
   * @param request the execute workflow request
   * @returns the execution created response
   */
  async executeWorkflow(
    workflowId: string,
    request: ExecuteWorkflowRequest = {}
  ): Promise<ExecutionCreatedResponse> {
    return apiClient.post<ExecutionCreatedResponse, ExecuteWorkflowRequest>(
      `/pipelines/${workflowId}/execute`,
      request,
      { headers: this.getHeaders() },
    );
  }

  /**
   * Gets execution status.
   *
   * @param executionId the execution ID
   * @returns the execution status
   */
  async getExecutionStatus(executionId: string): Promise<WorkflowExecution> {
    const response = await apiClient.get<Record<string, unknown>>(`/executions/${executionId}`, {
      headers: this.getHeaders(),
    });
    const nodes = Array.isArray(response.nodes) ? response.nodes : [];
    const totalNodes = Number(response.totalNodes ?? nodes.length);
    const completedNodes = Number(response.completedNodes ?? totalNodes);
    return {
      id: String(response.id ?? executionId),
      workflowId: String(response.pipelineId ?? ''),
      tenantId: this.resolveTenantId(),
      status: String(response.status ?? 'PENDING') as WorkflowExecution['status'],
      progress: totalNodes > 0 ? Math.round((completedNodes / totalNodes) * 100) : 100,
      startedAt: String(response.startTime ?? new Date().toISOString()),
      completedAt: typeof response.endTime === 'string' ? response.endTime : undefined,
      duration: typeof response.duration === 'number' ? response.duration : undefined,
      nodeStatuses: nodes.map((node) => {
        const typedNode = node as Record<string, unknown>;
        return {
          nodeId: String(typedNode.id ?? ''),
          nodeName: String(typedNode.name ?? typedNode.id ?? ''),
          state: String(typedNode.status ?? 'PENDING') as WorkflowExecution['nodeStatuses'][number]['state'],
          startedAt: typeof typedNode.startTime === 'string' ? typedNode.startTime : undefined,
          completedAt: typeof typedNode.endTime === 'string' ? typedNode.endTime : undefined,
          duration: typeof typedNode.duration === 'number' ? typedNode.duration : undefined,
          error: typeof typedNode.error === 'string' && typedNode.error !== '' ? typedNode.error : undefined,
          output: typedNode.output,
        };
      }),
      error: typeof response.error === 'string' ? response.error : undefined,
      output: response.output,
    };
  }

  /**
   * Cancels an execution.
   *
   * @param executionId the execution ID
   */
  async cancelExecution(executionId: string): Promise<void> {
    await apiClient.post(`/executions/${executionId}/cancel`, {}, {
      headers: this.getHeaders(),
    });
  }

  /**
  * Gets AI pipeline suggestions.
   *
   * @param collectionId the collection ID
   * @returns the suggestions response
   */
  async getSuggestions(collectionId: string): Promise<SuggestionsResponse> {
    void collectionId;
    throw new Error(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
  }

  /**
  * Gets pipeline templates.
  *
  * Launcher boundary note: canonical template-browsing routes are not
  * exposed yet, so this method currently fails explicitly.
   *
   * @param category optional category filter
   * @returns the templates response
   */
  async getTemplates(category?: string): Promise<TemplatesResponse> {
    void category;
    throw new Error(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
  }

  /**
  * Validates a pipeline definition.
   *
   * @param workflow the workflow to validate
   * @returns the validation result
   */
  async validateWorkflow(workflow: WorkflowDefinition): Promise<ValidateWorkflowResponse> {
    void workflow;
    throw new Error(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
  }

  /**
   * Gets the WebSocket stream URL for execution updates.
   *
   * @param executionId the execution ID
   * @returns the WebSocket URL
   */
  getExecutionStreamUrl(executionId: string): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1';
    return `${protocol}//${host}${baseURL}/events/${executionId}/stream?tenantId=${this.resolveTenantId()}&userId=${this.userId}`;
  }
}

/**
 * Singleton instance of the pipeline API client.
 *
 * @doc.type variable
 */
export const workflowClient = new WorkflowApiClient();
