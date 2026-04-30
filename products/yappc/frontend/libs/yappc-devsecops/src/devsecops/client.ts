/**
 * DevSecOps API Client
 * Handles all API communication with the backend server
 */

import { ApiClient } from '@ghatana/api';
import type {
  ApiResponse,
  DevSecOpsOverview,
  Item,
  ItemFilter,
  Phase,
  User,
  BulkOperationResult,
} from 'yappc-core/types/devsecops';

/**
 * DevSecOps API Client
 *
 * Provides methods for interacting with the DevSecOps API.
 * Uses real API calls to the Fastify backend server.
 *
 * @example
 * ```ts
 * const client = new DevSecOpsClient();
 * const phases = await client.getPhases();
 * const items = await client.getItems({ status: 'in-progress' });
 * await client.updateItemStatus('item-1', 'completed');
 * ```
 */
export class DevSecOpsClient {
  private readonly apiClient: ApiClient;

  /**
   * Create a new DevSecOps API client.
   * Uses relative `/api/devsecops/...` paths proxied by Vite to the backend.
   */
  constructor() {
    this.apiClient = new ApiClient();
  }

  /**
   * Fetch all phases from the API
   *
   * @returns Promise resolving to array of phase objects
   * @example
   * ```ts
   * const phases = await client.getPhases();
   * // Returns: [{ id, title, icon, status, completed }, ...]
   * ```
   */
  async getPhases(): Promise<ApiResponse<Phase[]>> {
    const overview = await this.getOverview();
    const phases = overview.data?.phases ?? [];
    return this.wrapResponse(phases, overview.metadata);
  }

  /**
   * Fetch detailed information for a specific phase
   *
   * @param phaseId - Unique identifier for the phase
   * @returns Promise resolving to phase details
   * @example
   * ```ts
   * const phase = await client.getPhase('development');
   * // Returns: { id, title, description, owner, status, progress, items }
   * ```
   */
  async getPhase(phaseId: string) {
    const response = await this.apiClient.get<unknown>(
      `/api/devsecops/phases/${encodeURIComponent(phaseId)}`,
    );
    return this.isApiResponse(response.data)
      ? response.data
      : this.wrapResponse(response.data);
  }

  /**
   * Fetch all items with optional filtering
   *
   * @param filters - Optional filter parameters (status, priority, owner, etc.)
   * @returns Promise resolving to array of items
   * @example
   * ```ts
   * const items = await client.getItems({ status: 'in-progress', priority: 'high' });
   * // Returns: [{ id, title, status, priority, owner }, ...]
   * ```
   */
  async getItems(filter?: ItemFilter): Promise<ApiResponse<Item[]>> {
    const overview = await this.getOverview();
    let items = overview.data?.items ?? [];

    if (filter?.phaseIds?.length) {
      items = items.filter((item) => filter.phaseIds?.includes(item.phaseId));
    }

    if (filter?.status?.length) {
      items = items.filter((item) => filter.status?.includes(item.status));
    }

    if (filter?.priority?.length) {
      items = items.filter((item) => filter.priority?.includes(item.priority));
    }

    if (filter?.tags?.length) {
      items = items.filter((item) =>
        item.tags?.some((tag) => filter.tags?.includes(tag))
      );
    }

    if (filter?.search) {
      const search = filter.search.toLowerCase();
      items = items.filter(
        (item) =>
          item.title.toLowerCase().includes(search) ||
          item.description?.toLowerCase().includes(search)
      );
    }

    return this.wrapResponse(items, overview.metadata);
  }

  /**
   * Fetch all distinct users derived from item ownership.
   *
   * This avoids introducing a separate /users endpoint for the mock flow
   * while still providing stable user identities for the canvas UI.
   */
  async getUsers(): Promise<ApiResponse<User[]>> {
    const overview = await this.getOverview();
    const items = overview.data?.items ?? [];

    const usersById = new Map<string, User>();
    for (const item of items) {
      for (const owner of item.owners ?? []) {
        if (!usersById.has(owner.id)) {
          usersById.set(owner.id, owner);
        }
      }
    }

    const users = Array.from(usersById.values());
    return this.wrapResponse(users, overview.metadata);
  }

  /**
   * Fetch the current user.
   *
   * For the mock implementation we simply pick the first derived user or
   * fall back to a synthetic DevSecOps user.
   */
  async getCurrentUser(): Promise<ApiResponse<User>> {
    const usersResponse = await this.getUsers();
    const [first] = usersResponse.data;

    const user: User =
      first ??
      ({
        id: 'devsecops-user',
        name: 'DevSecOps User',
        email: 'devsecops@example.com',
        role: 'Developer',
        teams: [],
      } as User);

    return this.wrapResponse(user, usersResponse.metadata);
  }

  /**
   * Fetch the DevSecOps overview payload (phases, items, KPIs, activity, persona dashboards).
   *
   * This is the canonical entry point for consumers that need multiple
   * overview slices in a single round‑trip.
   */
  async getOverview(): Promise<ApiResponse<DevSecOpsOverview>> {
    return this.fetchOverview();
  }

