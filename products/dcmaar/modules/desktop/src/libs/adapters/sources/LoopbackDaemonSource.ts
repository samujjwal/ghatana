/**
 * LoopbackDaemonSource: Communicates with local daemon via Tauri commands.
 * Provides automatic fallback to mock data when daemon is unavailable.
 */

import type {
  TelemetrySource,
  TelemetrySnapshot,
  SourceContext,
  HealthStatus,
} from '../types';

export interface LoopbackDaemonSourceOptions {
  daemonCommand: string;
  fallbackToMock?: boolean;
  healthCheckIntervalMs?: number;
}

export class LoopbackDaemonSource implements TelemetrySource {
  readonly kind = 'daemon' as const;

  private ctx?: SourceContext;
  private options: LoopbackDaemonSourceOptions;
  private healthTimer?: NodeJS.Timeout;
  private isDaemonAvailable = false;

  constructor(options: LoopbackDaemonSourceOptions) {
    this.options = {
      fallbackToMock: true,
      healthCheckIntervalMs: 10000,
      ...options,
    };
  }

  async init(ctx: SourceContext): Promise<void> {
    this.ctx = ctx;
    await this.checkDaemonHealth();

    if (this.options.healthCheckIntervalMs) {
      this.healthTimer = setInterval(
        () => this.checkDaemonHealth(),
        this.options.healthCheckIntervalMs,
      );
    }

    ctx.logger.info('LoopbackDaemonSource initialized', {
      daemonAvailable: this.isDaemonAvailable,
      fallbackEnabled: this.options.fallbackToMock,
    });
  }

  async getInitialSnapshot(): Promise<TelemetrySnapshot> {
    const span = this.ctx?.tracer.startSpan('LoopbackDaemonSource.getInitialSnapshot');

    try {
      if (this.isDaemonAvailable) {
        const snapshot = await this.invokeDaemon('get_snapshot');
        span?.setAttribute('source', 'daemon');
        span?.setStatus({ code: 'ok' });
        return snapshot;
      }

      if (this.options.fallbackToMock) {
        this.ctx?.logger.warn('Daemon unavailable, using mock data');
        span?.setAttribute('source', 'mock');
        span?.setStatus({ code: 'ok' });
        return this.getMockSnapshot();
      }

      throw new Error('Daemon unavailable and fallback disabled');
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  async subscribe(emit: (update: TelemetrySnapshot) => void): Promise<() => void> {
    // Subscribe to daemon events or poll
    const pollInterval = setInterval(async () => {
      try {
        const snapshot = await this.getInitialSnapshot();
        emit(snapshot);
      } catch (error) {
        this.ctx?.logger.warn('Subscription poll failed', {
          error: (error as Error).message,
        });
      }
    }, 5000);

    return () => clearInterval(pollInterval);
  }

  async healthCheck(): Promise<HealthStatus> {
    const start = Date.now();

    try {
      await this.invokeDaemon('health_check');
      this.isDaemonAvailable = true;

      return {
        healthy: true,
        lastCheck: new Date().toISOString(),
        latencyMs: Date.now() - start,
        details: { daemonAvailable: true },
      };
    } catch (error) {
      this.isDaemonAvailable = false;

      return {
        healthy: this.options.fallbackToMock ?? false,
        lastCheck: new Date().toISOString(),
        error: (error as Error).message,
        details: {
          daemonAvailable: false,
          fallbackActive: this.options.fallbackToMock,
        },
      };
    }
  }

  async close(): Promise<void> {
    if (this.healthTimer) {
      clearInterval(this.healthTimer);
      this.healthTimer = undefined;
    }
    this.ctx?.logger.info('LoopbackDaemonSource closed');
  }

  private async checkDaemonHealth(): Promise<void> {
    try {
      await this.invokeDaemon('health_check');
      if (!this.isDaemonAvailable) {
        this.ctx?.logger.info('Daemon connection restored');
      }
      this.isDaemonAvailable = true;
    } catch {
      if (this.isDaemonAvailable) {
        this.ctx?.logger.warn('Daemon connection lost');
      }
      this.isDaemonAvailable = false;
    }
  }

  private async invokeDaemon(command: string, args?: any): Promise<any> {
    // Tauri command invocation
    if (typeof window !== 'undefined' && '__TAURI__' in window) {
      const tauri = (window as any).__TAURI__;
      return tauri.invoke(this.options.daemonCommand, { command, args });
    }

    throw new Error('Tauri runtime not available');
  }

  private getMockSnapshot(): TelemetrySnapshot {
    return {
      version: '1.0.0',
      collectedAt: new Date().toISOString(),
      agents: [],
      metadata: { source: 'mock-fallback' },
    };
  }
}

export const createLoopbackDaemonSource = (
  options: LoopbackDaemonSourceOptions,
): TelemetrySource => {
  return new LoopbackDaemonSource(options);
};
