/**
 * LoopbackDaemonSink: Sends commands to local daemon via Tauri.
 * Implements retry logic and graceful degradation.
 */

import type {
  ControlSink,
  ControlCommand,
  SinkContext,
  SinkAck,
  HealthStatus,
} from '../types';

export interface LoopbackDaemonSinkOptions {
  daemonCommand: string;
  maxRetries?: number;
  retryDelayMs?: number;
}

export class LoopbackDaemonSink implements ControlSink {
  readonly kind = 'daemon' as const;

  private ctx?: SinkContext;
  private options: LoopbackDaemonSinkOptions;

  constructor(options: LoopbackDaemonSinkOptions) {
    this.options = {
      maxRetries: 3,
      retryDelayMs: 1000,
      ...options,
    };
  }

  async init(ctx: SinkContext): Promise<void> {
    this.ctx = ctx;
    ctx.logger.info('LoopbackDaemonSink initialized');
  }

  async enqueue(command: ControlCommand): Promise<void> {
    await this.ctx?.queue.enqueue(command);
  }

  async flush(): Promise<SinkAck[]> {
    const span = this.ctx?.tracer.startSpan('LoopbackDaemonSink.flush');
    const acks: SinkAck[] = [];

    try {
      let command = await this.ctx?.queue.peek();

      while (command) {
        const ack = await this.sendWithRetry(command);
        acks.push(ack);

        if (ack.ok) {
          await this.ctx?.queue.dequeue();
        } else {
          break; // Stop on first failure
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
      await this.invokeDaemon('health_check');
      return {
        healthy: true,
        lastCheck: new Date().toISOString(),
        latencyMs: Date.now() - start,
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
    this.ctx?.logger.info('LoopbackDaemonSink closed');
  }

  private async sendWithRetry(command: ControlCommand): Promise<SinkAck> {
    let lastError: Error | undefined;

    for (let attempt = 0; attempt <= (this.options.maxRetries ?? 0); attempt++) {
      try {
        await this.invokeDaemon('execute_command', command);

        return {
          ok: true,
          commandId: command.id,
          deliveredAt: new Date().toISOString(),
        };
      } catch (error) {
        lastError = error as Error;
        this.ctx?.logger.warn('Command send failed', {
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
      error: lastError?.message ?? 'Unknown error',
    };
  }

  private async invokeDaemon(command: string, args?: unknown): Promise<unknown> {
    if (typeof window !== 'undefined' && '__TAURI__' in window) {
      const tauri = (window as any).__TAURI__;
      return tauri.invoke(this.options.daemonCommand, { command, args });
    }

    throw new Error('Tauri runtime not available');
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}

export const createLoopbackDaemonSink = (
  options: LoopbackDaemonSinkOptions,
): ControlSink => {
  return new LoopbackDaemonSink(options);
};
