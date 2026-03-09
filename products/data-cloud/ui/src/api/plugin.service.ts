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
}

/**
 * Mock Plugin Service for Development
 */
class MockPluginService extends PluginService {
  private mockInstalledPlugins: Plugin[] = [
    {
      id: 'postgres-connector',
      metadata: {
        id: 'postgres-connector',
        name: 'PostgreSQL Connector',
        version: '2.1.0',
        author: 'Ghatana Team',
        description: 'Connect to PostgreSQL databases and sync data in real-time',
        category: 'connector',
        icon: '🐘',
        homepage: 'https://plugins.ghatana.io/postgres',
        documentation: 'https://docs.ghatana.io/plugins/postgres',
        license: 'Apache 2.0',
        tags: ['database', 'sql', 'postgres', 'connector'],
      },
      status: 'active',
      installedAt: '2024-11-15T10:00:00Z',
      updatedAt: '2024-12-01T15:30:00Z',
      capabilities: [
        {
          id: 'postgres-source',
          name: 'PostgreSQL Source',
          description: 'Read data from PostgreSQL tables',
          type: 'source',
        },
        {
          id: 'postgres-sink',
          name: 'PostgreSQL Sink',
          description: 'Write data to PostgreSQL tables',
          type: 'sink',
        },
      ],
      configuration: {
        host: 'localhost',
        port: 5432,
        database: 'datacloud',
        ssl: true,
      },
      health: {
        status: 'healthy',
        lastCheck: new Date().toISOString(),
        message: 'All connections active',
      },
      stats: {
        usageCount: 1247,
        errorCount: 3,
        lastUsed: '2024-12-16T18:45:00Z',
        averageExecutionTime: 45,
      },
    },
    {
      id: 'snowflake-connector',
      metadata: {
        id: 'snowflake-connector',
        name: 'Snowflake Connector',
        version: '1.8.2',
        author: 'Ghatana Team',
        description: 'Enterprise data warehouse integration for Snowflake',
        category: 'connector',
        icon: '❄️',
        homepage: 'https://plugins.ghatana.io/snowflake',
        documentation: 'https://docs.ghatana.io/plugins/snowflake',
        license: 'Apache 2.0',
        tags: ['warehouse', 'cloud', 'snowflake', 'analytics'],
      },
      status: 'active',
      installedAt: '2024-10-20T08:00:00Z',
      capabilities: [
        {
          id: 'snowflake-source',
          name: 'Snowflake Source',
          description: 'Read from Snowflake tables and views',
          type: 'source',
        },
      ],
      health: {
        status: 'healthy',
        lastCheck: new Date().toISOString(),
      },
      stats: {
        usageCount: 856,
        errorCount: 1,
        lastUsed: '2024-12-16T16:30:00Z',
        averageExecutionTime: 120,
      },
    },
    {
      id: 'data-quality-checker',
      metadata: {
        id: 'data-quality-checker',
        name: 'Data Quality Validator',
        version: '3.0.1',
        author: 'Quality Team',
        description: 'AI-powered data quality checks and validation rules',
        category: 'quality',
        icon: '✓',
        homepage: 'https://plugins.ghatana.io/quality',
        license: 'MIT',
        tags: ['quality', 'validation', 'ai', 'governance'],
      },
      status: 'active',
      installedAt: '2024-09-10T14:00:00Z',
      updatedAt: '2024-11-28T09:15:00Z',
      capabilities: [
        {
          id: 'quality-processor',
          name: 'Quality Processor',
          description: 'Validate data against quality rules',
          type: 'processor',
        },
      ],
      health: {
        status: 'healthy',
        lastCheck: new Date().toISOString(),
      },
      stats: {
        usageCount: 2341,
        errorCount: 12,
        lastUsed: '2024-12-17T07:00:00Z',
        averageExecutionTime: 35,
      },
    },
    {
      id: 'pii-detector',
      metadata: {
        id: 'pii-detector',
        name: 'PII Detection & Masking',
        version: '2.5.0',
        author: 'Security Team',
        description: 'Automatically detect and mask personally identifiable information',
        category: 'governance',
        icon: '🔒',
        homepage: 'https://plugins.ghatana.io/pii',
        license: 'Apache 2.0',
        tags: ['security', 'pii', 'gdpr', 'compliance'],
      },
      status: 'inactive',
      installedAt: '2024-08-05T11:00:00Z',
      capabilities: [
        {
          id: 'pii-processor',
          name: 'PII Processor',
          description: 'Detect and mask sensitive data',
          type: 'processor',
        },
      ],
      health: {
        status: 'degraded',
        lastCheck: new Date().toISOString(),
        message: 'Plugin disabled by user',
      },
      stats: {
        usageCount: 0,
        errorCount: 0,
        averageExecutionTime: 0,
      },
    },
  ];

