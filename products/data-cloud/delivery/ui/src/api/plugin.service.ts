/**
 * Plugin API Service
 *
 * Provides API client for bundled plugin inventory and lifecycle operations.
 * Handles the bundled plugin lifecycle exposed by the Data Cloud launcher.
 *
 * @doc.type service
 * @doc.purpose Plugin management API client
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';
import {
  PluginListResponseSchema,
  PluginViewSchema,
  type PluginListResponse as BackendPluginListResponse,
  type PluginView as BackendPluginView,
} from '../contracts/schemas';
import type { LogEntry } from '../components/plugins/PluginLogsViewer';
import type { PluginPerformanceMetrics } from '../components/plugins/PluginPerformanceMetrics';
import {
  PLUGIN_COMPATIBILITY_BOUNDARY_WARNING,
  PLUGIN_CONFIGURATION_BOUNDARY_MESSAGE,
  PLUGIN_INSTALL_BOUNDARY_MESSAGE,
  PLUGIN_MARKETPLACE_BOUNDARY_MESSAGE,
  PLUGIN_UNINSTALL_BOUNDARY_MESSAGE,
  PLUGIN_UPLOAD_BOUNDARY_MESSAGE,
} from '../lib/runtime-boundaries';

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

const DEFAULT_PLUGIN_AUTHOR = 'Ghatana Data Cloud';
const DEFAULT_PLUGIN_LICENSE = 'Bundled';

function unsupportedOperation<T>(message: string): Promise<T> {
  return Promise.reject(new Error(message));
}

function inferCategory(recordTypes: string[]): PluginCategory {
  const normalizedTypes = recordTypes.map((type) => type.toLowerCase());
  if (normalizedTypes.some((type) => type.includes('event') || type.includes('entity'))) {
    return 'connector';
  }
  if (normalizedTypes.some((type) => type.includes('audit') || type.includes('pii'))) {
    return 'governance';
  }
  if (normalizedTypes.some((type) => type.includes('metric') || type.includes('quality'))) {
    return 'quality';
  }
  if (normalizedTypes.some((type) => type.includes('vector') || type.includes('feature'))) {
    return 'ai';
  }
  return 'integration';
}

function createMetadata(plugin: BackendPluginView): PluginMetadata {
  const category = inferCategory(plugin.supportedRecordTypes);
  const supportedTypes = plugin.supportedRecordTypes.map((type) => type.toLowerCase());
  return {
    id: plugin.id,
    name: plugin.displayName,
    version: plugin.version,
    author: DEFAULT_PLUGIN_AUTHOR,
    description: `Bundled ${category} plugin with runtime support for ${plugin.supportedRecordTypes.join(', ') || 'core storage'} workloads.`,
    category,
    documentation: '/docs/platform-libraries',
    license: DEFAULT_PLUGIN_LICENSE,
    tags: supportedTypes.length > 0 ? supportedTypes : ['bundled'],
  };
}

function mapPluginView(plugin: BackendPluginView): Plugin {
  const timestamp = new Date().toISOString();
  const status: PluginStatus = plugin.status === 'enabled' ? 'active' : 'inactive';
  return {
    id: plugin.id,
    metadata: createMetadata(plugin),
    status,
    installedAt: timestamp,
    updatedAt: timestamp,
    capabilities: plugin.supportedRecordTypes.map((type) => ({
      id: `${plugin.id}:${type.toLowerCase()}`,
      name: type,
      description: `Handles ${type.toLowerCase()} records within the bundled plugin runtime.`,
      type: 'processor',
    })),
    configuration: {},
    health: {
      status: status === 'active' ? 'healthy' : 'degraded',
      lastCheck: timestamp,
      message: status === 'active'
        ? 'Bundled plugin is available to the running launcher.'
        : 'Bundled plugin has been disabled at runtime.',
    },
    stats: {
      usageCount: 0,
      errorCount: 0,
      averageExecutionTime: 0,
    },
  };
}

export class PluginService {
  async getInstalledPlugins(): Promise<Plugin[]> {
    const rawResponse = await apiClient.get<BackendPluginListResponse>('/plugins');
    const response = PluginListResponseSchema.parse(rawResponse);
    return response.plugins.map(mapPluginView);
  }

  async getPlugin(pluginId: string): Promise<Plugin> {
    const rawResponse = await apiClient.get<BackendPluginView>(`/plugins/${pluginId}`);
    const response = PluginViewSchema.parse(rawResponse);
    return mapPluginView(response);
  }

  async enablePlugin(pluginId: string): Promise<Plugin> {
    await apiClient.post<Record<string, unknown>>(`/plugins/${pluginId}/enable`);
    return this.getPlugin(pluginId);
  }

  async disablePlugin(pluginId: string): Promise<Plugin> {
    await apiClient.post<Record<string, unknown>>(`/plugins/${pluginId}/disable`);
    return this.getPlugin(pluginId);
  }

  async updatePluginConfiguration(
    pluginId: string,
    configuration: PluginConfiguration
  ): Promise<Plugin> {
    void pluginId;
    void configuration;
    return unsupportedOperation<Plugin>(PLUGIN_CONFIGURATION_BOUNDARY_MESSAGE);
  }

  async uninstallPlugin(pluginId: string): Promise<void> {
    void pluginId;
    return unsupportedOperation<void>(PLUGIN_UNINSTALL_BOUNDARY_MESSAGE);
  }

  async getPluginHealth(pluginId: string): Promise<Plugin['health']> {
    const plugin = await this.getPlugin(pluginId);
    return plugin.health;
  }

  async browseMarketplace(params?: {
    category?: PluginCategory;
    search?: string;
    official?: boolean;
  }): Promise<PluginMarketplaceItem[]> {
    void params;
    return [];
  }

  async getMarketplacePlugin(pluginId: string): Promise<PluginMarketplaceItem> {
    void pluginId;
    return unsupportedOperation<PluginMarketplaceItem>(PLUGIN_MARKETPLACE_BOUNDARY_MESSAGE);
  }

  async installPlugin(request: PluginInstallRequest): Promise<Plugin> {
    void request;
    return unsupportedOperation<Plugin>(PLUGIN_INSTALL_BOUNDARY_MESSAGE);
  }

  async updatePlugin(
    pluginId: string,
    request: PluginUpdateRequest
  ): Promise<Plugin> {
    await apiClient.post<Record<string, unknown>, PluginUpdateRequest>(`/plugins/${pluginId}/upgrade`, request);
    return this.getPlugin(pluginId);
  }

  async uploadPlugin(file: File, configuration?: PluginConfiguration): Promise<Plugin> {
    void file;
    void configuration;
    return unsupportedOperation<Plugin>(PLUGIN_UPLOAD_BOUNDARY_MESSAGE);
  }

  async refreshRegistry(): Promise<void> {
    return;
  }

  async validatePlugin(pluginId: string, version?: string): Promise<{
    compatible: boolean;
    issues: string[];
    warnings: string[];
  }> {
    void pluginId;
    void version;
    return {
      compatible: true,
      issues: [],
      warnings: [PLUGIN_COMPATIBILITY_BOUNDARY_WARNING],
    };
  }

  async getPluginLogs(
    pluginId: string,
    params: { level?: string; limit?: number; since?: string } = {}
  ): Promise<LogEntry[]> {
    const plugin = await this.getPlugin(pluginId);
    const message = plugin.status === 'active'
      ? 'Bundled plugin is enabled. Detailed runtime logs are not exposed over HTTP.'
      : 'Bundled plugin is disabled. Re-enable it to resume traffic.';
    const entries: LogEntry[] = [
      {
        timestamp: new Date().toISOString(),
        level: plugin.status === 'active' ? 'INFO' : 'WARN',
        message,
        source: plugin.id,
        context: {
          requestedLevel: params.level ?? 'INFO',
        },
      },
    ];
    return entries.slice(0, params.limit ?? 1);
  }

  async getPluginPerformanceMetrics(
    pluginId: string,
    timeRange: '1h' | '24h' | '7d' | '30d' = '24h'
  ): Promise<PluginPerformanceMetrics> {
    void timeRange;
    const plugin = await this.getPlugin(pluginId);
    return {
      pluginId,
      timestamp: new Date().toISOString(),
      executionTime: {
        avg: plugin.stats?.averageExecutionTime ?? 0,
        min: 0,
        max: plugin.stats?.averageExecutionTime ?? 0,
        p50: plugin.stats?.averageExecutionTime ?? 0,
        p95: plugin.stats?.averageExecutionTime ?? 0,
        p99: plugin.stats?.averageExecutionTime ?? 0,
      },
      memory: {
        used: 0,
        peak: 1,
        average: 0,
      },
      throughput: {
        requestsPerSecond: 0,
        recordsProcessed: 0,
        bytesProcessed: 0,
      },
      errors: {
        count: plugin.stats?.errorCount ?? 0,
        rate: 0,
        types: {},
      },
      cpu: {
        usage: 0,
        cores: 0,
      },
    };
  }

  async getPluginPerformanceHistory(
    pluginId: string,
    timeRange: '1h' | '24h' | '7d' | '30d' = '24h'
  ): Promise<Array<{ timestamp: string; executionTime: number; memory: number; requests: number; errors: number }>> {
    const metrics = await this.getPluginPerformanceMetrics(pluginId, timeRange);
    return [
      {
        timestamp: metrics.timestamp,
        executionTime: metrics.executionTime.avg,
        memory: metrics.memory.used,
        requests: metrics.throughput.requestsPerSecond,
        errors: metrics.errors.count,
      },
    ];
  }
}

export const pluginService = new PluginService();

export default pluginService;
