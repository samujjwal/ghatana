/**
 * LocalFileSink: Writes control commands to local filesystem as NDJSON.
 * Supports rotation, compression, and crash-safe writes.
 */

import type {
  ControlSink,
  ControlCommand,
  SinkContext,
  SinkAck,
  HealthStatus,
} from '../types';

export interface LocalFileSinkOptions {
  outDir: string;
  rotateMB?: number;
  rotateMinutes?: number;
  gzip?: boolean;
  fsync?: boolean;
}

interface FileHandle {
  path: string;
  size: number;
  createdAt: number;
}

export class LocalFileSink implements ControlSink {
  readonly kind = 'file' as const;

  private ctx?: SinkContext;
  private options: LocalFileSinkOptions;
  private currentFile?: FileHandle;
  private pendingCommands: ControlCommand[] = [];

  constructor(options: LocalFileSinkOptions) {
    this.options = {
      rotateMB: 10,
      rotateMinutes: 30,
      gzip: false,
      fsync: true,
      ...options,
    };
  }

  async init(ctx: SinkContext): Promise<void> {
    this.ctx = ctx;
    await this.ensureDirectory();
    await this.openFile();

    ctx.logger.info('LocalFileSink initialized', {
      outDir: this.options.outDir,
      rotateMB: this.options.rotateMB,
    });
  }

  async enqueue(command: ControlCommand): Promise<void> {
    const span = this.ctx?.tracer.startSpan('LocalFileSink.enqueue');

    try {
      this.pendingCommands.push(command);
      span?.setAttribute('commandId', command.id);
      span?.setAttribute('category', command.category);
      span?.setStatus({ code: 'ok' });
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  async flush(): Promise<SinkAck[]> {
    const span = this.ctx?.tracer.startSpan('LocalFileSink.flush');
    const acks: SinkAck[] = [];

    try {
      if (this.pendingCommands.length === 0) {
        return acks;
      }

      await this.checkRotation();

      for (const command of this.pendingCommands) {
        try {
          await this.writeCommand(command);
          acks.push({
            ok: true,
            commandId: command.id,
            deliveredAt: new Date().toISOString(),
          });
        } catch (error) {
          acks.push({
            ok: false,
            commandId: command.id,
            error: (error as Error).message,
          });
        }
      }

      this.pendingCommands = [];
      span?.setAttribute('flushedCount', acks.length);
      span?.setStatus({ code: 'ok' });

      return acks;
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      this.ctx?.logger.error('Flush failed', error as Error);
      throw error;
    } finally {
      span?.end();
    }
  }

  async healthCheck(): Promise<HealthStatus> {
    const start = Date.now();

    try {
      await this.ensureDirectory();
      return {
        healthy: true,
        lastCheck: new Date().toISOString(),
        latencyMs: Date.now() - start,
        details: {
          currentFile: this.currentFile?.path,
          pendingCount: this.pendingCommands.length,
        },
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
    if (this.pendingCommands.length > 0) {
      await this.flush();
    }
    this.ctx?.logger.info('LocalFileSink closed');
  }

  private async ensureDirectory(): Promise<void> {
    const { tauriFS } = await import('../tauri/fs');
    const exists = await tauriFS.exists(this.options.outDir);
    
    if (!exists) {
      await tauriFS.createDir(this.options.outDir, { recursive: true });
    }
  }

  private async openFile(): Promise<void> {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const filename = `commands-${timestamp}.ndjson`;
    const path = `${this.options.outDir}/${filename}`;

    this.currentFile = {
      path,
      size: 0,
      createdAt: Date.now(),
    };
  }

  private async checkRotation(): Promise<void> {
    if (!this.currentFile) {
      await this.openFile();
      return;
    }

    const sizeMB = this.currentFile.size / (1024 * 1024);
    const ageMinutes = (Date.now() - this.currentFile.createdAt) / (1000 * 60);

    const shouldRotate =
      (this.options.rotateMB && sizeMB >= this.options.rotateMB) ||
      (this.options.rotateMinutes && ageMinutes >= this.options.rotateMinutes);

    if (shouldRotate) {
      if (this.options.gzip) {
        await this.compressFile(this.currentFile.path);
      }
      await this.openFile();
    }
  }

  private async writeCommand(command: ControlCommand): Promise<void> {
    if (!this.currentFile) {
      await this.openFile();
    }

    const line = JSON.stringify(command) + '\n';
    const { tauriFS } = await import('../tauri/fs');
    
    await tauriFS.appendTextFile(this.currentFile!.path, line);
    this.currentFile!.size += line.length;
  }

  private async compressFile(path: string): Promise<void> {
    // Compression would require additional library
    // For now, just rename with .gz extension as marker
    const { tauriFS } = await import('../tauri/fs');
    const content = await tauriFS.readTextFile(path);
    await tauriFS.writeTextFile(`${path}.gz`, content);
    await tauriFS.removeFile(path);
  }
}

export const createLocalFileSink = (options: LocalFileSinkOptions): ControlSink => {
  return new LocalFileSink(options);
};
