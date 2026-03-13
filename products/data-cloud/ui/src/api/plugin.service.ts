/**
 * Plugin API Service
 *
 * Provides API client for plugin management operations.
 * Handles plugin discovery, installation, configuration, and lifecycle.
 *
 * @doc.type service
 * @doc.purpose Plugin management API client
 * @doc.layer frontend
 */

import axios, { AxiosInstance } from 'axios';

export type PluginStatus = 'active' | 'inactive' | 'error' | 'installing' | 'uninstalling';
export type PluginCategory = 'connector' | 'transformer' | 'quality' | 'governance' | 'visualization' | 'integration' | 'ai';

export interface PluginMetadata {
  id: string;
  name: string;
  version: string;
  author: string;
  description: string;
  category: PluginCategory;
  icon?: string;
  homepage?: string;
  documentation?: string;
  license: string;
  tags: string[];
}

export interface PluginCapability {
  id: string;
  name: string;
  description: string;
  type: 'source' | 'sink' | 'processor' | 'function' | 'ui';
}

export interface PluginConfiguration {
  [key: string]: unknown;
}

export interface Plugin {
  id: string;
  metadata: PluginMetadata;
  status: PluginStatus;
  installedAt: string;
  updatedAt?: string;
  capabilities: PluginCapability[];
  configuration?: PluginConfiguration;
  health?: {
    status: 'healthy' | 'degraded' | 'unhealthy';
    lastCheck: string;
    message?: string;
  };
  stats?: {
    usageCount: number;
    errorCount: number;
    lastUsed?: string;
    averageExecutionTime?: number;
  };
}

export interface PluginMarketplaceItem {
  id: string;
  metadata: PluginMetadata;
  downloads: number;
  rating: number;
  reviewCount: number;
  isOfficial: boolean;
  isInstalled: boolean;
  latestVersion: string;
  installedVersion?: string;
  updateAvailable: boolean;
}

export interface PluginInstallRequest {
  pluginId: string;
  version?: string;
  configuration?: PluginConfiguration;
}

export interface PluginUpdateRequest {
  version?: string;
  configuration?: PluginConfiguration;
}

/**
 * Plugin API Client
 */
export class PluginService {
  private client: AxiosInstance;

  constructor(baseURL: string = '/api') {
    this.client = axios.create({
      baseURL,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  // ==================== Installed Plugins ====================

  /**
   * Get all installed plugins
   */
  async getInstalledPlugins(): Promise<Plugin[]> {
    const response = await this.client.get<Plugin[]>('/plugins');
    return response.data;
  }

  /**
   * Get plugin by ID
   */
  async getPlugin(pluginId: string): Promise<Plugin> {
    const response = await this.client.get<Plugin>(`/plugins/${pluginId}`);
    return response.data;
  }

  /**
   * Enable a plugin
   */
  async enablePlugin(pluginId: string): Promise<Plugin> {
    const response = await this.client.post<Plugin>(`/plugins/${pluginId}/enable`);
    return response.data;
  }

  /**
   * Disable a plugin
   */
  async disablePlugin(pluginId: string): Promise<Plugin> {
    const response = await this.client.post<Plugin>(`/plugins/${pluginId}/disable`);
    return response.data;
  }

  /**
   * Update plugin configuration
   */
  async updatePluginConfiguration(
    pluginId: string,
    configuration: PluginConfiguration
  ): Promise<Plugin> {
    const response = await this.client.put<Plugin>(
      `/plugins/${pluginId}/configuration`,
      configuration
    );
    return response.data;
  }

  /**
   * Uninstall a plugin
   */
  async uninstallPlugin(pluginId: string): Promise<void> {
    await this.client.delete(`/plugins/${pluginId}`);
  }

  /**
   * Get plugin health status
   */
  async getPluginHealth(pluginId: string): Promise<Plugin['health']> {
    const response = await this.client.get<Plugin['health']>(
      `/plugins/${pluginId}/health`
    );
    return response.data;
  }

  // ==================== Marketplace ====================

  /**
   * Browse marketplace plugins
   */
  async browseMarketplace(params?: {
    category?: PluginCategory;
    search?: string;
    official?: boolean;
  }): Promise<PluginMarketplaceItem[]> {
    const response = await this.client.get<PluginMarketplaceItem[]>(
      '/plugins/marketplace',
      { params }
    );
    return response.data;
  }

  /**
   * Get marketplace plugin details
   */
  async getMarketplacePlugin(pluginId: string): Promise<PluginMarketplaceItem> {
    const response = await this.client.get<PluginMarketplaceItem>(
      `/plugins/marketplace/${pluginId}`
    );
    return response.data;
  }

  /**
   * Install a plugin from marketplace
   */
  async installPlugin(request: PluginInstallRequest): Promise<Plugin> {
    const response = await this.client.post<Plugin>('/plugins/install', request);
    return response.data;
  }

  /**
   * Update an installed plugin
   */
  async updatePlugin(
    pluginId: string,
    request: PluginUpdateRequest
  ): Promise<Plugin> {
    const response = await this.client.put<Plugin>(
      `/plugins/${pluginId}/update`,
      request
    );
    return response.data;
  }

  // ==================== Upload Custom Plugin ====================

  /**
   * Upload a custom plugin
   */
  async uploadPlugin(file: File, configuration?: PluginConfiguration): Promise<Plugin> {
    const formData = new FormData();
    formData.append('plugin', file);
    if (configuration) {
      formData.append('configuration', JSON.stringify(configuration));
    }

    const response = await this.client.post<Plugin>('/plugins/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  }

  // ==================== Plugin Registry ====================

  /**
   * Refresh plugin registry
   */
  async refreshRegistry(): Promise<void> {
    await this.client.post('/plugins/registry/refresh');
  }

  /**
   * Validate plugin compatibility
   */
  async validatePlugin(pluginId: string, version?: string): Promise<{
    compatible: boolean;
    issues: string[];
    warnings: string[];
  }> {
    const response = await this.client.post(`/plugins/${pluginId}/validate`, {
      version,
    });
    return response.data;
  }

  /**
   * Get plugin execution logs
   */
  async getPluginLogs(
    pluginId: string,
    params: { level?: string; limit?: number; since?: string } = {}
  ): Promise<import('../components/plugins/PluginLogsViewer').LogEntry[]> {
    const response = await this.client.get(
      `/plugins/${pluginId}/logs`,
      { params }
    );
    return response.data;
  }

  /**
   * Get plugin performance metrics
   */
  async getPluginPerformanceMetrics(
    pluginId: string,
    timeRange: '1h' | '24h' | '7d' | '30d' = '24h'
  ): Promise<import('../components/plugins/PluginPerformanceMetrics').PluginPerformanceMetrics> {
    const response = await this.client.get(
      `/plugins/${pluginId}/performance`,
      { params: { timeRange } }
    );
    return response.data;
  }

  /**
   * Get plugin performance history
   */
  async getPluginPerformanceHistory(
    pluginId: string,
    timeRange: '1h' | '24h' | '7d' | '30d' = '24h'
  ): Promise<Array<{ timestamp: string; executionTime: number; memory: number; requests: number; errors: number }>> {
    const response = await this.client.get(
      `/plugins/${pluginId}/performance/history`,
      { params: { timeRange } }
    );
    return response.data;
  }
}

/**
 * Default plugin service instance — always uses the real API.
 */
export const pluginService = new PluginService(
  import.meta.env.VITE_DC_API_URL ?? '/api',
);

export default pluginService;
