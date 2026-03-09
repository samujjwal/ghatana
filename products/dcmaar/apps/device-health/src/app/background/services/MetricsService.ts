import { DEFAULT_CONFIG } from '@shared/config/index';
import { loadConfig, subscribeToConfigChanges } from '@shared/config/storage';
import { performanceMonitor } from '../../../services/PerformanceMonitor';
import { devLog } from '@shared/utils/dev-logger';

import type { JsonValue, MonitoringConfig } from '../../../core/interfaces';

type BackgroundMetric = {
  name: string;
  type: string;
  value: number;
  timestamp: number;
  tags?: Record<string, JsonValue>;
  [key: string]: JsonValue | undefined;
};

function isBackgroundMetric(value: unknown): value is BackgroundMetric {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.name === 'string' &&
    typeof candidate.type === 'string' &&
    typeof candidate.value === 'number' &&
    typeof candidate.timestamp === 'number'
  );
}

function sanitizeMetrics(payload: unknown): BackgroundMetric[] {
  if (!Array.isArray(payload)) {
    return [];
  }
  return payload.filter(isBackgroundMetric).map((metric) => ({ ...metric }));
}

class MetricsService {
  private static instance: MetricsService;

  private readonly MAX_METRICS = 1000;

  private metrics: BackgroundMetric[] = [];
  private flushTimer: NodeJS.Timeout | null = null;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private currentConfig: MonitoringConfig = { ...DEFAULT_CONFIG.monitoring };
  private unsubscribeConfig: (() => void) | null = null;

  private constructor() {
    this.setupMessageHandlers();
    this.applyConfig(this.currentConfig);
    void this.initializeConfig();
  }

  private async initializeConfig(): Promise<void> {
    try {
      const config = await loadConfig();
      this.applyConfig(config.monitoring);
    } catch (error) {
      devLog.warn('[MetricsService] Failed to load config, using defaults', error);
    }

    this.unsubscribeConfig = subscribeToConfigChanges((config) => {
      this.applyConfig(config.monitoring);
    });
  }

  private applyConfig(config: MonitoringConfig): void {
    this.currentConfig = { ...config };
    performanceMonitor.configure(config);
    this.restartFlushTimer();
    this.restartHeartbeatTimer();
  }

  public static getInstance(): MetricsService {
    if (!MetricsService.instance) {
      MetricsService.instance = new MetricsService();
    }
    return MetricsService.instance;
  }

  private setupMessageHandlers(): void {
    chrome.runtime.onMessage.addListener((message: unknown, _sender, sendResponse) => {
      const payload = message as { type?: string; payload?: unknown } | null;
      if (payload?.type === 'PERFORMANCE_METRICS') {
        this.recordMetrics(sanitizeMetrics(payload.payload));
        sendResponse({ success: true, source: 'MetricsService' });
        return true;
      }
      return false;
    });
  }

  private restartFlushTimer(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
      this.flushTimer = null;
    }

    if (!this.currentConfig.enabled || this.currentConfig.flushIntervalMs <= 0) {
      return;
    }

    this.flushTimer = setInterval(() => {
      this.emitHeartbeat('flush');
      this.flushMetrics().catch((error) => {
        devLog.error('[MetricsService] Failed to flush metrics:', error);
      });
    }, this.currentConfig.flushIntervalMs);
  }

  private restartHeartbeatTimer(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }

    if (!this.currentConfig.enabled || this.currentConfig.heartbeatIntervalMs <= 0) {
      return;
    }

    this.heartbeatTimer = setInterval(() => {
      this.emitHeartbeat('interval');
    }, this.currentConfig.heartbeatIntervalMs);
  }

  private emitHeartbeat(reason: 'interval' | 'flush'): void {
    if (!this.currentConfig.enabled) {
      return;
    }

    const heartbeatMetric: BackgroundMetric = {
      name: this.currentConfig.heartbeatMetricName,
      type: 'counter',
      value: 1,
      timestamp: Date.now(),
      tags: { reason },
    };

    this.recordMetrics([heartbeatMetric]);
  }

  public recordMetrics(metrics: BackgroundMetric[]): void {
    if (!this.currentConfig.enabled || metrics.length === 0) {
      return;
    }

    this.metrics.push(...metrics);

    if (this.metrics.length > this.MAX_METRICS) {
      this.metrics = this.metrics.slice(-this.MAX_METRICS);
      devLog.warn(`[MetricsService] Metrics buffer full, truncating to ${this.MAX_METRICS} entries`);
    }

    devLog.debug(`[MetricsService] Added ${metrics.length} metrics, total: ${this.metrics.length}`);
  }

  public async flushMetrics(): Promise<void> {
    if (this.metrics.length === 0) {
      return;
    }

    const metricsToSend = [...this.metrics];
    this.metrics = [];

    try {
      devLog.info('[MetricsService] Flushing metrics:', metricsToSend);

      console.table(
        metricsToSend.map((metric) => ({
          name: metric.name,
          type: metric.type,
          value: metric.value,
          timestamp: new Date(metric.timestamp).toISOString(),
        }))
      );
    } catch (error) {
      devLog.error('[MetricsService] Failed to send metrics:', error);
      this.metrics = [...metricsToSend, ...this.metrics];
    }
  }

  public getMetrics(): BackgroundMetric[] {
    return [...this.metrics];
  }

  public clear(): void {
    this.metrics = [];
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

    if (this.unsubscribeConfig) {
      try {
        this.unsubscribeConfig();
      } catch (error) {
        devLog.warn('[MetricsService] Failed to unsubscribe config listener', error);
      }
      this.unsubscribeConfig = null;
    }

    void this.flushMetrics();
  }
}

export const metricsService = MetricsService.getInstance();
