/**
 * Data Source Service - Integration with External Data Sources
 *
 * This service provides a unified interface for connecting to and
 * fetching data from various external sources like databases,
 * APIs, monitoring systems, etc.
 */

export interface DataSourceConfig {
  id: string;
  name: string;
  type: DataSourceType;
  enabled: boolean;
  config: Record<string, any>;
}

export enum DataSourceType {
  PROMETHEUS = 'prometheus',
  ELASTICSEARCH = 'elasticsearch',
  GRAPHQL = 'graphql',
  REST_API = 'rest_api',
  POSTGRESQL = 'postgresql',
  MYSQL = 'mysql',
  MONGODB = 'mongodb',
  REDIS = 'redis',
  CUSTOM = 'custom',
}

export interface QueryOptions {
  startTime?: number;
  endTime?: number;
  limit?: number;
  offset?: number;
  filters?: Record<string, any>;
}

export interface DataSourceResult {
  data: unknown;
  metadata?: {
    total?: number;
    page?: number;
    pageSize?: number;
    took?: number;
  };
}

export class DataSourceService {
  private sources: Map<string, DataSourceConfig> = new Map();
  private connections: Map<string, any> = new Map();

  constructor() {
    this.loadSources();
  }

  /**
   * Register a new data source
   */
  async registerSource(config: DataSourceConfig): Promise<void> {
    this.sources.set(config.id, config);
    await this.saveSources();

    if (config.enabled) {
      await this.connect(config.id);
    }
  }

  /**
   * Update data source configuration
   */
  async updateSource(
    id: string,
    updates: Partial<DataSourceConfig>
  ): Promise<void> {
    const source = this.sources.get(id);
    if (!source) {
      throw new Error(`Data source ${id} not found`);
    }

    const updated = { ...source, ...updates };
    this.sources.set(id, updated);
    await this.saveSources();

    // Reconnect if config changed and enabled
    if (updated.enabled && this.connections.has(id)) {
      await this.disconnect(id);
      await this.connect(id);
    }
  }

  /**
   * Remove a data source
   */
  async removeSource(id: string): Promise<void> {
    await this.disconnect(id);
    this.sources.delete(id);
    await this.saveSources();
  }

  /**
   * Get all registered data sources
   */
  getSources(): DataSourceConfig[] {
    return Array.from(this.sources.values());
  }

  /**
   * Get a specific data source
   */
  getSource(id: string): DataSourceConfig | undefined {
    return this.sources.get(id);
  }

  /**
   * Test connection to a data source
   */
  async testConnection(id: string): Promise<boolean> {
    const source = this.sources.get(id);
    if (!source) {
      throw new Error(`Data source ${id} not found`);
    }

    try {
      switch (source.type) {
        case DataSourceType.PROMETHEUS:
          return await this.testPrometheus(source.config);

        case DataSourceType.ELASTICSEARCH:
          return await this.testElasticsearch(source.config);

        case DataSourceType.GRAPHQL:
          return await this.testGraphQL(source.config);

        case DataSourceType.REST_API:
          return await this.testRestAPI(source.config);

        case DataSourceType.POSTGRESQL:
        case DataSourceType.MYSQL:
        case DataSourceType.MONGODB:
        case DataSourceType.REDIS:
          // These would require backend/Tauri commands
          return await this.testDatabase(source.type, source.config);

        default:
          console.warn(`Unsupported data source type: ${source.type}`);
          return false;
      }
    } catch (error) {
      console.error(`Failed to test connection to ${id}:`, error);
      return false;
    }
  }

  /**
   * Query data from a source
   */
  async query(
    sourceId: string,
    query: string,
    options?: QueryOptions
  ): Promise<DataSourceResult> {
    const source = this.sources.get(sourceId);
    if (!source) {
      throw new Error(`Data source ${sourceId} not found`);
    }

    if (!source.enabled) {
      throw new Error(`Data source ${sourceId} is disabled`);
    }

    try {
      switch (source.type) {
        case DataSourceType.PROMETHEUS:
          return await this.queryPrometheus(source.config, query, options);

        case DataSourceType.ELASTICSEARCH:
          return await this.queryElasticsearch(source.config, query, options);

        case DataSourceType.GRAPHQL:
          return await this.queryGraphQL(source.config, query, options);

        case DataSourceType.REST_API:
          return await this.queryRestAPI(source.config, query, options);

        case DataSourceType.POSTGRESQL:
        case DataSourceType.MYSQL:
        case DataSourceType.MONGODB:
        case DataSourceType.REDIS:
          return await this.queryDatabase(source.type, source.config, query, options);

        default:
          throw new Error(`Unsupported data source type: ${source.type}`);
      }
    } catch (error) {
      console.error(`Failed to query ${sourceId}:`, error);
      throw error;
    }
  }

  // Private methods for each data source type

  private async testPrometheus(config: any): Promise<boolean> {
    try {
      const response = await fetch(`${config.url}/api/v1/status/config`, {
        headers: this.getHeaders(config),
      });
      return response.ok;
    } catch (_error) {
      return false;
    }
  }

  private async queryPrometheus(
    config: any,
    query: string,
    options?: QueryOptions
  ): Promise<DataSourceResult> {
    const params = new URLSearchParams({
      query,
    });

    if (options?.startTime) {
      params.append('start', options.startTime.toString());
    }
    if (options?.endTime) {
      params.append('end', options.endTime.toString());
    }

    const response = await fetch(
      `${config.url}/api/v1/query_range?${params}`,
      {
        headers: this.getHeaders(config),
      }
    );

    if (!response.ok) {
      throw new Error(`Prometheus query failed: ${response.statusText}`);
    }

    const result = await response.json();
    return {
      data: result.data.result,
      metadata: {
        took: result.data.took,
      },
    };
  }

