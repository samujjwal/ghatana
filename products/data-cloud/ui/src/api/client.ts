/**
 * @fileoverview Data Cloud API Client
 * Real API client replacing mock implementation
 * 
 * @doc.type service
 * @doc.purpose HTTP client for Data Cloud backend services
 * @doc.layer infrastructure
 * @doc.pattern HTTP Client
 */

import { z } from 'zod';
import {
  AnalyticsSqlQueryResponseSchema,
  CollectionEntityListResponseSchema,
  CollectionEntitySchema,
  FeatureSchema as SharedFeatureSchema,
  type AnalyticsSqlQueryResponse,
  type CollectionEntity as BackendCollectionEntity,
  type CollectionEntityListResponse as BackendCollectionListResponse,
  type Feature as SharedFeature,
} from '../contracts/schemas';

// ============================================================================
// Schemas
// ============================================================================

const CollectionSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().optional(),
  schema: z.object({
    fields: z.array(z.record(z.string(), z.unknown())),
  }).passthrough(),
  tags: z.array(z.string()).optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
  createdBy: z.string(),
  schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document']).optional(),
  status: z.enum(['active', 'draft', 'archived', 'processing']).optional(),
  entityCount: z.number().optional(),
  isActive: z.boolean().optional(),
});

const DatasetSchema = z.object({
  id: z.string(),
  collectionId: z.string(),
  name: z.string(),
  description: z.string().optional(),
  format: z.enum(['parquet', 'csv', 'json', 'avro']),
  location: z.string(),
  size: z.number(),
  rowCount: z.number().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

const LineageNodeSchema = z.object({
  id: z.string(),
  type: z.enum(['dataset', 'transformation', 'model']),
  name: z.string(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

const LineageEdgeSchema = z.object({
  source: z.string(),
  target: z.string(),
  type: z.enum(['derives_from', 'transforms', 'uses']),
});

const LineageGraphSchema = z.object({
  nodes: z.array(LineageNodeSchema),
  edges: z.array(LineageEdgeSchema),
});

const QueryResultSchema = z.object({
  columns: z.array(z.string()),
  rows: z.array(z.array(z.unknown())),
  rowCount: z.number(),
  executionTime: z.number(),
});

// ============================================================================
// Types
// ============================================================================

export type Collection = z.infer<typeof CollectionSchema>;
export type Dataset = z.infer<typeof DatasetSchema>;
export type LineageGraph = z.infer<typeof LineageGraphSchema>;
export type QueryResult = z.infer<typeof QueryResultSchema>;
export type Feature = SharedFeature;

export interface ApiError {
  message: string;
  code?: string;
  details?: Record<string, any>;
}

export interface PaginationParams {
  page?: number;
  pageSize?: number;
  cursor?: string;
}

export interface SearchParams extends PaginationParams {
  query?: string;
  filters?: Record<string, unknown>;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

// ============================================================================
// API Client
// ============================================================================

/**
 * Data Cloud API Client
 * @doc.purpose Communicate with Data Cloud backend services
 */
export class DataCloudApiClient {
  private baseUrl: string;
  private token: string | null = null;

  constructor(baseUrl?: string) {
    this.baseUrl = baseUrl || import.meta.env.VITE_API_URL || 'http://localhost:8080';
  }

  /**
   * Set authentication token
   * @doc.purpose Configure bearer token for authenticated requests
   */
  setToken(token: string): void {
    this.token = token;
  }

  /**
   * Clear authentication token
   * @doc.purpose Remove token on logout
   */
  clearToken(): void {
    this.token = null;
  }

  private unsupportedOperation(message: string): never {
    throw new Error(message);
  }

  private mapCollectionEntity(entity: BackendCollectionEntity): Collection {
    const data = entity.data;
    return CollectionSchema.parse({
      id: entity.id,
      name: typeof data.name === 'string' ? data.name : entity.id,
      description: typeof data.description === 'string' ? data.description : '',
      schemaType: data.schemaType ?? 'entity',
      status: data.status ?? 'draft',
      isActive: data.isActive ?? data.status === 'active',
      entityCount: typeof data.entityCount === 'number' ? data.entityCount : 0,
      schema: data.schema ?? { fields: [] },
      tags: Array.isArray(data.tags) ? data.tags : [],
      createdAt: entity.createdAt ?? (typeof data.createdAt === 'string' ? data.createdAt : new Date().toISOString()),
      updatedAt: entity.updatedAt ?? (typeof data.updatedAt === 'string' ? data.updatedAt : new Date().toISOString()),
      createdBy: typeof data.createdBy === 'string' ? data.createdBy : 'unknown',
    });
  }

  private mapAnalyticsQueryResult(response: AnalyticsSqlQueryResponse): QueryResult {
    const columns = response.rows[0] ? Object.keys(response.rows[0]) : [];
    return QueryResultSchema.parse({
      columns,
      rows: response.rows.map((row) => columns.map((column) => row[column])),
      rowCount: response.rowCount,
      executionTime: response.executionTimeMs,
    });
  }

  // ==========================================================================
  // Private Helper Methods
  // ==========================================================================

  private async request<T>(
    endpoint: string,
    options: RequestInit = {},
    schema?: z.ZodType<T>
  ): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`;
    
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    if (this.token) {
      (headers as Record<string, string>)['Authorization'] = `Bearer ${this.token}`;
    }

    try {
      const response = await fetch(url, {
        ...options,
        headers,
      });

      if (!response.ok) {
        const error: ApiError = await response.json().catch(() => ({
          message: response.statusText,
          code: response.status.toString(),
        }));
        throw new Error(error.message || `HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      
      if (schema) {
        return schema.parse(data);
      }
      
      return data as T;
    } catch (error) {
      if (error instanceof Error) {
        throw error;
      }
      throw new Error('Unknown error occurred');
    }
  }

  private buildQueryString(params: Record<string, any>): string {
    const searchParams = new URLSearchParams();
    
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        searchParams.append(key, String(value));
      }
    });
    
    const queryString = searchParams.toString();
    return queryString ? `?${queryString}` : '';
  }

  // ==========================================================================
  // Collections API
  // ==========================================================================

  /**
   * Get all collections
   * @doc.purpose Fetch list of data collections
   */
  async getCollections(params?: SearchParams): Promise<Collection[]> {
    const pageSize = params?.pageSize ?? 50;
    const page = params?.page ?? 1;
    const offset = (page - 1) * pageSize;
    const queryString = this.buildQueryString({
      limit: pageSize,
      offset,
      ...(params?.query ? { search: params.query } : {}),
    });
    const response = await this.request<BackendCollectionListResponse>(
      `/api/v1/entities/dc_collections${queryString}`,
      { method: 'GET' },
      CollectionEntityListResponseSchema,
    );
    return response.entities.map((entity) => this.mapCollectionEntity(entity));
  }

  /**
   * Get collection by ID
   * @doc.purpose Fetch single collection details
   */
  async getCollection(id: string): Promise<Collection> {
    const entity = await this.request<BackendCollectionEntity>(
      `/api/v1/entities/dc_collections/${id}`,
      { method: 'GET' },
      CollectionEntitySchema,
    );
    return this.mapCollectionEntity(entity);
  }

  /**
   * Create new collection
   * @doc.purpose Register new data collection
   */
  async createCollection(data: Omit<Collection, 'id' | 'createdAt' | 'updatedAt' | 'createdBy'>): Promise<Collection> {
    const saved = await this.request<{ id: string; createdAt?: string }>(
      '/api/v1/entities/dc_collections',
      {
        method: 'POST',
        body: JSON.stringify(data),
      },
    );
    return CollectionSchema.parse({
      id: saved.id,
      ...data,
      createdAt: saved.createdAt ?? new Date().toISOString(),
      updatedAt: saved.createdAt ?? new Date().toISOString(),
      createdBy: 'unknown',
    });
  }

  /**
   * Update collection
   * @doc.purpose Modify collection metadata
   */
  async updateCollection(id: string, data: Partial<Collection>): Promise<Collection> {
    await this.request(
      '/api/v1/entities/dc_collections',
      {
        method: 'POST',
        body: JSON.stringify({ id, ...data }),
      },
    );
    return this.getCollection(id);
  }

  /**
   * Delete collection
   * @doc.purpose Remove collection from catalog
   */
  async deleteCollection(id: string): Promise<void> {
    await this.request(`/api/v1/entities/dc_collections/${id}`, { method: 'DELETE' });
  }

  // ==========================================================================
  // Datasets API
  // ==========================================================================

  /**
   * Get datasets for collection
   * @doc.purpose Fetch datasets belonging to a collection
   */
  async getDatasets(collectionId: string, params?: SearchParams): Promise<Dataset[]> {
    void collectionId;
    void params;
    return this.unsupportedOperation(
      'Collection-scoped dataset catalog routes are not exposed by the current Data Cloud launcher API.',
    );
  }

  /**
   * Get dataset by ID
   * @doc.purpose Fetch single dataset details
   */
  async getDataset(collectionId: string, datasetId: string): Promise<Dataset> {
    void collectionId;
    void datasetId;
    return this.unsupportedOperation(
      'Collection-scoped dataset detail routes are not exposed by the current Data Cloud launcher API.',
    );
  }

  // ==========================================================================
  // Lineage API
  // ==========================================================================

  /**
   * Get lineage graph for dataset
   * @doc.purpose Fetch upstream and downstream lineage
   */
  async getLineage(datasetId: string, depth: number = 3): Promise<LineageGraph> {
    void datasetId;
    void depth;
    throw new Error(
      'Lineage graph APIs are not exposed by the current Data Cloud launcher. Use the Data Explorer lineage preview instead.',
    );
  }

  /**
   * Get impact analysis
   * @doc.purpose Analyze downstream impact of changes
   */
  async getImpactAnalysis(datasetId: string): Promise<LineageGraph> {
    void datasetId;
    throw new Error('Impact analysis APIs are not exposed by the current Data Cloud launcher.');
  }

  // ==========================================================================
  // Query API
  // ==========================================================================

  /**
   * Execute SQL query
   * @doc.purpose Run SQL query against data platform
   */
  async executeQuery(sql: string, limit?: number): Promise<QueryResult> {
    const response = await this.request<AnalyticsSqlQueryResponse>(
      '/api/v1/analytics/query',
      {
        method: 'POST',
        body: JSON.stringify({ query: sql, parameters: limit != null ? { limit } : {} }),
      },
      AnalyticsSqlQueryResponseSchema,
    );
    return this.mapAnalyticsQueryResult(response);
  }

  /**
   * Validate SQL query
   * @doc.purpose Check SQL syntax without execution
   */
  async validateQuery(sql: string): Promise<{ valid: boolean; errors?: string[] }> {
    void sql;
    return this.unsupportedOperation(
      'Standalone query validation is not exposed by the current Data Cloud launcher API.',
    );
  }

  // ==========================================================================
  // Feature Store API
  // ==========================================================================

  /**
   * Get features
   * @doc.purpose Fetch ML features from feature store
   */
  async getFeatures(params?: SearchParams): Promise<Feature[]> {
    const queryString = params ? this.buildQueryString(params) : '';
    return this.request(
      `/api/v1/features${queryString}`,
      { method: 'GET' },
      z.array(SharedFeatureSchema)
    );
  }

  /**
   * Get feature by ID
   * @doc.purpose Fetch single feature details
   */
  async getFeature(id: string): Promise<Feature> {
    return this.request(
      `/api/v1/features/${id}`,
      { method: 'GET' },
      SharedFeatureSchema
    );
  }

  /**
   * Register feature
   * @doc.purpose Add new feature to feature store
   */
  async registerFeature(data: Omit<Feature, 'id' | 'createdAt' | 'updatedAt'>): Promise<Feature> {
    return this.request(
      '/api/v1/features',
      {
        method: 'POST',
        body: JSON.stringify(data),
      },
      SharedFeatureSchema
    );
  }

  // ==========================================================================
  // Search API
  // ==========================================================================

  /**
   * Global search
   * @doc.purpose Search across all catalog entities
   */
  async search(query: string, params?: SearchParams): Promise<{
    collections: Collection[];
    datasets: Dataset[];
    features: Feature[];
  }> {
    void query;
    void params;
    return this.unsupportedOperation(
      'Global cross-catalog search is not exposed by the current Data Cloud launcher API.',
    );
  }
}

// ============================================================================
// Singleton Instance
// ============================================================================

export const apiClient = new DataCloudApiClient();
