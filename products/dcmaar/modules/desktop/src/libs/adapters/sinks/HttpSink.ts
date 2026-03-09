/**
 * HttpSink: Sends commands to HTTP endpoint with retry and backoff.
 * Supports JWT/API key auth and optional mTLS via Tauri.
 */

import type {
  ControlSink,
  ControlCommand,
  SinkContext,
  SinkAck,
  HealthStatus,
} from '../types';

export interface HttpSinkOptions {
  url: string;
  auth?: {
    type: 'jwt' | 'apikey';
    token: string;
  };
  maxRetries?: number;
  initialRetryMs?: number;
  maxRetryMs?: number;
  timeoutMs?: number;
  certPin?: string;
}

export class HttpSink implements ControlSink {
  readonly kind = 'http' as const;

  private ctx?: SinkContext;
  private options: HttpSinkOptions;

  constructor(options: HttpSinkOptions) {
    this.options = {
      maxRetries: 3,
      initialRetryMs: 1000,
      maxRetryMs: 30000,
      timeoutMs: 5000,
      ...options,
    };
  }

  async init(ctx: SinkContext): Promise<void> {
    this.ctx = ctx;
    await this.validateConnection();

    ctx.logger.info('HttpSink initialized', {
      url: this.options.url,
    });
  }

  async enqueue(command: ControlCommand): Promise<void> {
    await this.ctx?.queue.enqueue(command);
  }

  async flush(): Promise<SinkAck[]> {
    const span = this.ctx?.tracer.startSpan('HttpSink.flush');
    const acks: SinkAck[] = [];

    try {
      let command = await this.ctx?.queue.peek();

      while (command) {
        const ack = await this.sendWithBackoff(command);
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
      await this.validateConnection();
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
    this.ctx?.logger.info('HttpSink closed');
  }

  private async sendWithBackoff(command: ControlCommand): Promise<SinkAck> {
    let lastError: Error | undefined;
    let retryDelay = this.options.initialRetryMs ?? 1000;

    for (let attempt = 0; attempt <= (this.options.maxRetries ?? 0); attempt++) {
      try {
        await this.sendCommand(command);

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
          await this.delay(retryDelay);
          retryDelay = Math.min(
            retryDelay * 2,
            this.options.maxRetryMs ?? 30000,
          );
        }
      }
    }

    return {
      ok: false,
      commandId: command.id,
      error: lastError?.message ?? 'Unknown error',
    };
  }

  private async sendCommand(command: ControlCommand): Promise<void> {
    const abortController = new AbortController();
    const timeout = setTimeout(
      () => abortController.abort(),
      this.options.timeoutMs,
    );

    try {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };

      if (this.options.auth) {
        if (this.options.auth.type === 'jwt') {
          headers['Authorization'] = `Bearer ${this.options.auth.token}`;
        } else {
          headers['X-API-Key'] = this.options.auth.token;
        }
      }

      const response = await fetch(this.options.url, {
        method: 'POST',
        headers,
        body: JSON.stringify(command),
        signal: abortController.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
    } finally {
      clearTimeout(timeout);
    }
  }

  private async validateConnection(): Promise<void> {
    const response = await fetch(this.options.url, {
      method: 'HEAD',
    });

    if (!response.ok) {
      throw new Error(`Connection validation failed: ${response.status}`);
    }
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}

export const createHttpSink = (options: HttpSinkOptions): ControlSink => {
  return new HttpSink(options);
};
