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

// ============================================================================
// Schemas
// ============================================================================

const CollectionSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().optional(),
  schema: z.record(z.any()),
  tags: z.array(z.string()).optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
  createdBy: z.string(),
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
  metadata: z.record(z.any()).optional(),
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
  rows: z.array(z.array(z.any())),
  rowCount: z.number(),
  executionTime: z.number(),
});

const FeatureSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().optional(),
  dataType: z.string(),
  version: z.string(),
  tags: z.array(z.string()).optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// ============================================================================
// Types
// ============================================================================

export type Collection = z.infer<typeof CollectionSchema>;
export type Dataset = z.infer<typeof DatasetSchema>;
export type LineageGraph = z.infer<typeof LineageGraphSchema>;
export type QueryResult = z.infer<typeof QueryResultSchema>;
export type Feature = z.infer<typeof FeatureSchema>;

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
  filters?: Record<string, any>;
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
      headers['Authorization'] = `Bearer ${this.token}`;
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
    const queryString = params ? this.buildQueryString(params) : '';
    return this.request(
      `/api/v1/collections${queryString}`,
      { method: 'GET' },
      z.array(CollectionSchema)
    );
  }

  /**
   * Get collection by ID
   * @doc.purpose Fetch single collection details
   */
  async getCollection(id: string): Promise<Collection> {
    return this.request(
      `/api/v1/collections/${id}`,
      { method: 'GET' },
      CollectionSchema
    );
  }

  /**
   * Create new collection
   * @doc.purpose Register new data collection
   */
  async createCollection(data: Omit<Collection, 'id' | 'createdAt' | 'updatedAt' | 'createdBy'>): Promise<Collection> {
    return this.request(
      '/api/v1/collections',
      {
        method: 'POST',
        body: JSON.stringify(data),
      },
      CollectionSchema
    );
  }

  /**
   * Update collection
   * @doc.purpose Modify collection metadata
   */
  async updateCollection(id: string, data: Partial<Collection>): Promise<Collection> {
    return this.request(
      `/api/v1/collections/${id}`,
      {
        method: 'PUT',
        body: JSON.stringify(data),
      },
      CollectionSchema
    );
  }

  /**
   * Delete collection
   * @doc.purpose Remove collection from catalog
   */
  async deleteCollection(id: string): Promise<void> {
    await this.request(`/api/v1/collections/${id}`, { method: 'DELETE' });
  }

  // ==========================================================================
  // Datasets API
  // ==========================================================================

  /**
   * Get datasets for collection
   * @doc.purpose Fetch datasets belonging to a collection
   */
  async getDatasets(collectionId: string, params?: SearchParams): Promise<Dataset[]> {
    const queryString = params ? this.buildQueryString(params) : '';
    return this.request(
      `/api/v1/collections/${collectionId}/datasets${queryString}`,
      { method: 'GET' },
      z.array(DatasetSchema)
    );
  }

  /**
   * Get dataset by ID
   * @doc.purpose Fetch single dataset details
   */
  async getDataset(collectionId: string, datasetId: string): Promise<Dataset> {
    return this.request(
      `/api/v1/collections/${collectionId}/datasets/${datasetId}`,
      { method: 'GET' },
      DatasetSchema
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
    return this.request(
      `/api/v1/lineage/${datasetId}?depth=${depth}`,
      { method: 'GET' },
      LineageGraphSchema
    );
  }

  /**
   * Get impact analysis
   * @doc.purpose Analyze downstream impact of changes
   */
  async getImpactAnalysis(datasetId: string): Promise<LineageGraph> {
    return this.request(
      `/api/v1/lineage/${datasetId}/impact`,
      { method: 'GET' },
      LineageGraphSchema
    );
  }

  // ==========================================================================
  // Query API
  // ==========================================================================

  /**
   * Execute SQL query
   * @doc.purpose Run SQL query against data platform
   */
  async executeQuery(sql: string, limit?: number): Promise<QueryResult> {
    return this.request(
      '/api/v1/query/execute',
      {
        method: 'POST',
        body: JSON.stringify({ sql, limit }),
      },
      QueryResultSchema
    );
  }

  /**
   * Validate SQL query
   * @doc.purpose Check SQL syntax without execution
   */
  async validateQuery(sql: string): Promise<{ valid: boolean; errors?: string[] }> {
    return this.request('/api/v1/query/validate', {
      method: 'POST',
      body: JSON.stringify({ sql }),
    });
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
      z.array(FeatureSchema)
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
      FeatureSchema
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
      FeatureSchema
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
    const queryString = this.buildQueryString({ query, ...params });
    return this.request(`/api/v1/search${queryString}`, { method: 'GET' });
  }
}

// ============================================================================
// Singleton Instance
// ============================================================================

export const apiClient = new DataCloudApiClient();
