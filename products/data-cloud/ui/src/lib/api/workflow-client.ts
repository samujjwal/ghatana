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
    return apiClient.get<WorkflowListResponse>('/workflows', {
      params: { collectionId, page, pageSize },
      headers: this.getHeaders(),
    });
  }

  /**
   * Gets a workflow by ID.
   *
   * @param workflowId the workflow ID
   * @returns the workflow definition
   */
  async getWorkflow(workflowId: string): Promise<WorkflowDefinition> {
    return apiClient.get<WorkflowDefinition>(`/workflows/${workflowId}`, {
      headers: this.getHeaders(),
    });
  }

  /**
   * Creates a new workflow.
   *
   * @param request the create workflow request
   * @returns the created workflow
   */
  async createWorkflow(request: CreateWorkflowRequest): Promise<WorkflowDefinition> {
    return apiClient.post<WorkflowDefinition>('/workflows', request, {
      headers: this.getHeaders(),
    });
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
    return apiClient.put<WorkflowDefinition>(
      `/workflows/${workflowId}`,
      request,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Deletes a workflow.
   *
   * @param workflowId the workflow ID
   */
  async deleteWorkflow(workflowId: string): Promise<void> {
    await apiClient.delete(`/workflows/${workflowId}`, {
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
      `/workflows/${workflowId}/execute`,
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
    return apiClient.get<WorkflowExecution>(`/executions/${executionId}`, {
      headers: this.getHeaders(),
    });
  }

  /**
   * Cancels an execution.
   *
   * @param executionId the execution ID
   */
  async cancelExecution(executionId: string): Promise<void> {
    await apiClient.post(`/executions/${executionId}/cancel`, undefined, {
      headers: this.getHeaders(),
    });
  }

  /**
   * Gets AI workflow suggestions.
   *
   * @param collectionId the collection ID
   * @returns the suggestions response
   */
  async getSuggestions(collectionId: string): Promise<SuggestionsResponse> {
    return apiClient.get<SuggestionsResponse>('/workflows/suggestions', {
      params: { collectionId },
      headers: this.getHeaders(),
    });
  }

  /**
   * Gets workflow templates.
   *
   * @param category optional category filter
   * @returns the templates response
   */
  async getTemplates(category?: string): Promise<TemplatesResponse> {
    return apiClient.get<TemplatesResponse>('/workflows/templates', {
      params: category ? { category } : undefined,
      headers: this.getHeaders(),
    });
  }

  /**
   * Validates a workflow.
   *
   * @param workflow the workflow to validate
   * @returns the validation result
   */
  async validateWorkflow(workflow: WorkflowDefinition): Promise<ValidateWorkflowResponse> {
    return apiClient.post<ValidateWorkflowResponse>(
      '/workflows/validate',
      workflow,
      { headers: this.getHeaders() }
    );
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
    return `${protocol}//${host}${baseURL}/executions/${executionId}/stream?tenantId=${this.tenantId}&userId=${this.userId}`;
  }
}

/**
 * Singleton instance of the workflow API client.
 *
 * @doc.type variable
 */
export const workflowClient = new WorkflowApiClient();
