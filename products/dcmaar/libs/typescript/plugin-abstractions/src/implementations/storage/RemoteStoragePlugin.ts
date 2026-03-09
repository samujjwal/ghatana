/**
 * @doc.type class
 * @doc.purpose HTTP-based remote storage plugin
 * @doc.layer product
 * @doc.pattern Adapter
 * 
 * Provides storage using remote HTTP endpoints.
 * Suitable for storing data in external services, APIs, or cloud storage.
 * 
 * @see {@link IStorage}
 */

import { IStorage } from '../../interfaces/Storage';

/**
 * Remote storage response
 */
interface RemoteResponse {
  success: boolean;
  data?: unknown;
  error?: string;
}

/**
 * Remote Storage Plugin
 * 
 * HTTP-based storage using remote endpoints.
 * Suitable for:
 * - Cloud storage backends (AWS S3, Azure Blob, etc.)
 * - REST API backends
 * - Microservice integration
 * - Cross-origin data storage
 * 
 * NOT suitable for:
 * - Real-time streaming data
 * - High-frequency updates (latency-sensitive)
 * - Sensitive authentication data
 * 
 * Usage:
 * ```typescript
 * const storage = new RemoteStoragePlugin({
 *   baseUrl: 'https://api.example.com/storage',
 *   authToken: 'bearer-token-here',
 * });
 * await storage.initialize();
 * 
 * await storage.set('user-123', { name: 'John', age: 30 });
 * const user = await storage.get('user-123');
 * 
 * await storage.shutdown();
 * ```
 */
export class RemoteStoragePlugin implements IStorage {
  // IPlugin interface implementation
  readonly id = 'remote-storage';
  readonly name = 'Remote Storage';
  readonly version = '0.1.0';
  readonly description = 'HTTP-based remote storage';
  enabled = false;
  metadata: Record<string, unknown> = {};

  // Configuration
  private baseUrl: string;
  private authToken?: string;
  private timeout: number;
  private maxRetries: number;
  private headers: Record<string, string>;

  /**
   * Create Remote Storage plugin
   * 
   * @param config - Configuration object
   * @param config.baseUrl - Base URL for storage endpoints
   * @param config.authToken - Optional Bearer token for authentication
   * @param config.timeout - Request timeout in milliseconds (default 30000)
   * @param config.maxRetries - Max retry attempts (default 3)
   * @param config.headers - Additional HTTP headers
   */
  constructor(config: {
    baseUrl: string;
    authToken?: string;
    timeout?: number;
    maxRetries?: number;
    headers?: Record<string, string>;
  }) {
    this.baseUrl = config.baseUrl;
    this.authToken = config.authToken;
    this.timeout = config.timeout ?? 30000;
    this.maxRetries = config.maxRetries ?? 3;
    this.headers = config.headers ?? {};
  }

