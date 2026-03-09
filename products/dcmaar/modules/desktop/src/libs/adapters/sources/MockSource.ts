/**
 * MockSource: In-memory mock telemetry source for testing and offline use.
 * Uses existing adminClient mocks.
 */

import type {
  TelemetrySource,
  TelemetrySnapshot,
  SourceContext,
  HealthStatus,
} from '../types';
import { adminClient } from '../../adminClient';

export interface MockSourceOptions {
  refreshIntervalMs?: number;
}

export class MockSource implements TelemetrySource {
  readonly kind = 'mock' as const;

  private ctx?: SourceContext;
  private options: MockSourceOptions;
  private refreshTimer?: NodeJS.Timeout;

  constructor(options: MockSourceOptions = {}) {
    this.options = {
      refreshIntervalMs: 5000,
      ...options,
    };
  }

  async init(ctx: SourceContext): Promise<void> {
    this.ctx = ctx;
    ctx.logger.info('MockSource initialized', {
      refreshIntervalMs: this.options.refreshIntervalMs,
    });
  }

  async getInitialSnapshot(): Promise<TelemetrySnapshot> {
    const span = this.ctx?.tracer.startSpan('MockSource.getInitialSnapshot');

    try {
      const [status, metrics] = await Promise.all([
        adminClient.getStatus(),
        adminClient.getMetrics(),
      ]);

      const snapshot: TelemetrySnapshot = {
        version: '1.0.0',
        collectedAt: new Date().toISOString(),
        agents: [status],
        metrics,
        metadata: { source: 'mock' },
      };

      span?.setStatus({ code: 'ok' });
      return snapshot;
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  async subscribe(emit: (update: TelemetrySnapshot) => void): Promise<() => void> {
    if (!this.options.refreshIntervalMs) {
      return () => {};
    }

    this.refreshTimer = setInterval(async () => {
      try {
        const snapshot = await this.getInitialSnapshot();
        emit(snapshot);
      } catch (error) {
        this.ctx?.logger.warn('Mock refresh failed', {
          error: (error as Error).message,
        });
      }
    }, this.options.refreshIntervalMs);

    return () => {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer);
        this.refreshTimer = undefined;
      }
    };
  }

  async healthCheck(): Promise<HealthStatus> {
    return {
      healthy: true,
      lastCheck: new Date().toISOString(),
      latencyMs: 0,
      details: { source: 'mock' },
    };
  }

  async close(): Promise<void> {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = undefined;
    }
    this.ctx?.logger.info('MockSource closed');
  }
}

export const createMockSource = (options?: MockSourceOptions): TelemetrySource => {
  return new MockSource(options);
};
