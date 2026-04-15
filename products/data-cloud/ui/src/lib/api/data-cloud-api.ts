/**
 * Unified Data Cloud API Client
 *
 * Production-ready API client for all Data Cloud operations.
 * Provides a unified interface for all Data Cloud API operations.
 *
 * @doc.type service
 * @doc.purpose Unified API client for Data Cloud UI
 * @doc.layer frontend
 * @doc.pattern Facade, Repository
 */

import { apiClient, PaginatedResponse } from './client';
import { collectionsApi, Collection, CreateCollectionDto, UpdateCollectionDto } from './collections';
import { workflowsApi, Workflow, WorkflowExecution, CreateWorkflowDto, UpdateWorkflowDto } from './workflows';
import { collectionDataClient, CollectionRecord, ListRecordsResponse } from './collection-data-client';
import {
  EntityValidationResponseSchema,
  SearchResultSchema,
  type EntityValidationResponse,
  type SearchResult,
} from '../../contracts/schemas';

/**
 * API Response wrapper for backward compatibility with mock client
 */
export interface ApiResponse<T> {
  data: T;
  status: number;
  message?: string;
}

/**
 * Entity type (maps to CollectionRecord)
 */
export interface Entity {
  id: string;
  collectionId: string;
  tenantId?: string;
  data: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Execution type
 */
export interface Execution {
  id: string;
  workflowId: string;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
  startedAt: string;
  completedAt?: string;
  duration?: number;
  input?: Record<string, unknown>;
  output?: Record<string, unknown>;
  error?: string;
}

/**
 * Configuration for the API client
 */
export interface DataCloudApiConfig {
  baseUrl?: string;
  tenantId?: string;
  timeout?: number;
}

/**
 * Data Cloud API Client
 *
 * Unified API client that provides all Data Cloud operations.
 * Can be configured to use mock data for development or real API for production.
 */
class DataCloudApiClient {
  private tenantId: string = 'default';

  private unsupportedOperation(message: string): never {
    throw new Error(message);
  }

  constructor(config?: DataCloudApiConfig) {
    if (config?.tenantId) {
      this.tenantId = config.tenantId;
    }
    if (config?.baseUrl) {
      collectionDataClient.setBaseURL(config.baseUrl);
    }
  }

  /**
   * Set tenant ID for all requests
   */
  setTenantId(tenantId: string): void {
    this.tenantId = tenantId;
    collectionDataClient.setTenantId(tenantId);
  }

