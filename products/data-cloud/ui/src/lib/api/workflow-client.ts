/**
 * Workflow API client wrapper.
 *
 * <p><b>Purpose</b><br>
 * Provides type-safe API client for workflow operations.
 * Wraps auto-generated OpenAPI client with error handling and retry logic.
 *
 * <p><b>Architecture</b><br>
 * - Auto-generated from OpenAPI spec
 * - Error handling and retry logic
 * - Type-safe request/response handling
 * - Tenant context management
 *
 * @doc.type service
 * @doc.purpose Workflow API client
 * @doc.layer frontend
 * @doc.pattern API Client
 */

import { apiClient } from './client';
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

export const WORKFLOW_CLIENT_BOUNDARY_MESSAGE =
  'Workflow execution detail, template browsing, workflow suggestions, and remote validation are not exposed by the current Data Cloud launcher API.';

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
 * Workflow API client.
 *
 * Provides methods for workflow CRUD operations, execution, and AI features.
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
    this.tenantId = config.tenantId || 'default';
    this.userId = config.userId || 'anonymous';
  }

  private getHeaders(): Record<string, string> {
    return {
      'X-Tenant-ID': this.tenantId,
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
    const collectionId = typeof extras.collectionId === 'string' ? extras.collectionId : (fallbackCollectionId ?? 'default');
    const variables = extras.variables;
    const triggers = extras.triggers;
    const version = extras.version;
    const updatedBy = extras.updatedBy;

    const status = this.normalizeWorkflowStatus(pipeline.status);

    return {
      id: pipeline.id,
      tenantId: pipeline.tenantId ?? this.tenantId,
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
   * Lists all workflows.
   *
   * @param collectionId the collection ID to filter by
   * @param page the page number (0-based)
   * @param pageSize the page size
   * @returns the workflow list response
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
   * @param request the create workflow request
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
    return apiClient.post<ExecutionCreatedResponse>(
      `/pipelines/${workflowId}/execute`,
      request,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Gets execution status.
   *
   * @param executionId the execution ID
   * @returns the execution status
   */
  async getExecutionStatus(executionId: string): Promise<WorkflowExecution> {
    void executionId;
    throw new Error(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
  }

  /**
   * Cancels an execution.
   *
   * @param executionId the execution ID
   */
  async cancelExecution(executionId: string): Promise<void> {
    void executionId;
    throw new Error(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
  }

  /**
   * Gets AI workflow suggestions.
   *
   * @param collectionId the collection ID
   * @returns the suggestions response
   */
  async getSuggestions(collectionId: string): Promise<SuggestionsResponse> {
    void collectionId;
    throw new Error(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
  }

  /**
   * Gets workflow templates.
   *
   * @param category optional category filter
   * @returns the templates response
   */
  async getTemplates(category?: string): Promise<TemplatesResponse> {
    void category;
    throw new Error(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
  }

  /**
   * Validates a workflow.
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
    return `${protocol}//${host}${baseURL}/events/${executionId}/stream?tenantId=${this.tenantId}&userId=${this.userId}`;
  }
}

/**
 * Singleton instance of the workflow API client.
 *
 * @doc.type variable
 */
export const workflowClient = new WorkflowApiClient();
