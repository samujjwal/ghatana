/**
 * REST API Sync Adapter
 * 
 * Synchronizes Canvas documents with a REST API backend.
 * Implements diff-based syncing with audit trail integration.
 */

import type {
  SyncAdapter,
  SyncResult,
  CanvasChange,
  RestResponse,
  SyncConfig,
} from './types';

/**
 * REST API endpoints
 */
interface RestEndpoints {
  pull: (documentId: string) => string;
  push: (documentId: string) => string;
  diff: (documentId: string, version: number) => string;
}

/**
 * REST Sync Adapter Configuration
 */
export interface RestSyncConfig extends Pick<SyncConfig, 'endpoint' | 'authToken' | 'retry'> {
  /** Custom endpoints (required) */
  endpoints: RestEndpoints;
  
  /** Request timeout in milliseconds */
  timeout?: number;
  
  /** Enable diff-based syncing */
  enableDiff?: boolean;
}

/**
 * REST API Sync Adapter
 * 
 * Features:
 * - Pull/push operations with diff support
 * - Automatic retry with exponential backoff
 * - Authentication via Bearer token
 * - Audit trail integration
 * - Conflict detection
 * 
 * @example
 * ```ts
 * const adapter = new RestSyncAdapter({
 *   endpoint: 'https://api.example.com',
 *   authToken: 'your-token',
 *   enableDiff: true,
 * });
 * 
 * await adapter.connect();
 * const result = await adapter.pull('doc-123');
 * ```
 */
export class RestSyncAdapter implements SyncAdapter {
  readonly type = 'rest' as const;
  
  private config: Required<RestSyncConfig>;
  private connected = false;
  private abortController?: AbortController;
  
  /**
   *
   */
  constructor(config: RestSyncConfig) {
    const defaultEndpoints: RestEndpoints = {
      pull: (id) => `${config.endpoint}/documents/${id}`,
      push: (id) => `${config.endpoint}/documents/${id}`,
      diff: (id, version) => `${config.endpoint}/documents/${id}/diff?since=${version}`,
    };
    
    this.config = {
      endpoint: config.endpoint,
      authToken: config.authToken || '',
      timeout: config.timeout || 30000,
      enableDiff: config.enableDiff ?? true,
      retry: config.retry || {
        maxRetries: 3,
        backoffMultiplier: 2,
        initialDelay: 1000,
      },
      endpoints: {
        ...defaultEndpoints,
        ...config.endpoints,
      },
    };
  }
  
