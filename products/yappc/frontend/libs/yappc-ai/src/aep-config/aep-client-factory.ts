/**
 * AEP Client Factory
 *
 * Creates the appropriate AEP client based on the current mode:
 * - Library mode: In-process client using direct library calls
 * - Service mode: HTTP/gRPC client connecting to external service
 *
 * This factory abstracts away mode switching from consumers.
 */

import {
  AepConfig,
  AepMode,
  getAepConfig,
  validateAepConfig,
  isLibraryMode,
  isServiceMode,
} from './aep-mode';

/**
 * Base AEP client interface (supports both modes)
 */
export interface AepClient {
  /** Initialize the client */
  initialize(): Promise<void>;

  /** Check if client is ready */
  isReady(): boolean;

  /** Execute an agent with given input */
  executeAgent(agentId: string, input: Record<string, unknown>): Promise<unknown>;

  /** Publish an event */
  publishEvent(eventType: string, data: Record<string, unknown>): Promise<void>;

  /** Query events */
  queryEvents(filter: Record<string, unknown>): Promise<unknown[]>;

  /** Get pattern insights */
  getPatterns(): Promise<unknown[]>;

  /** Shutdown the client */
  shutdown(): Promise<void>;

  /** Get client mode */
  getMode(): AepMode;

  /** Get health status */
  getHealth(): Promise<{ status: 'healthy' | 'degraded' | 'unhealthy' }>;
}

/**
 * Library Mode Client
 *
 * Direct in-process client for development
 */
class AepLibraryClient implements AepClient {
  private config: AepConfig;
  private ready = false;
  private cache = new Map<string, unknown>();

  constructor(config: AepConfig) {
    this.config = config;
  }

  async initialize(): Promise<void> {
    console.info('📚 Initializing AEP in LIBRARY mode');
    console.info(`   Cache size: ${this.config.library?.cacheSize || 'N/A'}`);
    console.info(`   Debug: ${this.config.library?.debug ? 'ON' : 'OFF'}`);

    // Simulate library initialization
    await new Promise((resolve) => setTimeout(resolve, 100));

    this.ready = true;
    console.info('✅ AEP Library client ready');
  }

  isReady(): boolean {
    return this.ready;
  }

  async executeAgent(
    agentId: string,
    input: Record<string, unknown>
  ): Promise<unknown> {
    if (!this.ready) {
      throw new Error('AEP client not initialized');
    }

    if (this.config.library?.debug) {
      console.debug(`[AEP-LIB] Executing agent: ${agentId}`, input);
    }

    // Simulate agent execution in library mode
    const result = {
      agentId,
      status: 'completed',
      mode: 'library',
      timestamp: new Date().toISOString(),
      result: `Agent ${agentId} executed successfully in library mode`,
      input,
    };

    // Cache the result
    this.cache.set(`agent_${agentId}`, result);

    return result;
  }

  async publishEvent(
    eventType: string,
    data: Record<string, unknown>
  ): Promise<void> {
    if (!this.ready) {
      throw new Error('AEP client not initialized');
    }

    if (this.config.library?.debug) {
      console.debug(`[AEP-LIB] Publishing event: ${eventType}`, data);
    }

    const event = {
      type: eventType,
      data,
      timestamp: new Date().toISOString(),
      mode: 'library',
    };

    this.cache.set(`event_${Date.now()}`, event);
  }

  async queryEvents(filter: Record<string, unknown>): Promise<unknown[]> {
    if (!this.ready) {
      throw new Error('AEP client not initialized');
    }

    if (this.config.library?.debug) {
      console.debug(`[AEP-LIB] Querying events with filter:`, filter);
    }

    // Return cached events matching filter
    return Array.from(this.cache.values()).filter((item) =>
      Object.entries(filter).every(([key, value]) => item[key] === value)
    );
  }

  async getPatterns(): Promise<unknown[]> {
    if (!this.ready) {
      throw new Error('AEP client not initialized');
    }

    if (this.config.library?.debug) {
      console.debug(`[AEP-LIB] Getting patterns`);
    }

    return [
      {
        id: 'pattern_1',
        name: 'Common Workflow',
        confidence: 0.95,
        mode: 'library',
      },
      {
        id: 'pattern_2',
        name: 'Error Recovery',
        confidence: 0.87,
        mode: 'library',
      },
    ];
  }

  async shutdown(): Promise<void> {
    console.info('📚 Shutting down AEP Library client');
    this.cache.clear();
    this.ready = false;
    console.info('✅ AEP Library client shut down');
  }

  getMode(): AepMode {
    return AepMode.LIBRARY;
  }

  async getHealth(): Promise<{ status: 'healthy' | 'degraded' | 'unhealthy' }> {
    return { status: 'healthy' };
  }
}

/**
 * Service Mode Client
 *
 * HTTP client for external AEP service
 */
class AepServiceClient implements AepClient {
  private config: AepConfig;
  private ready = false;
  private serviceUrl: string;
  private healthCheckInterval: NodeJS.Timeout | null = null;

