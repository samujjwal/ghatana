/**
 * Data Cloud Client Factory
 *
 * Creates the appropriate Data Cloud client based on the current mode:
 * - Library mode: In-process client using direct library calls
 * - Service mode: HTTP/gRPC client connecting to external service
 *
 * This factory abstracts away mode switching from consumers.
 */

import {
  DataCloudConfig,
  DataCloudMode,
  getDataCloudConfig,
  validateDataCloudConfig,
  isLibraryMode,
  isServiceMode,
} from './data-cloud-mode';

async function parseJsonResponse<T>(
  response: Response,
  context: string
): Promise<T> {
  const raw = await response.text();

  if (!raw) {
    throw new Error(`${context} returned an empty response`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context} returned invalid JSON: ${detail}`);
  }
}

async function readErrorResponse(
  response: Response,
  fallback: string
): Promise<string> {
  const raw = await response.text();

  if (!raw) {
    return fallback;
  }

  try {
    const payload = JSON.parse(raw) as { message?: unknown; error?: unknown };
    if (typeof payload.message === 'string' && payload.message.length > 0) {
      return payload.message;
    }
    if (typeof payload.error === 'string' && payload.error.length > 0) {
      return payload.error;
    }
  } catch {
    if (raw.trim().length > 0) {
      return raw.trim();
    }
  }

  return fallback;
}

/**
 * Feature definition
 */
export interface Feature {
  name: string;
  value: number | string | boolean | Record<string, unknown>;
  timestamp: string;
  ttl?: number;
  tags?: string[];
}

/**
 * Base Data Cloud client interface (supports both modes)
 */
export interface DataCloudClient {
  /** Initialize the client */
  initialize(): Promise<void>;

  /** Check if client is ready */
  isReady(): boolean;

  /** Compute a feature value */
  computeFeature(featureName: string, input: Record<string, unknown>): Promise<Feature>;

  /** Get a cached feature value */
  getFeature(featureName: string): Promise<Feature | null>;

  /** Store a feature value */
  storeFeature(feature: Feature): Promise<void>;

  /** Query features by tags */
  queryFeatures(tags: string[]): Promise<Feature[]>;

  /** Get feature statistics */
  getFeatureStats(): Promise<Record<string, unknown>>;

  /** Shutdown the client */
  shutdown(): Promise<void>;

  /** Get client mode */
  getMode(): DataCloudMode;

  /** Get health status */
  getHealth(): Promise<{ status: 'healthy' | 'degraded' | 'unhealthy' }>;
}

/**
 * Library Mode Client
 *
 * Direct in-process client for development
 */
class DataCloudLibraryClient implements DataCloudClient {
  private config: DataCloudConfig;
  private ready = false;
  private cache = new Map<string, Feature>();

  constructor(config: DataCloudConfig) {
    this.config = config;
  }

  async initialize(): Promise<void> {
    console.info('☁️  Initializing Data Cloud in LIBRARY mode');
    console.info(`   Cache size: ${this.config.library?.cacheSize || 'N/A'}`);
    console.info(`   Local compute: ${this.config.library?.localFeatureCompute ? 'ON' : 'OFF'}`);

    // Simulate library initialization
    await new Promise((resolve) => setTimeout(resolve, 100));

    this.ready = true;
    console.info('✅ Data Cloud Library client ready');
  }

  isReady(): boolean {
    return this.ready;
  }

  async computeFeature(
    featureName: string,
    input: Record<string, unknown>
  ): Promise<Feature> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    if (this.config.library?.debug) {
      console.debug(`[DATA-CLOUD-LIB] Computing feature: ${featureName}`, input);
    }

    // Simulate feature computation in library mode
    const feature: Feature = {
      name: featureName,
      value: this.computeLocalValue(featureName, input),
      timestamp: new Date().toISOString(),
      ttl: this.config.global?.featureTTL,
    };

    // Cache the result
    this.cache.set(featureName, feature);

    return feature;
  }

  private computeLocalValue(
    featureName: string,
    input: Record<string, unknown>
  ): number | string | boolean | Record<string, unknown> {
    // Simple local computation for dev mode
    switch (featureName) {
      case 'agent.count':
        return Object.keys(input).length;
      case 'workflow.duration':
        return Math.random() * 1000;
      case 'code.quality.score':
        return Math.floor(Math.random() * 100);
      case 'user.activity':
        return { active: true, timestamp: new Date().toISOString() } as Record<string, unknown>;
      default: {
        const val = input[featureName];
        if (typeof val === 'string' || typeof val === 'number' || typeof val === 'boolean') {
          return val;
        }
        if (val && typeof val === 'object') {
          return val as Record<string, unknown>;
        }
        return 0;
      }
    }
  }

  async getFeature(featureName: string): Promise<Feature | null> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    const cached = this.cache.get(featureName);
    if (cached) {
      // Check TTL
      if (cached.ttl) {
        const age = Date.now() - new Date(cached.timestamp).getTime();
        if (age > cached.ttl) {
          this.cache.delete(featureName);
          return null;
        }
      }
      return cached;
    }
    return null;
  }

  async storeFeature(feature: Feature): Promise<void> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    if (this.config.library?.debug) {
      console.debug(`[DATA-CLOUD-LIB] Storing feature: ${feature.name}`);
    }

    this.cache.set(feature.name, {
      ...feature,
      timestamp: new Date().toISOString(),
    });
  }

  async queryFeatures(tags: string[]): Promise<Feature[]> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    if (this.config.library?.debug) {
      console.debug(`[DATA-CLOUD-LIB] Querying features with tags:`, tags);
    }

    // Return cached features matching tags
    return Array.from(this.cache.values()).filter((feature) =>
      tags.some((tag) => feature.tags?.includes(tag))
    );
  }

  async getFeatureStats(): Promise<Record<string, unknown>> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    const features = Array.from(this.cache.values());
    return {
      totalFeatures: features.length,
      cacheSize: this.cache.size,
      avgFeatureAge: this.calculateAvgAge(features),
      mode: 'library',
    };
  }

  private calculateAvgAge(features: Feature[]): number {
    if (features.length === 0) return 0;
    const now = Date.now();
    const totalAge = features.reduce(
      (sum, f) => sum + (now - new Date(f.timestamp).getTime()),
      0
    );
    return totalAge / features.length;
  }

  async shutdown(): Promise<void> {
    console.info('☁️  Shutting down Data Cloud Library client');
    this.cache.clear();
    this.ready = false;
    console.info('✅ Data Cloud Library client shut down');
  }

  getMode(): DataCloudMode {
    return DataCloudMode.LIBRARY;
  }

  async getHealth(): Promise<{ status: 'healthy' | 'degraded' | 'unhealthy' }> {
    return { status: 'healthy' };
  }
}

/**
 * Service Mode Client
 *
 * HTTP client for external Data Cloud service
 */
class DataCloudServiceClient implements DataCloudClient {
  private config: DataCloudConfig;
  private ready = false;
  private serviceUrl: string;
  private healthCheckInterval: NodeJS.Timeout | null = null;

  constructor(config: DataCloudConfig) {
    this.config = config;
    const { host, port } = config.service!;
    this.serviceUrl = `http://${host}:${port}`;
  }

  async initialize(): Promise<void> {
    console.info('☁️  Initializing Data Cloud in SERVICE mode');
    console.info(`   Service URL: ${this.serviceUrl}`);
    console.info(`   Timeout: ${this.config.service?.timeout}ms`);
    console.info(
      `   Health checks: ${this.config.service?.healthCheckEnabled ? 'ON' : 'OFF'}`
    );

    // Check service availability
    try {
      const response = await this.fetch('/health', { method: 'GET' });
      if (!response.ok) {
        throw new Error(`Service health check failed: ${response.status}`);
      }
      this.ready = true;
      console.info('✅ Data Cloud Service client ready');

      // Start health check interval if enabled
      if (this.config.service?.healthCheckEnabled) {
        this.startHealthChecks();
      }
    } catch (error) {
      console.error('❌ Failed to connect to Data Cloud service:', error);
      throw new Error(`Cannot connect to Data Cloud service at ${this.serviceUrl}`);
    }
  }

  private startHealthChecks(): void {
    const interval = this.config.service?.healthCheckInterval || 30000;
    this.healthCheckInterval = setInterval(async () => {
      try {
        const response = await this.fetch('/health', { method: 'GET' });
        if (!response.ok) {
          console.warn('⚠️ Data Cloud service health check failed');
        }
      } catch (error) {
        console.warn('⚠️ Data Cloud service health check error:', error);
      }
    }, interval);
  }

  isReady(): boolean {
    return this.ready;
  }

  async computeFeature(
    featureName: string,
    input: Record<string, unknown>
  ): Promise<Feature> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    const response = await this.fetch('/api/v1/features/compute', {
      method: 'POST',
      body: JSON.stringify({
        name: featureName,
        input,
      }),
    });

    if (!response.ok) {
      throw new Error(
        await readErrorResponse(response, `Failed to compute feature: ${response.statusText}`)
      );
    }

    return parseJsonResponse(response, 'Data Cloud computeFeature');
  }

  async getFeature(featureName: string): Promise<Feature | null> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    const response = await this.fetch(`/api/v1/features/${featureName}`, {
      method: 'GET',
    });

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw new Error(
        await readErrorResponse(response, `Failed to get feature: ${response.statusText}`)
      );
    }

    return parseJsonResponse(response, 'Data Cloud getFeature');
  }

  async storeFeature(feature: Feature): Promise<void> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    const response = await this.fetch('/api/v1/features', {
      method: 'POST',
      body: JSON.stringify(feature),
    });

    if (!response.ok) {
      throw new Error(
        await readErrorResponse(response, `Failed to store feature: ${response.statusText}`)
      );
    }
  }

  async queryFeatures(tags: string[]): Promise<Feature[]> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    const query = tags.map((t) => `tag=${encodeURIComponent(t)}`).join('&');
    const response = await this.fetch(`/api/v1/features?${query}`, {
      method: 'GET',
    });

    if (!response.ok) {
      throw new Error(
        await readErrorResponse(response, `Failed to query features: ${response.statusText}`)
      );
    }

    return parseJsonResponse(response, 'Data Cloud queryFeatures');
  }

  async getFeatureStats(): Promise<Record<string, unknown>> {
    if (!this.ready) {
      throw new Error('Data Cloud client not initialized');
    }

    const response = await this.fetch('/api/v1/features/stats', {
      method: 'GET',
    });

    if (!response.ok) {
      throw new Error(
        await readErrorResponse(response, `Failed to get feature stats: ${response.statusText}`)
      );
    }

    return parseJsonResponse(response, 'Data Cloud getFeatureStats');
  }

  async shutdown(): Promise<void> {
    console.info('☁️  Shutting down Data Cloud Service client');

    if (this.healthCheckInterval) {
      clearInterval(this.healthCheckInterval);
      this.healthCheckInterval = null;
    }

    this.ready = false;
    console.info('✅ Data Cloud Service client shut down');
  }

  getMode(): DataCloudMode {
    return DataCloudMode.SERVICE;
  }

  async getHealth(): Promise<{ status: 'healthy' | 'degraded' | 'unhealthy' }> {
    if (!this.ready) {
      return { status: 'unhealthy' };
    }

    try {
      const response = await this.fetch('/health', { method: 'GET' });
      return parseJsonResponse(response, 'Data Cloud health');
    } catch (error) {
      return { status: 'unhealthy' };
    }
  }

  private async fetch(
    path: string,
    options: RequestInit = {}
  ): Promise<Response> {
    const url = `${this.serviceUrl}${path}`;
    const timeout = this.config.service?.timeout || 30000;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          ...options.headers,
        },
      });

      return response;
    } finally {
      clearTimeout(timeoutId);
    }
  }
}