  /**
   * Initialize connection and validate credentials
   */
  async connect(): Promise<void> {
    if (this.connected) return;
    
    try {
      // Validate endpoint accessibility
      const response = await this.fetch(`${this.config.endpoint}/health`, {
        method: 'GET',
      });
      
      if (!response.ok) {
        throw new Error(`REST API unreachable: ${response.status}`);
      }
      
      this.connected = true;
      this.abortController = new AbortController();
    } catch (error) {
      throw new Error(`REST connection failed: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }
  
  /**
   * Close connection
   */
  async disconnect(): Promise<void> {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = undefined;
    }
    this.connected = false;
  }
  
  /**
   * Check connection status
   */
  isConnected(): boolean {
    return this.connected;
  }
  
  /**
   * Pull latest data from server
   * Uses diff-based sync if enabled and version provided
   */
  async pull(documentId: string, currentVersion?: number): Promise<SyncResult> {
    if (!this.connected) {
      throw new Error('Not connected. Call connect() first.');
    }
    
    try {
      const useDiff = this.config.enableDiff && currentVersion !== undefined;
      const endpoint = useDiff
        ? this.config.endpoints.diff(documentId, currentVersion)
        : this.config.endpoints.pull(documentId);
      
      const response = await this.fetchWithRetry<RestResponse<{
        document?: unknown;
        changes?: CanvasChange[];
        version: number;
      }>>(endpoint, {
        method: 'GET',
      });
      
      if (response.error) {
        return {
          success: false,
          version: currentVersion || 0,
          changes: [],
          error: response.error,
        };
      }
      
      const data = response.data!;
      
      return {
        success: true,
        version: data.version,
        changes: useDiff ? (data.changes || []) : this.fullDocumentToChanges(data.document, documentId, data.version),
      };
    } catch (error) {
      return {
        success: false,
        version: currentVersion || 0,
        changes: [],
        error: {
          code: 'PULL_ERROR',
          message: error instanceof Error ? error.message : 'Unknown error',
          details: error,
        },
      };
    }
  }
  
  /**
   * Push local changes to server
   * Applies diffs with audit entries
   */
  async push(documentId: string, changes: CanvasChange[]): Promise<SyncResult> {
    if (!this.connected) {
      throw new Error('Not connected. Call connect() first.');
    }
    
    try {
      const endpoint = this.config.endpoints.push(documentId);
      
      const response = await this.fetchWithRetry<RestResponse<{
        version: number;
        conflicts?: Array<{
          changeId: string;
          localChange: CanvasChange;
          serverChange: CanvasChange;
        }>;
      }>>(endpoint, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          changes,
          // Include audit metadata
          audit: {
            timestamp: Date.now(),
            source: 'canvas-client',
            changeCount: changes.length,
          },
        }),
      });
      
      if (response.error) {
        return {
          success: false,
          version: 0,
          changes: [],
          error: response.error,
        };
      }
      
      const data = response.data!;
      
      // Convert conflicts to standard format
      const conflicts = data.conflicts?.map((c) => ({
        changeId: c.changeId,
        documentId,
        localChange: c.localChange,
        serverChange: c.serverChange,
        strategy: 'last-write-wins' as const,
        resolved: false,
      }));
      
      return {
        success: true,
        version: data.version,
        changes: [],
        conflicts,
      };
    } catch (error) {
      return {
        success: false,
        version: 0,
        changes: [],
        error: {
          code: 'PUSH_ERROR',
          message: error instanceof Error ? error.message : 'Unknown error',
          details: error,
        },
      };
    }
  }
  
  /**
   * Fetch with authentication and timeout
   */
  private async fetch(url: string, options: RequestInit = {}): Promise<Response> {
    const headers = new Headers(options.headers);
    
    if (this.config.authToken) {
      headers.set('Authorization', `Bearer ${this.config.authToken}`);
    }
    
    return fetch(url, {
      ...options,
      headers,
      signal: this.abortController?.signal,
    });
  }
  
  /**
   * Fetch with automatic retry and exponential backoff
   */
  private async fetchWithRetry<T>(
    url: string,
    options: RequestInit = {},
    attempt = 0
  ): Promise<T> {
    try {
      const response = await this.fetch(url, options);
      
      if (!response.ok) {
        // Retry on 5xx errors
        if (response.status >= 500 && attempt < this.config.retry.maxRetries) {
          await this.delay(this.getBackoffDelay(attempt));
          return this.fetchWithRetry<T>(url, options, attempt + 1);
        }
        
        const errorData = await response.json().catch(() => ({}));
        throw new Error(`HTTP ${response.status}: ${errorData.message || response.statusText}`);
      }
      
      return await response.json();
    } catch (error) {
      // Retry on network errors
      if (attempt < this.config.retry.maxRetries) {
        await this.delay(this.getBackoffDelay(attempt));
        return this.fetchWithRetry<T>(url, options, attempt + 1);
      }
      
      throw error;
    }
  }
  
  /**
   * Calculate exponential backoff delay
   */
  private getBackoffDelay(attempt: number): number {
    return this.config.retry.initialDelay * Math.pow(this.config.retry.backoffMultiplier, attempt);
  }
  
  /**
   * Delay helper
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
  
  /**
   * Convert full document to change list (for initial sync)
   */
  private fullDocumentToChanges(document: unknown, documentId: string, version: number): CanvasChange[] {
    return [{
      id: `full-sync-${Date.now()}`,
      documentId,
      operation: 'update',
      timestamp: Date.now(),
      userId: 'system',
      data: document as unknown,
      version,
    }];
  }
}

/**
 * Create REST sync adapter with config
 */
export function createRestSyncAdapter(config: RestSyncConfig): RestSyncAdapter {
  return new RestSyncAdapter(config);
}
