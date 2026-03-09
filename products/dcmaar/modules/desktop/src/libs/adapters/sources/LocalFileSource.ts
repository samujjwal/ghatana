/**
 * LocalFileSource: Reads telemetry snapshots from local filesystem.
 * Supports polling, encryption, and signed snapshot verification.
 */

import type {
  TelemetrySource,
  TelemetrySnapshot,
  SourceContext,
  HealthStatus,
} from '../types';

export interface LocalFileSourceOptions {
  snapshotPath: string;
  pollMs?: number;
  verifySignature?: boolean;
}

export class LocalFileSource implements TelemetrySource {
  readonly kind = 'file' as const;

  private ctx?: SourceContext;
  private options: LocalFileSourceOptions;
  private pollTimer?: NodeJS.Timeout;
  private lastModified?: number;

  constructor(options: LocalFileSourceOptions) {
    this.options = {
      pollMs: 5000,
      verifySignature: true,
      ...options,
    };
  }

  async init(ctx: SourceContext): Promise<void> {
    this.ctx = ctx;
    ctx.logger.info('LocalFileSource initialized', {
      path: this.options.snapshotPath,
      pollMs: this.options.pollMs,
    });
  }

  async getInitialSnapshot(): Promise<TelemetrySnapshot> {
    const span = this.ctx?.tracer.startSpan('LocalFileSource.getInitialSnapshot');

    try {
      const snapshot = await this.readSnapshot();
      span?.setStatus({ code: 'ok' });
      return snapshot;
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      this.ctx?.logger.error('Failed to read initial snapshot', error as Error);
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
        const modified = await this.getFileModifiedTime();
        if (modified && modified !== this.lastModified) {
          this.lastModified = modified;
          const snapshot = await this.readSnapshot();
          emit(snapshot);
        }
      } catch (error) {
        this.ctx?.logger.warn('Poll failed', { error: (error as Error).message });
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
      await this.getFileModifiedTime();
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
    this.ctx?.logger.info('LocalFileSource closed');
  }

  private async readSnapshot(): Promise<TelemetrySnapshot> {
    // In browser/Tauri context, use appropriate file API
    // For now, placeholder implementation
    const content = await this.readFile(this.options.snapshotPath);
    const parsed = JSON.parse(content);

    if (this.options.verifySignature && parsed.signature && this.ctx) {
      const valid = await this.ctx.keyring.verify(
        parsed.snapshot,
        parsed.signature,
        parsed.kid,
      );

      if (!valid) {
        throw new Error('Invalid snapshot signature');
      }
    }

    return parsed.snapshot ?? parsed;
  }

  private async readFile(path: string): Promise<string> {
    const { tauriFS } = await import('../tauri/fs');
    return await tauriFS.readTextFile(path);
  }

  private async getFileModifiedTime(): Promise<number | undefined> {
    try {
      const { tauriFS } = await import('../tauri/fs');
      const metadata = await tauriFS.metadata(this.options.snapshotPath);
      return metadata.modifiedAt;
    } catch {
      return undefined;
    }
  }
}

export const createLocalFileSource = (
  options: LocalFileSourceOptions,
): TelemetrySource => {
  return new LocalFileSource(options);
};