  private mockMarketplacePlugins: PluginMarketplaceItem[] = [
    {
      id: 'mongodb-connector',
      metadata: {
        id: 'mongodb-connector',
        name: 'MongoDB Connector',
        version: '1.9.0',
        author: 'Ghatana Team',
        description: 'NoSQL database connector for MongoDB with change stream support',
        category: 'connector',
        icon: '🍃',
        homepage: 'https://plugins.ghatana.io/mongodb',
        license: 'Apache 2.0',
        tags: ['nosql', 'mongodb', 'document', 'realtime'],
      },
      downloads: 15420,
      rating: 4.8,
      reviewCount: 142,
      isOfficial: true,
      isInstalled: false,
      latestVersion: '1.9.0',
      updateAvailable: false,
    },
    {
      id: 'kafka-connector',
      metadata: {
        id: 'kafka-connector',
        name: 'Apache Kafka Connector',
        version: '2.3.1',
        author: 'Ghatana Team',
        description: 'Stream data from Kafka topics in real-time',
        category: 'connector',
        icon: '📨',
        homepage: 'https://plugins.ghatana.io/kafka',
        license: 'Apache 2.0',
        tags: ['streaming', 'kafka', 'events', 'realtime'],
      },
      downloads: 23105,
      rating: 4.9,
      reviewCount: 287,
      isOfficial: true,
      isInstalled: false,
      latestVersion: '2.3.1',
      updateAvailable: false,
    },
    {
      id: 'postgres-connector',
      metadata: {
        id: 'postgres-connector',
        name: 'PostgreSQL Connector',
        version: '2.2.0',
        author: 'Ghatana Team',
        description: 'Connect to PostgreSQL databases and sync data in real-time',
        category: 'connector',
        icon: '🐘',
        homepage: 'https://plugins.ghatana.io/postgres',
        license: 'Apache 2.0',
        tags: ['database', 'sql', 'postgres', 'connector'],
      },
      downloads: 31247,
      rating: 4.9,
      reviewCount: 412,
      isOfficial: true,
      isInstalled: true,
      latestVersion: '2.2.0',
      installedVersion: '2.1.0',
      updateAvailable: true,
    },
  ];

  async getInstalledPlugins(): Promise<Plugin[]> {
    // Simulate network delay
    await new Promise((resolve) => setTimeout(resolve, 300));
    return [...this.mockInstalledPlugins];
  }

  async getPlugin(pluginId: string): Promise<Plugin> {
    await new Promise((resolve) => setTimeout(resolve, 200));
    const plugin = this.mockInstalledPlugins.find((p) => p.id === pluginId);
    if (!plugin) {
      throw new Error(`Plugin ${pluginId} not found`);
    }
    return plugin;
  }

  async browseMarketplace(params?: {
    category?: PluginCategory;
    search?: string;
    official?: boolean;
  }): Promise<PluginMarketplaceItem[]> {
    await new Promise((resolve) => setTimeout(resolve, 400));
    let results = [...this.mockMarketplacePlugins];

    if (params?.category) {
      results = results.filter((p) => p.metadata.category === params.category);
    }

    if (params?.search) {
      const query = params.search.toLowerCase();
      results = results.filter(
        (p) =>
          p.metadata.name.toLowerCase().includes(query) ||
          p.metadata.description.toLowerCase().includes(query) ||
          p.metadata.tags.some((tag) => tag.toLowerCase().includes(query))
      );
    }

    if (params?.official !== undefined) {
      results = results.filter((p) => p.isOfficial === params.official);
    }

    return results;
  }

  async enablePlugin(pluginId: string): Promise<Plugin> {
    await new Promise((resolve) => setTimeout(resolve, 500));
    const plugin = this.mockInstalledPlugins.find((p) => p.id === pluginId);
    if (!plugin) {
      throw new Error(`Plugin ${pluginId} not found`);
    }
    plugin.status = 'active';
    if (plugin.health) {
      plugin.health.status = 'healthy';
      plugin.health.message = 'Plugin enabled successfully';
    }
    return plugin;
  }

  async disablePlugin(pluginId: string): Promise<Plugin> {
    await new Promise((resolve) => setTimeout(resolve, 500));
    const plugin = this.mockInstalledPlugins.find((p) => p.id === pluginId);
    if (!plugin) {
      throw new Error(`Plugin ${pluginId} not found`);
    }
    plugin.status = 'inactive';
    if (plugin.health) {
      plugin.health.status = 'degraded';
      plugin.health.message = 'Plugin disabled by user';
    }
    return plugin;
  }

  async updatePluginConfiguration(
    pluginId: string,
    configuration: PluginConfiguration
  ): Promise<Plugin> {
    await new Promise((resolve) => setTimeout(resolve, 600));
    const plugin = this.mockInstalledPlugins.find((p) => p.id === pluginId);
    if (!plugin) {
      throw new Error(`Plugin ${pluginId} not found`);
    }
    plugin.configuration = configuration;
    plugin.updatedAt = new Date().toISOString();
    return plugin;
  }
}

/**
 * Default plugin service instance
 * Uses mock data in development mode
 */
export const pluginService =
  import.meta.env.MODE === 'development'
    ? new MockPluginService()
    : new PluginService();

export default pluginService;
