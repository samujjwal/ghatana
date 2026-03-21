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

import { apiClient } from '../lib/api/client';

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
  // ==================== Installed Plugins ====================

  /**
   * Get all installed plugins
   */
  async getInstalledPlugins(): Promise<Plugin[]> {
    return apiClient.get<Plugin[]>('/plugins');
  }

  /**
   * Get plugin by ID
   */
  async getPlugin(pluginId: string): Promise<Plugin> {
    return apiClient.get<Plugin>(`/plugins/${pluginId}`);
  }

  /**
   * Enable a plugin
   */
  async enablePlugin(pluginId: string): Promise<Plugin> {
    return apiClient.post<Plugin>(`/plugins/${pluginId}/enable`);
  }

  /**
   * Disable a plugin
   */
  async disablePlugin(pluginId: string): Promise<Plugin> {
    return apiClient.post<Plugin>(`/plugins/${pluginId}/disable`);
  }

  /**
   * Update plugin configuration
   */
  async updatePluginConfiguration(
    pluginId: string,
    configuration: PluginConfiguration
  ): Promise<Plugin> {
    return apiClient.put<Plugin>(`/plugins/${pluginId}/configuration`, configuration);
  }

  /**
   * Uninstall a plugin
   */
  async uninstallPlugin(pluginId: string): Promise<void> {
    await apiClient.delete<void>(`/plugins/${pluginId}`);
  }

  /**
   * Get plugin health status
   */
  async getPluginHealth(pluginId: string): Promise<Plugin['health']> {
    return apiClient.get<Plugin['health']>(`/plugins/${pluginId}/health`);
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
    return apiClient.get<PluginMarketplaceItem[]>('/plugins/marketplace', { params });
  }

  /**
   * Get marketplace plugin details
   */
  async getMarketplacePlugin(pluginId: string): Promise<PluginMarketplaceItem> {
    return apiClient.get<PluginMarketplaceItem>(`/plugins/marketplace/${pluginId}`);
  }

  /**
   * Install a plugin from marketplace
   */
  async installPlugin(request: PluginInstallRequest): Promise<Plugin> {
    return apiClient.post<Plugin>('/plugins/install', request);
  }

  /**
   * Update an installed plugin
   */
  async updatePlugin(
    pluginId: string,
    request: PluginUpdateRequest
  ): Promise<Plugin> {
    return apiClient.put<Plugin>(`/plugins/${pluginId}/update`, request);
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
    return apiClient.post<Plugin, FormData>('/plugins/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  }

  // ==================== Plugin Registry ====================

  /**
   * Refresh plugin registry
   */
  async refreshRegistry(): Promise<void> {
    await apiClient.post<void>('/plugins/registry/refresh');
  }

  /**
   * Validate plugin compatibility
   */
  async validatePlugin(pluginId: string, version?: string): Promise<{
    compatible: boolean;
    issues: string[];
    warnings: string[];
  }> {
    return apiClient.post(`/plugins/${pluginId}/validate`, { version });
  }

  /**
   * Get plugin execution logs
   */
  async getPluginLogs(
    pluginId: string,
    params: { level?: string; limit?: number; since?: string } = {}
  ): Promise<import('../components/plugins/PluginLogsViewer').LogEntry[]> {
    return apiClient.get(`/plugins/${pluginId}/logs`, { params });
  }

  /**
   * Get plugin performance metrics
   */
  async getPluginPerformanceMetrics(
    pluginId: string,
    timeRange: '1h' | '24h' | '7d' | '30d' = '24h'
  ): Promise<import('../components/plugins/PluginPerformanceMetrics').PluginPerformanceMetrics> {
    return apiClient.get(`/plugins/${pluginId}/performance`, { params: { timeRange } });
  }

  /**
   * Get plugin performance history
   */
  async getPluginPerformanceHistory(
    pluginId: string,
    timeRange: '1h' | '24h' | '7d' | '30d' = '24h'
  ): Promise<Array<{ timestamp: string; executionTime: number; memory: number; requests: number; errors: number }>> {
    return apiClient.get(`/plugins/${pluginId}/performance/history`, { params: { timeRange } });
  }
}

/**
 * Default plugin service instance — always uses the real API.
 */
export const pluginService = new PluginService();

export default pluginService;
