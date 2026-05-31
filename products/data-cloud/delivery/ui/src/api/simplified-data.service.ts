/**
 * Simplified Data Cloud API Service
 *
 * Provides a unified, simplified interface for Data Cloud operations
 * with zero-cognitive-load design principles.
 */

export interface SimplifiedEntity {
  id: string;
  name: string;
  type: string;
  status: "active" | "inactive" | "error";
  lastModified: string;
  metadata?: Record<string, any>;
}

export interface SimplifiedCollection {
  id: string;
  name: string;
  description?: string;
  entityCount: number;
  status: "active" | "inactive";
  lastModified: string;
}

export interface SimplifiedDataSource {
  id: string;
  name: string;
  type: string;
  status: "connected" | "disconnected" | "error";
  lastSync?: string;
}

export interface SimplifiedPipeline {
  id: string;
  name: string;
  status: "running" | "stopped" | "error" | "completed";
  progress?: number;
  lastRun?: string;
}

export interface SimplifiedDashboard {
  totalEntities: number;
  totalCollections: number;
  totalDataSources: number;
  activePipelines: number;
  recentActivity: Array<{
    id: string;
    type: string;
    description: string;
    timestamp: string;
  }>;
  systemHealth: "healthy" | "warning" | "error";
}

export interface SearchRequest {
  query: string;
  type?: "entity" | "collection" | "pipeline" | "all";
  filters?: Record<string, any>;
}

export interface SearchResult {
  items: Array<{
    id: string;
    type: string;
    name: string;
    description?: string;
    highlight?: string;
    metadata?: Record<string, any>;
  }>;
  total: number;
  suggestions?: string[];
}

/**
 * Simplified Data Cloud Service
 *
 * This service provides a unified interface for all Data Cloud operations
 * with consistent error handling and response formats.
 */
export class SimplifiedDataService {
  private baseUrl: string;
  private tenantId: string;

  constructor(baseUrl: string, tenantId: string) {
    this.baseUrl = baseUrl.replace(/\/$/, "");
    this.tenantId = tenantId;
  }

  /**
   * Gets the main dashboard overview
   */
  async getDashboard(): Promise<SimplifiedDashboard> {
    const response = await this.fetch("/api/v1/simplified/dashboard");
    return response.json();
  }

  /**
   * Searches across all Data Cloud resources
   */
  async search(request: SearchRequest): Promise<SearchResult> {
    const params = new URLSearchParams({
      q: request.query,
      type: request.type || "all",
      ...(request.filters &&
        Object.entries(request.filters).reduce(
          (acc, [k, v]) => {
            acc[k] = String(v);
            return acc;
          },
          {} as Record<string, string>,
        )),
    });

    const response = await this.fetch(`/api/v1/simplified/search?${params}`);
    return response.json();
  }

  /**
   * Gets all collections
   */
  async getCollections(): Promise<SimplifiedCollection[]> {
    const response = await this.fetch("/api/v1/simplified/collections");
    return response.json();
  }

  /**
   * Gets entities in a collection
   */
  async getEntities(collectionId?: string): Promise<SimplifiedEntity[]> {
    const url = collectionId
      ? `/api/v1/simplified/collections/${collectionId}/entities`
      : "/api/v1/simplified/entities";

    const response = await this.fetch(url);
    return response.json();
  }

  /**
   * Gets data sources
   */
  async getDataSources(): Promise<SimplifiedDataSource[]> {
    const response = await this.fetch("/api/v1/simplified/data-sources");
    return response.json();
  }

  /**
   * Gets pipelines
   */
  async getPipelines(): Promise<SimplifiedPipeline[]> {
    const response = await this.fetch("/api/v1/simplified/pipelines");
    return response.json();
  }

