/**
 * GrpcSink: Sends control commands via gRPC with retries and mTLS support.
 */

import type {
  ControlSink,
  ControlCommand,
  SinkContext,
  SinkAck,
  HealthStatus,
} from '../types';
import { createGrpcClient, type GrpcClient } from '../tauri/grpc';

export interface GrpcSinkOptions {
  endpoint: string;
  service: string;
  method: string;
  metadata?: Record<string, string>;
  maxRetries?: number;
  retryDelayMs?: number;
  clientCert?: string;
  clientKey?: string;
  caCert?: string;
  certPin?: string;
  timeoutMs?: number;
}

export class GrpcSink implements ControlSink {
  readonly kind = 'grpc' as const;

  private ctx?: SinkContext;
  private client: GrpcClient;
  private options: GrpcSinkOptions;

  constructor(options: GrpcSinkOptions) {
    this.options = {
      maxRetries: 3,
      retryDelayMs: 1000,
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

  async init(ctx: SinkContext): Promise<void> {
    this.ctx = ctx;
    ctx.logger.info('GrpcSink initialized', {
      endpoint: this.options.endpoint,
      service: this.options.service,
      method: this.options.method,
    });
  }

  async enqueue(command: ControlCommand): Promise<void> {
    await this.ctx?.queue.enqueue(command);
  }

  async flush(): Promise<SinkAck[]> {
    const span = this.ctx?.tracer.startSpan('GrpcSink.flush');
    const acks: SinkAck[] = [];

    try {
      let command = await this.ctx?.queue.peek();

      while (command) {
        const ack = await this.sendWithRetry(command);
        acks.push(ack);

        if (ack.ok) {
          await this.ctx?.queue.dequeue();
        } else {
          break;
        }

        command = await this.ctx?.queue.peek();
      }

      span?.setAttribute('flushedCount', acks.length);
      span?.setStatus({ code: 'ok' });

      return acks;
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
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
    await this.flush();
    this.ctx?.logger.info('GrpcSink closed');
  }

  private async sendWithRetry(command: ControlCommand): Promise<SinkAck> {
    let lastError: Error | undefined;

    for (let attempt = 0; attempt <= (this.options.maxRetries ?? 0); attempt++) {
      try {
        await this.client.unary({
          service: this.options.service,
          method: this.options.method,
          payload: { command },
          metadata: this.options.metadata,
          timeoutMs: this.options.timeoutMs,
        });

        return {
          ok: true,
          commandId: command.id,
          deliveredAt: new Date().toISOString(),
        };
      } catch (error) {
        lastError = error as Error;
        this.ctx?.logger.warn('gRPC command failed', {
          commandId: command.id,
          attempt: attempt + 1,
          error: lastError.message,
        });

        if (attempt < (this.options.maxRetries ?? 0)) {
          await this.delay(this.options.retryDelayMs ?? 1000);
        }
      }
    }

    return {
      ok: false,
      commandId: command.id,
      error: lastError?.message ?? 'Unknown gRPC error',
    };
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}

export const createGrpcSink = (options: GrpcSinkOptions): ControlSink => {
  return new GrpcSink(options);
};
