/**
 * Collection Data API client.
 *
 * <p><b>Purpose</b><br>
 * Provides type-safe API client for collection data operations (CRUD).
 * Handles all data operations with error handling and retry logic.
 *
 * <p><b>Features</b><br>
 * - Create records
 * - Read records (single and list)
 * - Update records
 * - Delete records
 * - List with pagination and filtering
 * - Bulk operations
 * - Error handling and retry logic
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { collectionDataClient } from '@/lib/api/collection-data-client';
 *
 * // Create record
 * const record = await collectionDataClient.createRecord(
 *   tenantId,
 *   collectionId,
 *   data
 * );
 *
 * // List records
 * const { items, total } = await collectionDataClient.listRecords(
 *   tenantId,
 *   collectionId,
 *   { offset: 0, limit: 20 }
 * );
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Collection data CRUD API client
 * @doc.layer frontend
 * @doc.pattern API Client
 */

import axios, { AxiosInstance, AxiosError } from 'axios';

/**
 * Collection record type - flexible structure for any collection data.
 */
export interface CollectionRecord {
  id: string;
  collectionId: string;
  tenantId: string;
  data: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
  version: number;
}

/**
 * Create record request.
 */
export interface CreateRecordRequest {
  data: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

/**
 * Update record request.
 */
export interface UpdateRecordRequest {
  data: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

/**
 * List records request.
 */
export interface ListRecordsRequest {
  offset?: number;
  limit?: number;
  filter?: Record<string, unknown>;
  sort?: Array<{ field: string; order: 'asc' | 'desc' }>;
  search?: string;
}

/**
 * List records response.
 */
export interface ListRecordsResponse {
  items: CollectionRecord[];
  total: number;
  offset: number;
  limit: number;
}

/**
 * Bulk create request.
 */
export interface BulkCreateRequest {
  records: Array<Record<string, unknown>>;
}

/**
 * Bulk create response.
 */
export interface BulkCreateResponse {
  successful: CollectionRecord[];
  failed: Array<{ index: number; error: string }>;
}

/**
 * API error response.
 */
export interface ApiError {
  code: string;
  message: string;
  details?: unknown;
}

/**
 * Collection Data API Client.
 */
class CollectionDataClient {
  private client: AxiosInstance;
  private baseURL: string;
  private maxRetries = 3;
  private retryDelay = 1000;

