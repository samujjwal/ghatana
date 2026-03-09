import axios, { AxiosInstance } from 'axios';
import type {
  WorkflowDefinition,
  WorkflowExecution,
  ValidationResult,
  WorkflowTemplate,
  WorkflowSuggestion,
} from '@/types/workflow.types';

/**
 * Workflow API client.
 *
 * <p><b>Purpose</b><br>
 * Provides type-safe API client for workflow operations.
 * Auto-generated from OpenAPI specification.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const client = new WorkflowClient('http://localhost:8080');
 *
 * // List workflows
 * const workflows = await client.listWorkflows('tenant-123');
 *
 * // Create workflow
 * const workflow = await client.createWorkflow('tenant-123', {
 *   name: 'My Workflow',
 *   collectionId: 'collection-id',
 *   nodes: [],
 *   edges: [],
 *   triggers: [],
 *   variables: {}
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Workflow API client
 * @doc.layer frontend
 */
export class WorkflowClient {
  private client: AxiosInstance;
  private tenantId: string = '';

  /**
   * Creates a new workflow client.
   *
   * @param baseURL the API base URL
   * @param tenantId the tenant ID (optional)
   */
  constructor(baseURL: string = '/api/v1', tenantId?: string) {
    this.client = axios.create({
      baseURL,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (tenantId) {
      this.setTenantId(tenantId);
    }

    // Add request interceptor for tenant header
    this.client.interceptors.request.use((config) => {
      if (this.tenantId) {
        config.headers['X-Tenant-ID'] = this.tenantId;
      }
      return config;
    });

    // Add response interceptor for error handling
    this.client.interceptors.response.use(
      (response) => response,
      (error) => {
        console.error('API Error:', error.response?.data || error.message);
        return Promise.reject(error);
      }
    );
  }

  /**
   * Sets the tenant ID.
   *
   * @param tenantId the tenant ID
   */
  setTenantId(tenantId: string): void {
    this.tenantId = tenantId;
  }

  /**
   * Lists all workflows.
   *
   * @param tenantId the tenant ID
   * @param offset the offset (default: 0)
   * @param limit the limit (default: 50)
   * @returns list of workflows
   */
  async listWorkflows(
    tenantId: string,
    offset: number = 0,
    limit: number = 50
  ): Promise<WorkflowDefinition[]> {
    const response = await this.client.get('/workflows', {
      params: { offset, limit },
      headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data.items || [];
  }

  /**
   * Gets a workflow by ID.
   *
   * @param tenantId the tenant ID
   * @param workflowId the workflow ID
   * @returns the workflow
   */
  async getWorkflow(tenantId: string, workflowId: string): Promise<WorkflowDefinition> {
    const response = await this.client.get(`/workflows/${workflowId}`, {
      headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data;
  }

  /**
   * Creates a new workflow.
   *
   * @param tenantId the tenant ID
   * @param workflow the workflow definition
   * @returns the created workflow
   */
  async createWorkflow(
    tenantId: string,
    workflow: Omit<WorkflowDefinition, 'id' | 'createdAt' | 'updatedAt' | 'createdBy' | 'updatedBy'>
  ): Promise<WorkflowDefinition> {
    const response = await this.client.post('/workflows', workflow, {
      headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data;
  }

  /**
   * Updates a workflow.
   *
   * @param tenantId the tenant ID
   * @param workflowId the workflow ID
   * @param updates the partial updates
   * @returns the updated workflow
   */
  async updateWorkflow(
    tenantId: string,
    workflowId: string,
    updates: Partial<WorkflowDefinition>
  ): Promise<WorkflowDefinition> {
    const response = await this.client.put(`/workflows/${workflowId}`, updates, {
      headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data;
  }

  /**
   * Deletes a workflow.
   *
   * @param tenantId the tenant ID
   * @param workflowId the workflow ID
   */
  async deleteWorkflow(tenantId: string, workflowId: string): Promise<void> {
    await this.client.delete(`/workflows/${workflowId}`, {
      headers: { 'X-Tenant-ID': tenantId },
    });
  }

  /**
   * Executes a workflow.
   *
   * @param tenantId the tenant ID
   * @param workflowId the workflow ID
   * @param variables the execution variables
   * @returns the execution record
   */
  async executeWorkflow(
    tenantId: string,
    workflowId: string,
    variables?: Record<string, unknown>
  ): Promise<WorkflowExecution> {
    const response = await this.client.post(
      `/workflows/${workflowId}/execute`,
      { variables: variables || {} },
      {
        headers: { 'X-Tenant-ID': tenantId },
      }
    );
    return response.data;
  }

  /**
   * Gets workflow execution status.
   *
   * @param tenantId the tenant ID
   * @param executionId the execution ID
   * @returns the execution record
   */
  async getExecutionStatus(tenantId: string, executionId: string): Promise<WorkflowExecution> {
    const response = await this.client.get(`/executions/${executionId}`, {
      headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data;
  }

  /**
   * Validates a workflow.
   *
   * @param tenantId the tenant ID
   * @param workflow the workflow to validate
   * @returns the validation result
   */
  async validateWorkflow(
    tenantId: string,
    workflow: WorkflowDefinition
  ): Promise<ValidationResult> {
    const response = await this.client.post('/workflows/validate', workflow, {
      headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data;
  }

  /**
   * Gets workflow templates.
   *
   * @param tenantId the tenant ID
   * @param category the category filter (optional)
   * @returns list of templates
   */
  async getTemplates(tenantId: string, category?: string): Promise<WorkflowTemplate[]> {
    const response = await this.client.get('/workflows/templates', {
      params: { category },
      headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data.items || [];
  }

  /**
   * Gets AI suggestions for a workflow.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @returns list of suggestions
   */
  async getSuggestions(tenantId: string, collectionId: string): Promise<WorkflowSuggestion[]> {
    const response = await this.client.post(
      '/workflows/suggest',
      { collectionId },
      {
        headers: { 'X-Tenant-ID': tenantId },
      }
    );
    return response.data.suggestions || [];
  }
}

/**
 * Create a singleton workflow client instance.
 *
 * @param baseURL the API base URL
 * @param tenantId the tenant ID
 * @returns the workflow client
 */
export function createWorkflowClient(
  baseURL?: string,
  tenantId?: string
): WorkflowClient {
  return new WorkflowClient(baseURL, tenantId);
}

// Export singleton instance
export const workflowClient = createWorkflowClient();