  constructor(config: AepConfig) {
    this.config = config;
    const { host, port } = config.service!;
    this.serviceUrl = `http://${host}:${port}`;
  }

  async initialize(): Promise<void> {
    console.info('🔗 Initializing AEP in SERVICE mode');
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
      console.info('✅ AEP Service client ready');

      // Start health check interval if enabled
      if (this.config.service?.healthCheckEnabled) {
        this.startHealthChecks();
      }
    } catch (error) {
      console.error('❌ Failed to connect to AEP service:', error);
      throw new Error(`Cannot connect to AEP service at ${this.serviceUrl}`);
    }
  }

  private startHealthChecks(): void {
    const interval = this.config.service?.healthCheckInterval || 30000;
    this.healthCheckInterval = setInterval(async () => {
      try {
        const response = await this.fetch('/health', { method: 'GET' });
        if (!response.ok) {
          console.warn('⚠️ AEP service health check failed');
        }
      } catch (error) {
        console.warn('⚠️ AEP service health check error:', error);
      }
    }, interval);
  }

  isReady(): boolean {
    return this.ready;
  }

  async executeAgent(
    agentId: string,
    input: Record<string, unknown>
  ): Promise<unknown> {
    if (!this.ready) {
      throw new Error('AEP client not initialized');
    }

    const response = await this.fetch('/api/aep/agents/execute', {
      method: 'POST',
      body: JSON.stringify({
        agentId,
        input,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to execute agent: ${response.statusText}`);
    }

    return response.json();
  }

  async publishEvent(
    eventType: string,
    data: Record<string, unknown>
  ): Promise<void> {
    if (!this.ready) {
      throw new Error('AEP client not initialized');
    }

    const response = await this.fetch('/api/aep/events', {
      method: 'POST',
      body: JSON.stringify({
        type: eventType,
        data,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to publish event: ${response.statusText}`);
    }
  }

  async queryEvents(filter: Record<string, unknown>): Promise<unknown[]> {
    if (!this.ready) {
      throw new Error('AEP client not initialized');
    }

    const query = new URLSearchParams(filter);
    const response = await this.fetch(`/api/aep/events?${query}`, {
      method: 'GET',
    });

    if (!response.ok) {
      throw new Error(`Failed to query events: ${response.statusText}`);
    }

    return response.json();
  }

  async getPatterns(): Promise<unknown[]> {
    if (!this.ready) {
      throw new Error('AEP client not initialized');
    }

    const response = await this.fetch('/api/aep/patterns', {
      method: 'GET',
    });

    if (!response.ok) {
      throw new Error(`Failed to get patterns: ${response.statusText}`);
    }

    return response.json();
  }

  async shutdown(): Promise<void> {
    console.info('🔗 Shutting down AEP Service client');

    if (this.healthCheckInterval) {
      clearInterval(this.healthCheckInterval);
      this.healthCheckInterval = null;
    }

    this.ready = false;
    console.info('✅ AEP Service client shut down');
  }

  getMode(): AepMode {
    return AepMode.SERVICE;
  }

  async getHealth(): Promise<{ status: 'healthy' | 'degraded' | 'unhealthy' }> {
    if (!this.ready) {
      return { status: 'unhealthy' };
    }

    try {
      const response = await this.fetch('/health', { method: 'GET' });
      return response.json();
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
 * Global AEP client instance
 */
let globalClient: AepClient | null = null;

/**
 * Factory function to create AEP client based on mode
 *
 * @param config Optional config (uses getAepConfig() if not provided)
 * @returns Appropriate AEP client instance
 *
 * @example
 * ```ts
 * // Automatically picks mode based on environment
 * const client = createAepClient();
 * await client.initialize();
 *
 * // In dev: runs library client
 * // In staging/prod: connects to service
 *
 * await client.executeAgent('my-agent', { input: 'data' });
 * ```
 */
export function createAepClient(config?: AepConfig): AepClient {
  const cfg = config || getAepConfig();
  validateAepConfig(cfg);

  if (isLibraryMode(cfg)) {
    return new AepLibraryClient(cfg);
  } else if (isServiceMode(cfg)) {
    return new AepServiceClient(cfg);
  } else {
    throw new Error(`Unknown AEP mode: ${cfg.mode}`);
  }
}

/**
 * Get or create global AEP client instance
 *
 * Useful for sharing a single client across the application.
 *
 * @example
 * ```ts
 * const client = getGlobalAepClient();
 * if (!client.isReady()) {
 *   await client.initialize();
 * }
 * ```
 */
export async function getGlobalAepClient(): Promise<AepClient> {
  if (!globalClient) {
    globalClient = createAepClient();
    await globalClient.initialize();
  }
  return globalClient;
}

/**
 * Reset global AEP client (for testing)
 */
export function resetGlobalAepClient(): void {
  if (globalClient) {
    globalClient.shutdown().catch(console.error);
    globalClient = null;
  }
}

export { AepClient };
