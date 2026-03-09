import { devLog } from '@shared/utils/dev-logger';

import type { MonitoringConfig } from '../core/interfaces';

const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined';
const isServiceWorker = typeof self !== 'undefined' && 'ServiceWorkerGlobalScope' in self;
type MetricType = 'timing' | 'counter' | 'distribution';

interface Metric {
  name: string;
  type: MetricType;
  value: number;
  tags?: Record<string, string>;
  timestamp?: number;
}

interface PerformanceMonitorAPI {
  enable(): void;
  disable(): void;
  timeStart(name: string, tags?: Record<string, string>): () => void;
  record(type: MetricType, name: string, value: number, tags?: Record<string, string>): void;
  getMetrics(): Metric[];
  clear(): void;
  flush(): Promise<void>;
  destroy(): void;
  configure(options: Partial<MonitoringConfig>): void;
}

export type { PerformanceMonitorAPI };

class PerformanceMonitor implements PerformanceMonitorAPI {
  private static instance: PerformanceMonitor | null = null;
  private metrics: Metric[] = [];
  private enabled: boolean = true;
  private readonly MAX_METRICS = 1000;
  private flushInterval = 60000; // 1 minute default
  private flushTimer: NodeJS.Timeout | null = null;
  private unloadHandler: (() => void) | null = null;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private heartbeatInterval = 0;
  private heartbeatMetricName = 'dcmaar.heartbeat';

  private constructor() {
    this.restartFlushTimer();
    this.restartHeartbeatTimer();
    this.setupUnloadHandler();
  }

  public static getInstance(): PerformanceMonitor {
    if (!PerformanceMonitor.instance) {
      PerformanceMonitor.instance = new PerformanceMonitor();
    }
    return PerformanceMonitor.instance;
  }
  public enable(): void {
    if (!this.enabled) {
      this.enabled = true;
      this.restartFlushTimer();
      this.restartHeartbeatTimer();
    }
  }

  public disable(): void {
    if (this.enabled) {
      this.enabled = false;
      this.restartFlushTimer();
      this.restartHeartbeatTimer();
    }
  }

  public configure(options: Partial<MonitoringConfig>): void {
    if (typeof options.enabled === 'boolean') {
      this.enabled = options.enabled;
    }

    if (typeof options.flushIntervalMs === 'number' && options.flushIntervalMs > 0) {
      this.flushInterval = options.flushIntervalMs;
    }

    if (typeof options.heartbeatIntervalMs === 'number') {
      this.heartbeatInterval = Math.max(0, options.heartbeatIntervalMs);
    }

    if (typeof options.heartbeatMetricName === 'string' && options.heartbeatMetricName.trim()) {
      this.heartbeatMetricName = options.heartbeatMetricName.trim();
    }

    this.restartFlushTimer();
    this.restartHeartbeatTimer();
  }

  public timeStart(name: string, tags: Record<string, string> = {}): () => void {
    const startTime = typeof performance !== 'undefined' ? performance.now() : Date.now();

    return () => {
      const endTime = typeof performance !== 'undefined' ? performance.now() : Date.now();
      const duration = endTime - startTime;
      this.record('timing', name, duration, tags);
    };
  }

  public record(
    type: MetricType,
    name: string,
    value: number,
    tags: Record<string, string> = {}
  ): void {
    if (!this.enabled) return;

    const metric: Metric = {
      name,
      type,
      value,
      tags,
      timestamp: Date.now(),
    };

    this.metrics.push(metric);
    devLog.debug('[Performance]', metric);

    // Prevent memory leaks
    if (this.metrics.length > this.MAX_METRICS) {
      this.metrics = this.metrics.slice(-this.MAX_METRICS);
    }
  }

  public getMetrics(): Metric[] {
    return [...this.metrics];
  }

  public clear(): void {
    this.metrics = [];
  }

  public async flush(): Promise<void> {
    if (this.metrics.length === 0) return;

    const metricsToSend = [...this.metrics];
    this.metrics = [];

    try {
      // In a real app, you would send these to your metrics service
      devLog.info('[Performance] Flushing metrics:', metricsToSend);

      // Example: Send to background script for processing
      const runtime = typeof chrome !== 'undefined' ? chrome.runtime : undefined;
      if (runtime?.id) {
        await runtime.sendMessage({
          type: 'PERFORMANCE_METRICS',
          payload: metricsToSend,
        });
      }
    } catch (error) {
      devLog.error('[Performance] Failed to flush metrics:', error);
      // Restore metrics if sending fails
      this.metrics = [...metricsToSend, ...this.metrics];
    }
  }

  private restartFlushTimer(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
      this.flushTimer = null;
    }

    if (this.flushInterval <= 0 || !this.enabled) {
      return;
    }

    this.flushTimer = setInterval(() => {
      this.flush().catch((error) => {
        devLog.error('[Performance] Auto-flush failed:', error);
      });
    }, this.flushInterval);
  }

  private restartHeartbeatTimer(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }

    if (this.heartbeatInterval <= 0 || !this.enabled) {
      return;
    }

    this.heartbeatTimer = setInterval(() => {
      if (!this.enabled) {
        return;
      }
      this.record('counter', this.heartbeatMetricName, 1, {
        source: 'heartbeat',
      });
    }, this.heartbeatInterval);
  }

  private setupUnloadHandler(): void {
    if (!isBrowser || isServiceWorker) {
      return;
    }

    // Flush metrics before the page unloads
    const handleUnload = () => {
      if (this.metrics.length === 0) {
        return;
      }

      const data = new Blob(
        [JSON.stringify({ metrics: this.metrics })],
        { type: 'application/json' }
      );

      if (navigator?.sendBeacon) {
        navigator.sendBeacon('/api/performance', data);
      }

      // Also try to flush via the background script
      try {
        const runtime = typeof chrome !== 'undefined' ? chrome.runtime : undefined;
        if (runtime) {
          runtime.sendMessage({
            type: 'PERFORMANCE_METRICS',
            payload: this.metrics,
          });
        }
      } catch (error) {
        devLog.error('[Performance] Failed to send metrics on unload:', error);
      }
    };

    window.addEventListener('beforeunload', handleUnload);
    window.addEventListener('pagehide', handleUnload);
    this.unloadHandler = handleUnload;
  }

  public destroy(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
      this.flushTimer = null;
    }

    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }

    if (isBrowser && !isServiceWorker && this.unloadHandler) {
      // Remove event listeners
      window.removeEventListener('beforeunload', this.unloadHandler);
      window.removeEventListener('pagehide', this.unloadHandler);
      this.unloadHandler = null;
    }

    // Flush any remaining metrics
    this.flush().catch(console.error);
  }
}

// Export a singleton instance (no-op shim in worker contexts)
const noopMonitor: PerformanceMonitorAPI = {
  enable: () => {},
  disable: () => {},
  timeStart: () => () => {},
  record: () => {},
  getMetrics: () => [],
  clear: () => {},
  flush: async () => {},
  destroy: () => {},
  configure: () => {},
};

export const performanceMonitor: PerformanceMonitorAPI = isServiceWorker
  ? noopMonitor
  : PerformanceMonitor.getInstance();

export default PerformanceMonitor;