  constructor(baseURL: string = 'http://localhost:8080/api') {
    this.baseURL = baseURL;
    this.client = axios.create({
      baseURL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Add request interceptor to include tenant ID
    this.client.interceptors.request.use((config) => {
      const tenantId = localStorage.getItem('tenantId');
      if (tenantId) {
        config.headers['X-Tenant-ID'] = tenantId;
      }
      return config;
    });

    // Add response interceptor for error handling
    this.client.interceptors.response.use(
      (response) => response,
      (error) => this.handleError(error)
    );
  }

  /**
   * Create a new record.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param request the create request
   * @returns created record
   * @throws ApiError on failure
   */
  async createRecord(
    tenantId: string,
    collectionId: string,
    request: CreateRecordRequest
  ): Promise<CollectionRecord> {
    return this.retryRequest(async () => {
      const response = await this.client.post<CollectionRecord>(
        `/tenants/${tenantId}/collections/${collectionId}/records`,
        request
      );
      return response.data;
    });
  }

  /**
   * Get a record by ID.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param recordId the record ID
   * @returns the record
   * @throws ApiError on failure
   */
  async getRecord(
    tenantId: string,
    collectionId: string,
    recordId: string
  ): Promise<CollectionRecord> {
    return this.retryRequest(async () => {
      const response = await this.client.get<CollectionRecord>(
        `/tenants/${tenantId}/collections/${collectionId}/records/${recordId}`
      );
      return response.data;
    });
  }

  /**
   * List records with pagination and filtering.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param request the list request
   * @returns list response with items and total count
   * @throws ApiError on failure
   */
  async listRecords(
    tenantId: string,
    collectionId: string,
    request: ListRecordsRequest = {}
  ): Promise<ListRecordsResponse> {
    return this.retryRequest(async () => {
      const params = new URLSearchParams();

      if (request.offset !== undefined) params.append('offset', String(request.offset));
      if (request.limit !== undefined) params.append('limit', String(request.limit));
      if (request.search) params.append('search', request.search);
      if (request.filter) params.append('filter', JSON.stringify(request.filter));
      if (request.sort) params.append('sort', JSON.stringify(request.sort));

      const response = await this.client.get<ListRecordsResponse>(
        `/tenants/${tenantId}/collections/${collectionId}/records`,
        { params: Object.fromEntries(params) }
      );
      return response.data;
    });
  }

  /**
   * Update a record.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param recordId the record ID
   * @param request the update request
   * @returns updated record
   * @throws ApiError on failure
   */
  async updateRecord(
    tenantId: string,
    collectionId: string,
    recordId: string,
    request: UpdateRecordRequest
  ): Promise<CollectionRecord> {
    return this.retryRequest(async () => {
      const response = await this.client.put<CollectionRecord>(
        `/tenants/${tenantId}/collections/${collectionId}/records/${recordId}`,
        request
      );
      return response.data;
    });
  }

  /**
   * Delete a record.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param recordId the record ID
   * @throws ApiError on failure
   */
  async deleteRecord(
    tenantId: string,
    collectionId: string,
    recordId: string
  ): Promise<void> {
    return this.retryRequest(async () => {
      await this.client.delete(
        `/tenants/${tenantId}/collections/${collectionId}/records/${recordId}`
      );
    });
  }

  /**
   * Bulk create records.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param request the bulk create request
   * @returns bulk response with successful and failed records
   * @throws ApiError on failure
   */
  async bulkCreateRecords(
    tenantId: string,
    collectionId: string,
    request: BulkCreateRequest
  ): Promise<BulkCreateResponse> {
    return this.retryRequest(async () => {
      const response = await this.client.post<BulkCreateResponse>(
        `/tenants/${tenantId}/collections/${collectionId}/records/bulk`,
        request
      );
      return response.data;
    });
  }

  /**
   * Bulk delete records.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param recordIds the record IDs to delete
   * @returns number of deleted records
   * @throws ApiError on failure
   */
  async bulkDeleteRecords(
    tenantId: string,
    collectionId: string,
    recordIds: string[]
  ): Promise<number> {
    return this.retryRequest(async () => {
      const response = await this.client.post<{ deleted: number }>(
        `/tenants/${tenantId}/collections/${collectionId}/records/bulk-delete`,
        { ids: recordIds }
      );
      return response.data.deleted;
    });
  }

  /**
   * Search records.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param query the search query
   * @param options search options
   * @returns search results
   * @throws ApiError on failure
   */
  async searchRecords(
    tenantId: string,
    collectionId: string,
    query: string,
    options: { offset?: number; limit?: number } = {}
  ): Promise<ListRecordsResponse> {
    return this.listRecords(tenantId, collectionId, {
      search: query,
      ...options,
    });
  }

  /**
   * Export records as CSV or JSON.
   *
   * @param tenantId the tenant ID
   * @param collectionId the collection ID
   * @param format export format (csv or json)
   * @param filter optional filter
   * @returns blob of exported data
   * @throws ApiError on failure
   */
  async exportRecords(
    tenantId: string,
    collectionId: string,
    format: 'csv' | 'json',
    filter?: Record<string, unknown>
  ): Promise<Blob> {
    return this.retryRequest(async () => {
      const params: Record<string, string> = { format };
      if (filter) params.filter = JSON.stringify(filter);

      const response = await this.client.get<Blob>(
        `/tenants/${tenantId}/collections/${collectionId}/records/export`,
        {
          params,
          responseType: 'blob',
        }
      );
      return response.data;
    });
  }

  /**
   * Retry logic for failed requests.
   *
   * @param fn the async function to retry
   * @returns result from function
   * @throws last error if all retries fail
   */
  private async retryRequest<T>(fn: () => Promise<T>): Promise<T> {
    let lastError: Error | undefined;

    for (let i = 0; i < this.maxRetries; i++) {
      try {
        return await fn();
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error));

        // Don't retry on client errors (4xx) except 408, 429
        if (
          axios.isAxiosError(error) &&
          error.response?.status &&
          error.response.status >= 400 &&
          error.response.status < 500 &&
          ![408, 429].includes(error.response.status)
        ) {
          throw error;
        }

        // Wait before retrying
        if (i < this.maxRetries - 1) {
          await this.delay(this.retryDelay * Math.pow(2, i));
        }
      }
    }

    throw lastError || new Error('Request failed after retries');
  }

  /**
   * Handle API errors.
   *
   * @param error the axios error
   * @returns rejected promise
   */
  private handleError(error: AxiosError): Promise<never> {
    const apiError: ApiError = {
      code: 'UNKNOWN_ERROR',
      message: 'An unknown error occurred',
    };

    if (axios.isAxiosError(error)) {
      if (error.response) {
        const data = error.response.data as Record<string, unknown>;
        apiError.code = (data.code as string) || `HTTP_${error.response.status}`;
        apiError.message = (data.message as string) || error.message;
        apiError.details = data;
      } else if (error.request) {
        apiError.code = 'NO_RESPONSE';
        apiError.message = 'No response from server';
      } else {
        apiError.code = 'REQUEST_ERROR';
        apiError.message = error.message;
      }
    }

    const error_ = new Error(apiError.message);
    (error_ as any).code = apiError.code;
    (error_ as any).details = apiError.details;

    return Promise.reject(error_);
  }

  /**
   * Delay utility.
   *
   * @param ms milliseconds to delay
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /**
   * Set tenant ID for all requests.
   *
   * @param tenantId the tenant ID
   */
  setTenantId(tenantId: string): void {
    localStorage.setItem('tenantId', tenantId);
  }

  /**
   * Set API base URL.
   *
   * @param baseURL the new base URL
   */
  setBaseURL(baseURL: string): void {
    this.baseURL = baseURL;
    this.client.defaults.baseURL = baseURL;
  }
}

// Export singleton instance
export const collectionDataClient = new CollectionDataClient();

export default collectionDataClient;

