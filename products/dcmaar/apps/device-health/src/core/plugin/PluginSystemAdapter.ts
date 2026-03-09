/**
 * System metrics adapter that communicates with Rust plugins.
 * 
 * Handles:
 * - Plugin discovery and initialization
 * - Real-time metrics collection via message passing
 * - Error handling and graceful degradation
 * - Metric caching and throttling
 * - Plugin lifecycle management
 */

export interface PluginMetrics {
  cpu: {
    usage: number;
    temperature: number;
    throttled: boolean;
    cores: number;
  };
  memory: {
    usage: number;
    total: number;
    available: number;
    gcActivity: number;
  };
  battery: {
    level: number;
    charging: boolean;
    health: 'good' | 'degraded' | 'poor';
    timeRemaining: number;
  };
  timestamp: number;
}

export interface PluginConfig {
  cpuEnabled: boolean;
  memoryEnabled: boolean;
  batteryEnabled: boolean;
  pollIntervalMs: number;
  maxCacheAgeMs: number;
  errorThreshold: number;
}

export interface PluginError {
  code: string;
  message: string;
  plugin: string;
  recoverable: boolean;
  timestamp: number;
}

export type MetricsCallback = (metrics: PluginMetrics, error?: PluginError) => void;

/**
 * Adapter for communicating with Rust-based system metric plugins.
 * Uses browser extension messaging API to pass data between content script
 * and background service worker (which has access to system APIs).
 */
export class PluginSystemAdapter {
  private static instance: PluginSystemAdapter;
  private config: PluginConfig;
  private metricsCache: PluginMetrics | null = null;
  private cacheTimestamp: number = 0;
  private errorCount: number = 0;
  private callbacks: Set<MetricsCallback> = new Set();
  private pollTimer: number | null = null;
  private lastPollTime: number = 0;
  private isInitialized: boolean = false;

  private constructor(config: Partial<PluginConfig> = {}) {
    this.config = {
      cpuEnabled: true,
      memoryEnabled: true,
      batteryEnabled: true,
      pollIntervalMs: 1000,
      maxCacheAgeMs: 5000,
      errorThreshold: 10,
      ...config,
    };
  }

  /**
   * Get or create singleton adapter instance.
   */
  public static getInstance(config?: Partial<PluginConfig>): PluginSystemAdapter {
    if (!PluginSystemAdapter.instance) {
      PluginSystemAdapter.instance = new PluginSystemAdapter(config);
    }
    return PluginSystemAdapter.instance;
  }

  /**
   * Initialize the plugin adapter and connect to background service worker.
   */
  public async initialize(): Promise<void> {
    if (this.isInitialized) {
      return;
    }

    try {
      // Verify browser extension API is available
      if (!this.hasBrowserAPI()) {
        throw new Error('Browser extension API not available');
      }

      // Test connection to background service worker
      await this.sendMessage({ type: 'PING' });

      this.isInitialized = true;
      console.log('[PluginSystemAdapter] Initialized successfully');
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : String(error);
      console.error('[PluginSystemAdapter] Initialization failed:', errorMsg);
      this.handleError({
        code: 'INIT_FAILED',
        message: `Failed to initialize plugin adapter: ${errorMsg}`,
        plugin: 'system_adapter',
        recoverable: true,
        timestamp: Date.now(),
      });
      throw error;
    }
  }

  /**
   * Start polling for system metrics.
   */
  public startPolling(callback: MetricsCallback): void {
    this.callbacks.add(callback);

    if (!this.pollTimer) {
      // Initial poll immediately
      this.poll();

      // Then poll at configured interval
      this.pollTimer = window.setInterval(() => {
        this.poll();
      }, this.config.pollIntervalMs);
    }
  }

