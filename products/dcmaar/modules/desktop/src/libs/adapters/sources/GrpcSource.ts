/**
 * GrpcSource: Streams telemetry snapshots over gRPC with optional mTLS.
 */

import type {
  TelemetrySource,
  TelemetrySnapshot,
  SourceContext,
  HealthStatus,
} from '../types';
import { createGrpcClient, type GrpcClient } from '../tauri/grpc';

export interface GrpcSourceOptions {
  endpoint: string;
  service: string;
  method: string;
  metadata?: Record<string, string>;
  pollMs?: number;
  clientCert?: string;
  clientKey?: string;
  caCert?: string;
  certPin?: string;
  timeoutMs?: number;
}

export class GrpcSource implements TelemetrySource {
  readonly kind = 'grpc' as const;

  private ctx?: SourceContext;
  private client: GrpcClient;
  private options: GrpcSourceOptions;
  private pollTimer?: NodeJS.Timeout;

  constructor(options: GrpcSourceOptions) {
    this.options = {
      pollMs: 5000,
      timeoutMs: 5000,
      ...options,
    };

    this.client = createGrpcClient({
      endpoint: options.endpoint,
      clientCert: options.clientCert,
      clientKey: options.clientKey,
      caCert: options.caCert,
      certPin: options.certPin,
    });
  }

  async init(ctx: SourceContext): Promise<void> {
    this.ctx = ctx;
    ctx.logger.info('GrpcSource initialized', {
      endpoint: this.options.endpoint,
      service: this.options.service,
      method: this.options.method,
    });
  }

  async getInitialSnapshot(): Promise<TelemetrySnapshot> {
    const span = this.ctx?.tracer.startSpan('GrpcSource.getInitialSnapshot');

    try {
      const response = await this.client.unary<TelemetrySnapshot>({
        service: this.options.service,
        method: this.options.method,
        payload: {},
        metadata: this.options.metadata,
        timeoutMs: this.options.timeoutMs,
      });

      if (response.status !== 200) {
        throw new Error(`gRPC request failed with status ${response.status}`);
      }

      span?.setStatus({ code: 'ok' });
      return response.payload;
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  async subscribe(emit: (update: TelemetrySnapshot) => void): Promise<() => void> {
    const span = this.ctx?.tracer.startSpan('GrpcSource.subscribe');

    try {
      await this.client.stream<TelemetrySnapshot>({
        service: this.options.service,
        method: this.options.method,
        payload: {},
        metadata: this.options.metadata,
        timeoutMs: this.options.timeoutMs,
      }, (message) => {
        if (message.type === 'data' && message.payload) {
          emit(message.payload);
        } else if (message.type === 'error') {
          this.ctx?.logger.error('gRPC stream error', new Error(message.error ?? 'Unknown error'));
        }
      });

      span?.setStatus({ code: 'ok' });
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }

    if (!this.options.pollMs) {
      return () => {};
    }

    this.pollTimer = setInterval(async () => {
      try {
        const snapshot = await this.getInitialSnapshot();
        emit(snapshot);
      } catch (error) {
        this.ctx?.logger.warn('gRPC poll failed', { error: (error as Error).message });
      }
    }, this.options.pollMs);

    return () => {
      if (this.pollTimer) {
        clearInterval(this.pollTimer);
        this.pollTimer = undefined;
      }
    };
  }

  async healthCheck(): Promise<HealthStatus> {
    const start = Date.now();

    try {
      await this.client.unary({
        service: this.options.service,
        method: `${this.options.method}Health`,
        payload: {},
        metadata: this.options.metadata,
        timeoutMs: this.options.timeoutMs,
      });

      return {
        healthy: true,
        lastCheck: new Date().toISOString(),
        latencyMs: Date.now() - start,
        details: { endpoint: this.options.endpoint },
      };
    } catch (error) {
      return {
        healthy: false,
        lastCheck: new Date().toISOString(),
        error: (error as Error).message,
      };
    }
  }

  async close(): Promise<void> {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = undefined;
    }
    this.ctx?.logger.info('GrpcSource closed');
  }
}

export const createGrpcSource = (options: GrpcSourceOptions): TelemetrySource => {
  return new GrpcSource(options);
};