  private async testElasticsearch(config: any): Promise<boolean> {
    try {
      const response = await fetch(`${config.url}/_cluster/health`, {
        headers: this.getHeaders(config),
      });
      return response.ok;
    } catch (_error) {
      return false;
    }
  }

  private async queryElasticsearch(
    config: any,
    query: string,
    options?: QueryOptions
  ): Promise<DataSourceResult> {
    const body = JSON.parse(query);

    if (options?.limit) {
      body.size = options.limit;
    }
    if (options?.offset) {
      body.from = options.offset;
    }

    const response = await fetch(`${config.url}/${config.index}/_search`, {
      method: 'POST',
      headers: this.getHeaders(config),
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      throw new Error(`Elasticsearch query failed: ${response.statusText}`);
    }

    const result = await response.json();
    return {
      data: result.hits.hits.map((hit: any) => hit._source),
      metadata: {
        total: result.hits.total.value,
        took: result.took,
      },
    };
  }

  private async testGraphQL(config: any): Promise<boolean> {
    try {
      const response = await fetch(config.url, {
        method: 'POST',
        headers: this.getHeaders(config),
        body: JSON.stringify({ query: '{ __typename }' }),
      });
      return response.ok;
    } catch (_error) {
      return false;
    }
  }

  private async queryGraphQL(
    config: any,
    query: string,
    options?: QueryOptions
  ): Promise<DataSourceResult> {
    const response = await fetch(config.url, {
      method: 'POST',
      headers: this.getHeaders(config),
      body: JSON.stringify({
        query,
        variables: options?.filters,
      }),
    });

    if (!response.ok) {
      throw new Error(`GraphQL query failed: ${response.statusText}`);
    }

    const result = await response.json();

    if (result.errors) {
      throw new Error(`GraphQL errors: ${JSON.stringify(result.errors)}`);
    }

    return {
      data: result.data,
    };
  }

  private async testRestAPI(config: any): Promise<boolean> {
    try {
      const response = await fetch(config.url, {
        headers: this.getHeaders(config),
      });
      return response.ok;
    } catch (_error) {
      return false;
    }
  }

  private async queryRestAPI(
    config: any,
    query: string,
    options?: QueryOptions
  ): Promise<DataSourceResult> {
    // query should be the endpoint path
    const url = new URL(query, config.url);

    // Add query parameters
    if (options?.filters) {
      Object.entries(options.filters).forEach(([key, value]) => {
        url.searchParams.append(key, String(value));
      });
    }

    const response = await fetch(url.toString(), {
      headers: this.getHeaders(config),
    });

    if (!response.ok) {
      throw new Error(`REST API query failed: ${response.statusText}`);
    }

    const data = await response.json();
    return { data };
  }

  private async testDatabase(
    type: DataSourceType,
    config: any
  ): Promise<boolean> {
    // This would use Tauri commands to test database connections
    try {
      const { invoke } = await import('@tauri-apps/api/core');
      const result = await invoke<boolean>('test_database_connection', {
        type,
        config,
      });
      return result;
    } catch (error) {
      console.error('Database test not implemented:', error);
      return false;
    }
  }

  private async queryDatabase(
    type: DataSourceType,
    config: any,
    query: string,
    options?: QueryOptions
  ): Promise<DataSourceResult> {
    // This would use Tauri commands to query databases
    try {
      const { invoke } = await import('@tauri-apps/api/core');
      const result = await invoke<DataSourceResult>('query_database', {
        type,
        config,
        query,
        options,
      });
      return result;
    } catch (error) {
      console.error('Database query not implemented:', error);
      throw error;
    }
  }

  private getHeaders(config: any): Record<string, string> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    if (config.apiKey) {
      headers['Authorization'] = `Bearer ${config.apiKey}`;
    }

    if (config.headers) {
      Object.assign(headers, config.headers);
    }

    return headers;
  }

  private async connect(id: string): Promise<void> {
    const source = this.sources.get(id);
    if (!source) {
      throw new Error(`Data source ${id} not found`);
    }

    // For now, just test the connection
    // In the future, we might maintain persistent connections for some sources
    const connected = await this.testConnection(id);
    if (connected) {
      this.connections.set(id, { connected: true });
    }
  }

  private async disconnect(id: string): Promise<void> {
    this.connections.delete(id);
  }

  private async loadSources(): Promise<void> {
    try {
      const stored = localStorage.getItem('dataSources');
      if (stored) {
        const sources: DataSourceConfig[] = JSON.parse(stored);
        sources.forEach((source) => {
          this.sources.set(source.id, source);
        });
      }
    } catch (error) {
      console.error('Failed to load data sources:', error);
    }
  }

  private async saveSources(): Promise<void> {
    try {
      const sources = Array.from(this.sources.values());
      localStorage.setItem('dataSources', JSON.stringify(sources));
    } catch (error) {
      console.error('Failed to save data sources:', error);
    }
  }
}

// Singleton instance
let dataSourceServiceInstance: DataSourceService | null = null;

/**
 * Get or create the data source service instance
 */
export function getDataSourceService(): DataSourceService {
  if (!dataSourceServiceInstance) {
    dataSourceServiceInstance = new DataSourceService();
  }

  return dataSourceServiceInstance;
}

export default DataSourceService;