  /**
   * Initialize the storage plugin
   * 
   * Verifies connectivity to remote storage endpoint.
   * 
   * @throws Error if endpoint is unreachable
   */
  async initialize(): Promise<void> {
    try {
      // Test connectivity
      const response = await this.makeRequest('GET', '/_health', null, 1);
      if (!response.ok) {
        throw new Error(`Health check failed: ${response.status}`);
      }
      this.enabled = true;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Failed to initialize remote storage: ${message}`);
    }
  }

  /**
   * Shutdown the storage plugin
   */
  async shutdown(): Promise<void> {
    this.enabled = false;
  }

  /**
   * Store a value
   * 
   * @param key - Storage key
   * @param value - Value to store
   * @param ttl - Time-to-live in milliseconds
   * @throws Error on network or server error
   */
  async set(key: string, value: unknown, ttl?: number): Promise<void> {
    if (!this.enabled) {
      throw new Error('Remote storage not initialized');
    }

    const payload = {
      value,
      ttl,
      timestamp: Date.now(),
    };

    const response = await this.makeRequest(
      'POST',
      `/${encodeURIComponent(key)}`,
      JSON.stringify(payload),
    );

    const data = (await response.json()) as RemoteResponse;

    if (!data.success) {
      throw new Error(`Failed to store: ${data.error ?? 'unknown error'}`);
    }
  }

  /**
   * Retrieve a value
   * 
   * @param key - Storage key
   * @returns Stored value or null if not found
   * @throws Error on network or server error
   */
  async get(key: string): Promise<unknown | null> {
    if (!this.enabled) {
      throw new Error('Remote storage not initialized');
    }

    const response = await this.makeRequest(
      'GET',
      `/${encodeURIComponent(key)}`,
      null,
    );

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw new Error(`Failed to retrieve: ${response.status}`);
    }

    const data = (await response.json()) as RemoteResponse;

    if (!data.success) {
      throw new Error(`Failed to retrieve: ${data.error ?? 'unknown error'}`);
    }

    return data.data ?? null;
  }

  /**
   * Delete a value
   * 
   * @param key - Storage key
   * @throws Error on network or server error
   */
  async delete(key: string): Promise<void> {
    if (!this.enabled) {
      throw new Error('Remote storage not initialized');
    }

    const response = await this.makeRequest(
      'DELETE',
      `/${encodeURIComponent(key)}`,
      null,
    );

    if (response.status === 404) {
      // Key not found is not an error for delete
      return;
    }

    if (!response.ok) {
      throw new Error(`Failed to delete: ${response.status}`);
    }

    const data = (await response.json()) as RemoteResponse;

    if (!data.success) {
      throw new Error(`Failed to delete: ${data.error ?? 'unknown error'}`);
    }
  }

  /**
   * Check if key exists
   * 
   * @param key - Storage key
   * @returns True if key exists on remote storage
   */
  async exists(key: string): Promise<boolean> {
    if (!this.enabled) {
      throw new Error('Remote storage not initialized');
    }

    const response = await this.makeRequest(
      'HEAD',
      `/${encodeURIComponent(key)}`,
      null,
    );

    return response.status === 200;
  }

  /**
   * Clear all storage
   * 
   * Clears all keys on remote storage.
   * 
   * @throws Error on network or server error
   */
  async clear(): Promise<void> {
    if (!this.enabled) {
      throw new Error('Remote storage not initialized');
    }

    const response = await this.makeRequest('DELETE', '/', null);

    if (!response.ok) {
      throw new Error(`Failed to clear: ${response.status}`);
    }

    const data = (await response.json()) as RemoteResponse;

    if (!data.success) {
      throw new Error(`Failed to clear: ${data.error ?? 'unknown error'}`);
    }
  }

  /**
   * Execute command interface from IPlugin
   * 
   * @param command - Command name
   * @param params - Command parameters
   * @returns Command result
   */
  async execute(
    command: string,
    params?: Record<string, unknown>,
  ): Promise<unknown> {
    switch (command) {
      case 'set':
        return await this.set(
          String(params?.key ?? ''),
          params?.value,
          params?.ttl as number | undefined,
        );
      case 'get':
        return await this.get(String(params?.key ?? ''));
      case 'delete':
        return await this.delete(String(params?.key ?? ''));
      case 'exists':
        return await this.exists(String(params?.key ?? ''));
      case 'clear':
        return await this.clear();
      default:
        throw new Error(`Unknown command: ${command}`);
    }
  }

  /**
   * Make HTTP request with retry logic
   * 
   * @private
   * @param method - HTTP method
   * @param path - Request path
   * @param body - Request body
   * @param maxRetries - Override max retries
   * @returns Response object
   */
  private async makeRequest(
    method: string,
    path: string,
    body: string | null,
    maxRetries?: number,
  ): Promise<Response> {
    const retries = maxRetries ?? this.maxRetries;
    let lastError: Error | null = null;

    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.timeout);

        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
          ...this.headers,
        };

        if (this.authToken) {
          headers.Authorization = `Bearer ${this.authToken}`;
        }

        const response = await fetch(`${this.baseUrl}${path}`, {
          method,
          headers,
          body,
          signal: controller.signal,
        });

        clearTimeout(timeoutId);
        return response;
      } catch (error) {
        lastError =
          error instanceof Error ? error : new Error(String(error));

        if (attempt < retries) {
          // Exponential backoff: 100ms * 2^attempt
          const delay = 100 * Math.pow(2, attempt);
          await this.delay(delay);
        }
      }
    }

    throw lastError ?? new Error('Request failed after retries');
  }

  /**
   * Delay helper
   * 
   * @private
   * @param ms - Milliseconds to delay
   */
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}
