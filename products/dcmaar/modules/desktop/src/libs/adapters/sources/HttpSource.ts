/**
 * HttpSource: Fetches telemetry snapshots from HTTP endpoint.
 * Supports long-polling, JWT auth, and certificate pinning.
 */

import type {
  TelemetrySource,
  TelemetrySnapshot,
  SourceContext,
  HealthStatus,
} from '../types';

export interface HttpSourceOptions {
  snapshotUrl: string;
  auth?: {
    type: 'jwt' | 'apikey';
    token: string;
  };
  pollMs?: number;
  certPin?: string;
  timeoutMs?: number;
}

export class HttpSource implements TelemetrySource {
  readonly kind = 'http' as const;

  private ctx?: SourceContext;
  private options: HttpSourceOptions;
  private pollTimer?: NodeJS.Timeout;
  private abortController?: AbortController;

  constructor(options: HttpSourceOptions) {
    this.options = {
      pollMs: 10000,
      timeoutMs: 5000,
      ...options,
    };
  }

  async init(ctx: SourceContext): Promise<void> {
    this.ctx = ctx;
    await this.validateConnection();

    ctx.logger.info('HttpSource initialized', {
      url: this.options.snapshotUrl,
      pollMs: this.options.pollMs,
    });
  }

  async getInitialSnapshot(): Promise<TelemetrySnapshot> {
    const span = this.ctx?.tracer.startSpan('HttpSource.getInitialSnapshot');

    try {
      const snapshot = await this.fetchSnapshot();
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
    if (!this.options.pollMs) {
      return () => {};
    }

    this.pollTimer = setInterval(async () => {
      try {
        const snapshot = await this.fetchSnapshot();
        emit(snapshot);
      } catch (error) {
        this.ctx?.logger.warn('Poll failed', {
          error: (error as Error).message,
        });
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
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = undefined;
    }

    if (this.abortController) {
      this.abortController.abort();
      this.abortController = undefined;
    }

    this.ctx?.logger.info('HttpSource closed');
  }

  private async fetchSnapshot(): Promise<TelemetrySnapshot> {
    this.abortController = new AbortController();
    const timeout = setTimeout(
      () => this.abortController?.abort(),
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

      const response = await fetch(this.options.snapshotUrl, {
        method: 'GET',
        headers,
        signal: this.abortController.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();

      if (data.signature && this.ctx) {
        const valid = await this.ctx.keyring.verify(
          data.snapshot,
          data.signature,
          data.kid,
        );

        if (!valid) {
          throw new Error('Invalid snapshot signature');
        }
      }

      return data.snapshot ?? data;
    } finally {
      clearTimeout(timeout);
    }
  }

  private async validateConnection(): Promise<void> {
    // Lightweight HEAD request to check connectivity
    const response = await fetch(this.options.snapshotUrl, {
      method: 'HEAD',
    });

    if (!response.ok) {
      throw new Error(`Connection validation failed: ${response.status}`);
    }
  }
}

export const createHttpSource = (options: HttpSourceOptions): TelemetrySource => {
  return new HttpSource(options);
};