/**
 * Global Data Cloud client instance
 */
let globalClient: DataCloudClient | null = null;

/**
 * Factory function to create Data Cloud client based on mode
 *
 * @param config Optional config (uses getDataCloudConfig() if not provided)
 * @returns Appropriate Data Cloud client instance
 *
 * @example
 * ```ts
 * // Automatically picks mode based on environment
 * const client = createDataCloudClient();
 * await client.initialize();
 *
 * // In dev: runs library client
 * // In staging/prod: connects to service
 *
 * const feature = await client.computeFeature('agent.count', { agents: 5 });
 * ```
 */
export function createDataCloudClient(config?: DataCloudConfig): DataCloudClient {
  const cfg = config || getDataCloudConfig();
  validateDataCloudConfig(cfg);

  if (isLibraryMode(cfg)) {
    return new DataCloudLibraryClient(cfg);
  } else if (isServiceMode(cfg)) {
    return new DataCloudServiceClient(cfg);
  } else {
    throw new Error(`Unknown Data Cloud mode: ${cfg.mode}`);
  }
}

/**
 * Get or create global Data Cloud client instance
 *
 * Useful for sharing a single client across the application.
 *
 * @example
 * ```ts
 * const client = getGlobalDataCloudClient();
 * if (!client.isReady()) {
 *   await client.initialize();
 * }
 * ```
 */
export async function getGlobalDataCloudClient(): Promise<DataCloudClient> {
  if (!globalClient) {
    globalClient = createDataCloudClient();
    await globalClient.initialize();
  }
  return globalClient;
}

/**
 * Reset global Data Cloud client (for testing)
 */
export function resetGlobalDataCloudClient(): void {
  if (globalClient) {
    void globalClient.shutdown().catch((error: unknown) => {
      console.error('Failed to shutdown global Data Cloud client:', error);
    });
    globalClient = null;
  }
}

export type { DataCloudClient };
