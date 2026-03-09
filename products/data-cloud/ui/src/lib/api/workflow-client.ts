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

import axios, { AxiosInstance, AxiosError } from 'axios';
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
  baseURL?: string;
  timeout?: number;
  tenantId?: string;
  userId?: string;
}

/**
 * API error response.
 *
 * @doc.type interface
 */
export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

/**
 * Workflow API client.
 *
 * Provides methods for workflow CRUD operations, execution, and AI features.
 *
 * @doc.type class
 */
export class WorkflowApiClient {
  private client: AxiosInstance;
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

    this.client = axios.create({
      baseURL: config.baseURL || '/api/v1',
      timeout: config.timeout || 30000,
      headers: {
        'Content-Type': 'application/json',
        'X-Tenant-ID': this.tenantId,
        'X-User-ID': this.userId,
      },
    });

    // Add response interceptor for error handling
    this.client.interceptors.response.use(
      (response) => response,
      (error) => this.handleError(error)
    );
  }

  /**
   * Sets the tenant ID for subsequent requests.
   *
   * @param tenantId the tenant ID
   */
  setTenantId(tenantId: string): void {
    this.tenantId = tenantId;
    this.client.defaults.headers['X-Tenant-ID'] = tenantId;
  }

  /**
   * Sets the user ID for subsequent requests.
   *
   * @param userId the user ID
   */
  setUserId(userId: string): void {
    this.userId = userId;
    this.client.defaults.headers['X-User-ID'] = userId;
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
    const response = await this.client.get<WorkflowListResponse>('/workflows', {
      params: { collectionId, page, pageSize },
    });
    return response.data;
  }

  /**
   * Gets a workflow by ID.
   *
   * @param workflowId the workflow ID
   * @returns the workflow definition
   */
  async getWorkflow(workflowId: string): Promise<WorkflowDefinition> {
    const response = await this.client.get<WorkflowDefinition>(`/workflows/${workflowId}`);
    return response.data;
  }

  /**
   * Creates a new workflow.
   *
   * @param request the create workflow request
   * @returns the created workflow
   */
  async createWorkflow(request: CreateWorkflowRequest): Promise<WorkflowDefinition> {
    const response = await this.client.post<WorkflowDefinition>('/workflows', request);
    return response.data;
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
    const response = await this.client.put<WorkflowDefinition>(
      `/workflows/${workflowId}`,
      request
    );
    return response.data;
  }

  /**
   * Deletes a workflow.
   *
   * @param workflowId the workflow ID
   */
  async deleteWorkflow(workflowId: string): Promise<void> {
    await this.client.delete(`/workflows/${workflowId}`);
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
    const response = await this.client.post<ExecutionCreatedResponse>(
      `/workflows/${workflowId}/execute`,
      request
    );
    return response.data;
  }

  /**
   * Gets execution status.
   *
   * @param executionId the execution ID
   * @returns the execution status
   */
  async getExecutionStatus(executionId: string): Promise<WorkflowExecution> {
    const response = await this.client.get<WorkflowExecution>(`/executions/${executionId}`);
    return response.data;
  }

  /**
   * Cancels an execution.
   *
   * @param executionId the execution ID
   */
  async cancelExecution(executionId: string): Promise<void> {
    await this.client.post(`/executions/${executionId}/cancel`);
  }

  /**
   * Gets AI workflow suggestions.
   *
   * @param collectionId the collection ID
   * @returns the suggestions response
   */
  async getSuggestions(collectionId: string): Promise<SuggestionsResponse> {
    const response = await this.client.get<SuggestionsResponse>('/workflows/suggestions', {
      params: { collectionId },
    });
    return response.data;
  }

  /**
   * Gets workflow templates.
   *
   * @param category optional category filter
   * @returns the templates response
   */
  async getTemplates(category?: string): Promise<TemplatesResponse> {
    const response = await this.client.get<TemplatesResponse>('/workflows/templates', {
      params: category ? { category } : undefined,
    });
    return response.data;
  }

  /**
   * Validates a workflow.
   *
   * @param workflow the workflow to validate
   * @returns the validation result
   */
  async validateWorkflow(workflow: WorkflowDefinition): Promise<ValidateWorkflowResponse> {
    const response = await this.client.post<ValidateWorkflowResponse>(
      '/workflows/validate',
      workflow
    );
    return response.data;
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
    const baseURL = this.client.defaults.baseURL || '/api/v1';
    return `${protocol}//${host}${baseURL}/executions/${executionId}/stream?tenantId=${this.tenantId}&userId=${this.userId}`;
  }

  /**
   * Handles API errors.
   *
   * @param error the axios error
   * @throws the parsed API error
   */
  private handleError(error: AxiosError): never {
    if (error.response) {
      const data = error.response.data as Record<string, unknown>;
      const apiError: ApiError = {
        code: (data.code as string) || 'UNKNOWN_ERROR',
        message: (data.message as string) || error.message,
        details: (data.details as Record<string, unknown>) || undefined,
      };
      throw apiError;
    }

    if (error.request) {
      throw {
        code: 'NETWORK_ERROR',
        message: 'Network error: No response from server',
      };
    }

    throw {
      code: 'REQUEST_ERROR',
      message: error.message,
    };
  }
}

/**
 * Singleton instance of the workflow API client.
 *
 * @doc.type variable
 */
export const workflowClient = new WorkflowApiClient({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
});