  /**
   * Internal helper to fetch DevSecOps overview via the browser API.
   *
   * Uses the relative /api/devsecops/overview endpoint so MSW can
   * intercept requests in development.
   */
  private async fetchOverview(): Promise<ApiResponse<DevSecOpsOverview>> {
    const response = await this.apiClient.get<unknown>('/api/devsecops/overview');

    if (this.isApiResponse<DevSecOpsOverview>(response.data)) {
      return response.data;
    }

    // Backwards compatibility: handle legacy unwrapped payload
    return this.wrapResponse(response.data as DevSecOpsOverview);
  }

  /**
   * Wrap raw data in an ApiResponse, optionally reusing server metadata.
   */
  private wrapResponse<T>(
    data: T,
    metadata?: ApiResponse<unknown>['metadata']
  ): ApiResponse<T> {
    return {
      data,
      success: true,
      metadata: metadata ?? {
        timestamp: new Date().toISOString(),
        requestId: Math.random().toString(36).slice(2),
      },
    };
  }

  /**
   *
   */
  private isApiResponse<T>(value: unknown): value is ApiResponse<T> {
    return (
      !!value &&
      typeof value === 'object' &&
      'data' in value &&
      'success' in value
    );
  }

  /**
   * Fetch detailed information for a specific item
   *
   * @param itemId - Unique identifier for the item
   * @returns Promise resolving to item details
   * @example
   * ```ts
   * const item = await client.getItem('item-123');
   * // Returns: { id, title, description, status, artifacts, securityScan }
   * ```
   */
  async getItem(itemId: string) {
    const response = await this.apiClient.get<unknown>(
      `/api/devsecops/items/${encodeURIComponent(itemId)}`,
    );
    return this.isApiResponse<Item>(response.data)
      ? response.data
      : this.wrapResponse(response.data as Item);
  }

  /**
   * Fetch all available reports
   *
   * @returns Promise resolving to array of report summaries
   * @example
   * ```ts
   * const reports = await client.getReports();
   * // Returns: [{ id, title, description }, ...]
   * ```
   */
  async getReports() {
    const response = await this.apiClient.get<unknown>('/api/devsecops/reports');
    return this.isApiResponse(response.data)
      ? response.data
      : this.wrapResponse(response.data);
  }

  /**
   * Fetch detailed information for a specific report
   *
   * @param reportId - Unique identifier for the report
   * @returns Promise resolving to report details with KPIs and risks
   * @example
   * ```ts
   * const report = await client.getReport('executive');
   * // Returns: { id, title, generatedAt, data: { kpis, risks } }
   * ```
   */
  async getReport(reportId: string) {
    const response = await this.apiClient.get<unknown>(
      `/api/devsecops/reports/${encodeURIComponent(reportId)}`,
    );
    return this.isApiResponse(response.data)
      ? response.data
      : this.wrapResponse(response.data);
  }

  /**
   * Update the status of an item
   *
   * @param itemId - Unique identifier for the item to update
   * @param status - New status value (e.g., 'todo', 'in-progress', 'completed', 'blocked')
   * @returns Promise resolving to success response
   * @example
   * ```ts
   * const result = await client.updateItemStatus('item-123', 'completed');
   * // Returns: ApiResponse<Item>
   * ```
   */
  async updateItemStatus(
    itemId: string,
    status: Item['status']
  ): Promise<ApiResponse<Item>> {
    return this.updateItem(itemId, { status });
  }

  /**
   * Create a new DevSecOps item
   */
  async createItem(payload: Partial<Item>): Promise<ApiResponse<Item>> {
    const response = await this.apiClient.post<unknown>('/api/devsecops/items', { body: payload });
    return this.isApiResponse<Item>(response.data)
      ? response.data
      : this.wrapResponse(response.data as Item);
  }

  /**
   * Update an existing DevSecOps item
   */
  async updateItem(
    itemId: string,
    data: Partial<Item>
  ): Promise<ApiResponse<Item>> {
    const response = await this.apiClient.patch<unknown>(
      `/api/devsecops/items/${encodeURIComponent(itemId)}`,
      { body: data },
    );
    return this.isApiResponse<Item>(response.data)
      ? response.data
      : this.wrapResponse(response.data as Item);
  }

  /**
   * Bulk update multiple DevSecOps items.
   */
  async bulkUpdateItems(
    itemIds: string[],
    data: Partial<Item>
  ): Promise<ApiResponse<BulkOperationResult>> {
    const response = await this.apiClient.patch<unknown>('/api/devsecops/items/bulk', {
      body: { itemIds, data },
    });
    return this.isApiResponse<BulkOperationResult>(response.data)
      ? response.data
      : this.wrapResponse(response.data as BulkOperationResult);
  }

  /**
   * Delete a DevSecOps item.
   */
  async deleteItem(itemId: string): Promise<ApiResponse<boolean>> {
    const response = await this.apiClient.delete<unknown>(
      `/api/devsecops/items/${encodeURIComponent(itemId)}`,
    );
    return this.isApiResponse<boolean>(response.data)
      ? response.data
      : this.wrapResponse(response.data as boolean);
  }
}

// Export singleton instance
export const devsecopsClient = new DevSecOpsClient();