  /**
   * Creates a new entity with simplified interface
   */
  async createEntity(data: {
    name: string;
    type: string;
    collectionId?: string;
    content?: any;
  }): Promise<SimplifiedEntity> {
    const response = await this.fetch("/api/v1/simplified/entities", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    return response.json();
  }

  /**
   * Updates an entity
   */
  async updateEntity(
    id: string,
    data: Partial<SimplifiedEntity>,
  ): Promise<SimplifiedEntity> {
    const response = await this.fetch(`/api/v1/simplified/entities/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    return response.json();
  }

  /**
   * Deletes an entity
   */
  async deleteEntity(id: string): Promise<void> {
    await this.fetch(`/api/v1/simplified/entities/${id}`, {
      method: "DELETE",
    });
  }

  /**
   * Creates a new collection
   */
  async createCollection(data: {
    name: string;
    description?: string;
  }): Promise<SimplifiedCollection> {
    const response = await this.fetch("/api/v1/simplified/collections", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    return response.json();
  }

  /**
   * Connects a data source
   */
  async connectDataSource(data: {
    name: string;
    type: string;
    configuration: Record<string, any>;
  }): Promise<SimplifiedDataSource> {
    const response = await this.fetch("/api/v1/simplified/data-sources", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    return response.json();
  }

  /**
   * Creates and runs a pipeline
   */
  async createPipeline(data: {
    name: string;
    source: string;
    target: string;
    transformations?: Array<{
      type: string;
      config: Record<string, any>;
    }>;
  }): Promise<SimplifiedPipeline> {
    const response = await this.fetch("/api/v1/simplified/pipelines", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    return response.json();
  }

  /**
   * Runs a pipeline
   */
  async runPipeline(id: string): Promise<SimplifiedPipeline> {
    const response = await this.fetch(
      `/api/v1/simplified/pipelines/${id}/run`,
      {
        method: "POST",
      },
    );
    return response.json();
  }

  /**
   * Gets quick actions available for the current user
   */
  async getQuickActions(): Promise<
    Array<{
      id: string;
      name: string;
      description: string;
      icon: string;
      action: string;
    }>
  > {
    const response = await this.fetch("/api/v1/simplified/quick-actions");
    return response.json();
  }

  /**
   * Executes a quick action
   */
  async executeQuickAction(
    actionId: string,
    params?: Record<string, any>,
  ): Promise<any> {
    const response = await this.fetch(
      `/api/v1/simplified/quick-actions/${actionId}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(params || {}),
      },
    );
    return response.json();
  }

  /**
   * Gets system status and health
   */
  async getSystemStatus(): Promise<{
    status: "healthy" | "warning" | "error";
    services: Record<
      string,
      {
        status: "healthy" | "warning" | "error";
        message?: string;
      }
    >;
    uptime: number;
    version: string;
  }> {
    const response = await this.fetch("/api/v1/simplified/status");
    return response.json();
  }

  /**
   * Generic fetch method with error handling
   */
  private async fetch(path: string, options?: RequestInit): Promise<Response> {
    const url = `${this.baseUrl}${path}`;

    const defaultHeaders = {
      "X-Tenant-ID": this.tenantId,
      "Content-Type": "application/json",
    };

    try {
      const response = await fetch(url, {
        ...options,
        headers: {
          ...defaultHeaders,
          ...options?.headers,
        },
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({
          message: `HTTP ${response.status}: ${response.statusText}`,
        }));
        throw new Error(
          error.message || `Request failed: ${response.statusText}`,
        );
      }

      return response;
    } catch (error) {
      console.error(`SimplifiedDataService error for ${path}:`, error);
      throw error;
    }
  }
}

/**
 * React hook for using the simplified data service
 */
export function useSimplifiedDataService(baseUrl: string, tenantId: string) {
  const service = new SimplifiedDataService(baseUrl, tenantId);

  return {
    dashboard: () => service.getDashboard(),
    search: (request: SearchRequest) => service.search(request),
    collections: () => service.getCollections(),
    entities: (collectionId?: string) => service.getEntities(collectionId),
    dataSources: () => service.getDataSources(),
    pipelines: () => service.getPipelines(),
    createEntity: (data: any) => service.createEntity(data),
    updateEntity: (id: string, data: any) => service.updateEntity(id, data),
    deleteEntity: (id: string) => service.deleteEntity(id),
    createCollection: (data: any) => service.createCollection(data),
    connectDataSource: (data: any) => service.connectDataSource(data),
    createPipeline: (data: any) => service.createPipeline(data),
    runPipeline: (id: string) => service.runPipeline(id),
    quickActions: () => service.getQuickActions(),
    executeQuickAction: (actionId: string, params?: any) =>
      service.executeQuickAction(actionId, params),
    systemStatus: () => service.getSystemStatus(),
  };
}

/**
 * Default export for convenience
 */
export default SimplifiedDataService;