  /**

   * Get all collections
   */
  async getCollections(): Promise<ApiResponse<Collection[]>> {
    try {
      const response = await collectionsApi.list();
      return {
        data: response.items,
        status: 200,
        message: 'Collections retrieved successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Get collection by ID
   */
  async getCollectionById(id: string): Promise<ApiResponse<Collection>> {
    try {
      const collection = await collectionsApi.get(id);
      return {
        data: collection,
        status: 200,
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Create a new collection
   */
  async createCollection(data: CreateCollectionDto): Promise<ApiResponse<Collection>> {
    try {
      const collection = await collectionsApi.create(data);
      return {
        data: collection,
        status: 201,
        message: 'Collection created successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Update a collection
   */
  async updateCollection(id: string, data: UpdateCollectionDto): Promise<ApiResponse<Collection>> {
    try {
      const collection = await collectionsApi.update(id, data);
      return {
        data: collection,
        status: 200,
        message: 'Collection updated successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Delete a collection
   */
  async deleteCollection(id: string): Promise<ApiResponse<{ id: string }>> {
    try {
      await collectionsApi.delete(id);
      return {
        data: { id },
        status: 200,
        message: 'Collection deleted successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Search collections
   */
  async searchCollections(query: string): Promise<ApiResponse<Collection[]>> {
    try {
      const response = await collectionsApi.list({ search: query });
      return {
        data: response.items,
        status: 200,
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  // ═══════════════════════════════════════════════════════════════
  // Entity/Record API
  // ═══════════════════════════════════════════════════════════════

  /**
   * Get entities for a collection
   */
  async getCollectionEntities(
    collectionId: string,
    skip = 0,
    limit = 10
  ): Promise<ApiResponse<PaginatedResponse<Entity>>> {
    try {
      const response = await collectionDataClient.listRecords(
        this.tenantId,
        collectionId,
        { offset: skip, limit }
      );
      
      const entities: Entity[] = response.items.map(record => ({
        id: record.id,
        collectionId: record.collectionId,
        tenantId: record.tenantId,
        data: record.data,
        createdAt: record.createdAt,
        updatedAt: record.updatedAt,
        createdBy: record.createdBy,
        updatedBy: record.updatedBy,
        version: record.version,
      }));

      return {
        data: {
          items: entities,
          total: response.total,
          page: Math.floor(skip / limit) + 1,
          pageSize: limit,
          hasMore: skip + limit < response.total,
        },
        status: 200,
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Create an entity
   */
  async createEntity(
    collectionId: string,
    data: Record<string, unknown>
  ): Promise<ApiResponse<Entity>> {
    try {
      const record = await collectionDataClient.createRecord(
        this.tenantId,
        collectionId,
        { data }
      );

      return {
        data: {
          id: record.id,
          collectionId: record.collectionId,
          tenantId: record.tenantId,
          data: record.data,
          createdAt: record.createdAt,
          updatedAt: record.updatedAt,
          createdBy: record.createdBy,
          updatedBy: record.updatedBy,
          version: record.version,
        },
        status: 201,
        message: 'Entity created successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Update an entity
   */
  async updateEntity(
    collectionId: string,
    entityId: string,
    data: Record<string, unknown>
  ): Promise<ApiResponse<Entity>> {
    try {
      const record = await collectionDataClient.updateRecord(
        this.tenantId,
        collectionId,
        entityId,
        { data }
      );

      return {
        data: {
          id: record.id,
          collectionId: record.collectionId,
          tenantId: record.tenantId,
          data: record.data,
          createdAt: record.createdAt,
          updatedAt: record.updatedAt,
          createdBy: record.createdBy,
          updatedBy: record.updatedBy,
          version: record.version,
        },
        status: 200,
        message: 'Entity updated successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Delete an entity
   */
  async deleteEntity(
    collectionId: string,
    entityId: string
  ): Promise<ApiResponse<{ success: boolean }>> {
    try {
      await collectionDataClient.deleteRecord(this.tenantId, collectionId, entityId);
      return {
        data: { success: true },
        status: 200,
        message: 'Entity deleted successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  // ═══════════════════════════════════════════════════════════════
  // Workflows API
  // ═══════════════════════════════════════════════════════════════

  /**
   * Get all workflows
   */
  async getWorkflows(): Promise<ApiResponse<Workflow[]>> {
    try {
      const response = await workflowsApi.list();
      return {
        data: response.items,
        status: 200,
        message: 'Workflows retrieved successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Get workflow by ID
   */
  async getWorkflowById(id: string): Promise<ApiResponse<Workflow>> {
    try {
      const workflow = await workflowsApi.get(id);
      return {
        data: workflow,
        status: 200,
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Create a workflow
   */
  async createWorkflow(data: CreateWorkflowDto): Promise<ApiResponse<Workflow>> {
    try {
      const workflow = await workflowsApi.create(data);
      return {
        data: workflow,
        status: 201,
        message: 'Workflow created successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Update a workflow
   */
  async updateWorkflow(id: string, data: UpdateWorkflowDto): Promise<ApiResponse<Workflow>> {
    try {
      const workflow = await workflowsApi.update(id, data);
      return {
        data: workflow,
        status: 200,
        message: 'Workflow updated successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Delete a workflow
   */
  async deleteWorkflow(id: string): Promise<ApiResponse<{ success: boolean }>> {
    try {
      await workflowsApi.delete(id);
      return {
        data: { success: true },
        status: 200,
        message: 'Workflow deleted successfully',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Execute a workflow
   */
  async executeWorkflow(id: string, input?: Record<string, unknown>): Promise<ApiResponse<{ executionId: string }>> {
    try {
      const result = await workflowsApi.execute(id, input);
      const executionId = (result as { executionId?: string; id?: string }).executionId
        ?? (result as { id?: string }).id
        ?? '';
      return {
        data: { executionId },
        status: 200,
        message: 'Workflow execution started',
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Get workflow executions
   */
  async getWorkflowExecutions(
    workflowId: string,
    skip = 0,
    limit = 10
  ): Promise<ApiResponse<PaginatedResponse<Execution>>> {
    try {
      const response = await workflowsApi.getExecutions(workflowId, { page: Math.floor(skip / limit) + 1, pageSize: limit });
      return {
        data: {
          items: response.items,
          total: response.total,
          page: response.page,
          pageSize: response.pageSize,
          hasMore: response.hasMore,
        },
        status: 200,
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Get execution by ID
   */
  async getExecutionById(id: string): Promise<ApiResponse<Execution>> {
    void id;
    return this.unsupportedOperation('Execution-by-ID lookup is not exposed by the current Data Cloud launcher API.');
  }

  /**
   * Search workflows
   */
  async searchWorkflows(query: string): Promise<ApiResponse<Workflow[]>> {
    try {
      const response = await workflowsApi.list({ search: query });
      return {
        data: response.items,
        status: 200,
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  // ═══════════════════════════════════════════════════════════════
  // Validation API
  // ═══════════════════════════════════════════════════════════════

  /**
   * Validate entity data against collection schema
   */
  async validateEntity(
    collectionId: string,
    data: Record<string, unknown>
  ): Promise<ApiResponse<EntityValidationResponse>> {
    try {
      const rawResult = await apiClient.post<EntityValidationResponse>(
        `/entities/${collectionId}/validate`,
        { data },
        { params: { tenantId: this.tenantId } }
      );
      const result = EntityValidationResponseSchema.parse(rawResult);
      return {
        data: result,
        status: 200,
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Suggest schema from sample data
   */
  async suggestSchema(
    data: Record<string, unknown>[]
  ): Promise<ApiResponse<{ fields: Array<{ name: string; type: string }> }>> {
    void data;
    return this.unsupportedOperation('Collection-agnostic schema suggestion is not exposed by the current Data Cloud launcher API.');
  }

  // ═══════════════════════════════════════════════════════════════
  // Search API
  // ═══════════════════════════════════════════════════════════════

  /**
   * Full-text search across entities
   */
  async search(
    query: string,
    options?: { collectionId?: string; limit?: number; offset?: number }
  ): Promise<ApiResponse<SearchResult[]>> {
    if (!options?.collectionId) {
      return this.unsupportedOperation('Cross-collection search is not exposed by the current Data Cloud launcher API. Provide a collectionId to use canonical entity search.');
    }

    try {
      const params: Record<string, unknown> = {
        q: query,
        tenantId: this.tenantId,
      };
      if (options.limit) params.limit = options.limit;
      if (options.offset) params.offset = options.offset;

      const rawResults = await apiClient.get<SearchResult[]>(`/entities/${options.collectionId}/search`, { params });
      const results = rawResults.map((result) => SearchResultSchema.parse(result));
      return {
        data: results,
        status: 200,
      };
    } catch (error) {
      throw this.handleError(error);
    }
  }

  // ═══════════════════════════════════════════════════════════════
  // Error Handling
  // ═══════════════════════════════════════════════════════════════

  private handleError(error: unknown): Error {
    if (error instanceof Error) {
      return error;
    }
    return new Error(String(error));
  }
}

/**
 * Singleton instance of the Data Cloud API client
 */
export const dataCloudApi = new DataCloudApiClient({
  tenantId: localStorage.getItem('tenantId') || 'default',
});

export default dataCloudApi;