  /**
   * Stop polling for system metrics.
   */
  public stopPolling(callback?: MetricsCallback): void {
    if (callback) {
      this.callbacks.delete(callback);
    } else {
      this.callbacks.clear();
    }

    // Stop timer if no more callbacks
    if (this.callbacks.size === 0 && this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  /**
   * Get current metrics synchronously from cache.
   */
  public getMetrics(): PluginMetrics | null {
    const now = Date.now();
    if (
      this.metricsCache &&
      now - this.cacheTimestamp < this.config.maxCacheAgeMs
    ) {
      return this.metricsCache;
    }
    return null;
  }

  /**
   * Force refresh of system metrics.
   */
  public async refreshMetrics(): Promise<PluginMetrics> {
    const metrics = await this.fetchMetrics();
    this.updateCache(metrics);
    return metrics;
  }

  /**
   * Update plugin configuration.
   */
  public updateConfig(config: Partial<PluginConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current error count.
   */
  public getErrorCount(): number {
    return this.errorCount;
  }

  /**
   * Reset error counter.
   */
  public resetErrorCount(): void {
    this.errorCount = 0;
  }

  /**
   * Private: Poll for metrics and notify callbacks.
   */
  private async poll(): Promise<void> {
    const now = Date.now();

    // Check if we should use cache
    if (now - this.cacheTimestamp < this.config.maxCacheAgeMs && this.metricsCache) {
      this.notifyCallbacks(this.metricsCache);
      return;
    }

    try {
      this.lastPollTime = now;
      const metrics = await this.fetchMetrics();
      this.updateCache(metrics);
      this.errorCount = 0; // Reset error counter on success
      this.notifyCallbacks(metrics);
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : String(error);
      const pluginError: PluginError = {
        code: 'FETCH_FAILED',
        message: `Failed to fetch metrics: ${errorMsg}`,
        plugin: 'system_adapter',
        recoverable: true,
        timestamp: now,
      };
      this.handleError(pluginError);
      this.notifyCallbacks(this.metricsCache || this.getDefaultMetrics(), pluginError);
    }
  }

  /**
   * Private: Fetch metrics from background service worker.
   */
  private async fetchMetrics(): Promise<PluginMetrics> {
    try {
      const response = await this.sendMessage({
        type: 'GET_METRICS',
        config: this.config,
      });

      if (!response || !response.metrics) {
        throw new Error('Invalid response from background worker');
      }

      return response.metrics as PluginMetrics;
    } catch (error) {
      // Fallback to mock metrics for development/testing
      console.warn('[PluginSystemAdapter] Using fallback metrics:', error);
      return this.getDefaultMetrics();
    }
  }

  /**
   * Private: Send message to background service worker.
   */
  private sendMessage(message: Record<string, unknown>): Promise<Record<string, unknown>> {
    return new Promise((resolve, reject) => {
      try {
        const browserAPI = (window as unknown as Record<string, unknown>).browser as
          | {
              runtime?: {
                sendMessage?: (
                  msg: Record<string, unknown>,
                  cb: (response: Record<string, unknown>) => void
                ) => void;
              };
            }
          | undefined;

        if (!browserAPI?.runtime?.sendMessage) {
          throw new Error('Browser runtime API not available');
        }

        const timeout = setTimeout(() => {
          reject(new Error('Message timeout'));
        }, 5000);

        browserAPI.runtime.sendMessage(message, (response) => {
          clearTimeout(timeout);
          if (chrome?.runtime?.lastError) {
            reject(new Error(chrome.runtime.lastError.message));
          } else {
            resolve(response || {});
          }
        });
      } catch (error) {
        reject(error);
      }
    });
  }

  /**
   * Private: Update metrics cache.
   */
  private updateCache(metrics: PluginMetrics): void {
    this.metricsCache = metrics;
    this.cacheTimestamp = Date.now();
  }

  /**
   * Private: Notify all registered callbacks.
   */
  private notifyCallbacks(metrics: PluginMetrics, error?: PluginError): void {
    this.callbacks.forEach((callback) => {
      try {
        callback(metrics, error);
      } catch (err) {
        console.error('[PluginSystemAdapter] Callback error:', err);
      }
    });
  }

  /**
   * Private: Handle plugin errors with recovery logic.
   */
  private handleError(error: PluginError): void {
    this.errorCount++;
    console.error('[PluginSystemAdapter] Error:', error);

    // Escalate if error threshold exceeded
    if (this.errorCount > this.config.errorThreshold) {
      console.error('[PluginSystemAdapter] Error threshold exceeded, stopping polling');
      this.stopPolling();
    }
  }

  /**
   * Private: Check if browser API is available.
   */
  private hasBrowserAPI(): boolean {
    try {
      const browserAPI = (window as unknown as Record<string, unknown>).browser as unknown;
      return browserAPI !== undefined && browserAPI !== null;
    } catch {
      return false;
    }
  }

  /**
   * Private: Get default/mock metrics for fallback.
   */
  private getDefaultMetrics(): PluginMetrics {
    return {
      cpu: {
        usage: 0,
        temperature: 0,
        throttled: false,
        cores: 0,
      },
      memory: {
        usage: 0,
        total: 0,
        available: 0,
        gcActivity: 0,
      },
      battery: {
        level: 100,
        charging: false,
        health: 'good',
        timeRemaining: 0,
      },
      timestamp: Date.now(),
    };
  }

  /**
   * Shutdown the adapter and clean up resources.
   */
  public shutdown(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    this.callbacks.clear();
    this.metricsCache = null;
  }
}

/**
 * Hook for using the plugin system adapter in React components.
 */
export function usePluginSystemAdapter(): PluginSystemAdapter {
  return PluginSystemAdapter.getInstance();
}
