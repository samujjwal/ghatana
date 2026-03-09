/**
 * @fileoverview Filesystem connector template supporting read, write, and watch workflows.
 *
 * Demonstrates how to ingest or emit files, watch directories, and integrate with telemetry and
 * resilience primitives. Combine with `RetryPolicy` for resilient writes and `DeadLetterQueue` to
 * persist failed file operations for later replay.
 *
 * @see {@link FileSystemConnector}
 * @see {@link ../BaseConnector.BaseConnector | BaseConnector}
 * @see {@link ../resilience/RetryPolicy.RetryPolicy | RetryPolicy}
 * @see {@link ../resilience/DeadLetterQueue.DeadLetterQueue | DeadLetterQueue}
 */
import { promises as fs, watch, FSWatcher } from 'fs';
import { join, resolve, basename } from 'path';
import { v4 as uuidv4 } from 'uuid';
import { BaseConnector } from '../BaseConnector';
import { ConnectionOptions } from '../types';

/**
 * Configuration contract for `FileSystemConnector`.
 */
export interface FileSystemConnectorConfig extends ConnectionOptions {
  /**
   * Path to watch or read from
   */
  path: string;
  
  /**
   * Mode: 'read', 'write', or 'watch'
   * @default 'watch'
   */
  mode?: 'read' | 'write' | 'watch';
  
  /**
   * File pattern to match (glob pattern)
   * @default '*'
   */
  pattern?: string;
  
  /**
   * Encoding for text files
   * @default 'utf8'
   */
  encoding?: BufferEncoding;
  
  /**
   * Watch options
   */
  watchOptions?: {
    /**
     * Watch recursively
     * @default true
     */
    recursive?: boolean;
    
    /**
     * Persistent watcher
     * @default true
     */
    persistent?: boolean;
  };
  
  /**
   * Polling interval for read mode (milliseconds)
   */
  pollInterval?: number;
  
  /**
   * File format: 'json', 'text', 'binary', 'csv'
   * @default 'text'
   */
  format?: 'json' | 'text' | 'binary' | 'csv';
  
  /**
   * Create directory if it doesn't exist
   * @default true
   */
  createIfNotExists?: boolean;
  
  /**
   * Maximum file size to read (bytes)
   * @default 10MB
   */
  maxFileSize?: number;
}

/**
 * Connector that interacts with the local filesystem for ingest and egress scenarios.
 *
 * **Example (watch directory and emit events):**
 * ```ts
 * const connector = new FileSystemConnector({ path: './incoming', mode: 'watch' });
 * connector.onEvent('file_created', event => console.log(event.metadata?.filename));
 * await connector.connect();
 * ```
 *
 * **Example (poll directory with retry + DLQ):**
 * ```ts
 * const retry = new RetryPolicy({ maxAttempts: 3 });
 * const dlq = new DeadLetterQueue();
 * const connector = new FileSystemConnector({ path: './batch', mode: 'read', pollInterval: 2000 });
 * await connector.connect();
 * connector.on('error', error => dlq.add({ id: uuidv4(), payload: {} } as any, error));
 * await retry.execute(() => connector.send({ processed: Date.now() }, { filename: 'status.json' }));
 * ```
 */
export class FileSystemConnector extends BaseConnector<FileSystemConnectorConfig> {
  private watcher: FSWatcher | null = null;
  private pollInterval: NodeJS.Timeout | null = null;
  private lastReadTime: Map<string, number> = new Map();

  /**
   * Initializes connector with default watch-mode configuration.
   */
  constructor(config: FileSystemConnectorConfig) {
    super({
      mode: 'watch',
      pattern: '*',
      encoding: 'utf8',
      watchOptions: {
        recursive: true,
        persistent: true,
      },
      format: 'text',
      createIfNotExists: true,
      maxFileSize: 10 * 1024 * 1024, // 10MB
      ...config,
      type: 'filesystem',
    });
  }

  /** @inheritdoc */
  protected async _connect(): Promise<void> {
    const resolvedPath = resolve(this._config.path);

    // Check if path exists
    try {
      const stats = await fs.stat(resolvedPath);
      
      if (!stats.isDirectory() && !stats.isFile()) {
        throw new Error(`Path ${resolvedPath} is neither a file nor a directory`);
      }
    } catch (error: unknown) {
      if (error.code === 'ENOENT' && this._config.createIfNotExists) {
        // Create directory if it doesn't exist
        await fs.mkdir(resolvedPath, { recursive: true });
      } else {
        throw error;
      }
    }

    // Start watching or polling based on mode
    switch (this._config.mode) {
      case 'watch':
        await this._startWatching(resolvedPath);
        break;
      case 'read':
        if (this._config.pollInterval) {
          await this._startPolling(resolvedPath);
        } else {
          await this._readOnce(resolvedPath);
        }
        break;
      case 'write':
        // Write mode doesn't require initialization
        break;
    }

    this.emit('connected');
  }

  /** @inheritdoc */
  protected async _disconnect(): Promise<void> {
    if (this.watcher) {
      this.watcher.close();
      this.watcher = null;
    }

    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }

    this.lastReadTime.clear();
  }

  /**
   * Writes data to the configured filesystem path.
   *
   * Serializes data according to `format`, writes to disk, and emits `file_written` events containing
   * file metadata. Use together with `Telemetry` to record write durations or with `RetryPolicy`
   * when writing to shared volumes.
   *
   * @param data - Payload to persist.
   * @param options - Publish options such as `filename`.
   */
  public async send(data: unknown, options: Record<string, any> = {}): Promise<void> {
    if (this._config.mode === 'read') {
      throw new Error('Cannot send data in read mode');
    }

    const filename = options.filename || `${uuidv4()}.${this._getFileExtension()}`;
    const filepath = join(this._config.path, filename);

    try {
      const content = this._formatData(data);

      if (this._config.format === 'binary') {
        await fs.writeFile(filepath, content as Buffer);
      } else {
        await fs.writeFile(filepath, content as string, { encoding: this._config.encoding });
      }

      this._emitEvent({
        id: uuidv4(),
        type: 'file_written',
        timestamp: Date.now(),
        payload: { filepath, filename, size: content.length },
      });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Begins streaming filesystem events via `fs.watch`.
   *
   * Normalizes watch events into structured connector events (`file_created`, `file_modified`).
   * Consumes glob pattern filters and enforces `maxFileSize` limits before emitting payloads.
   */
  private async _startWatching(path: string): Promise<void> {
    try {
      this.watcher = watch(
        path,
        {
          recursive: this._config.watchOptions?.recursive ?? true,
          persistent: this._config.watchOptions?.persistent ?? true,
        },
        async (eventType, filename) => {
          if (!filename) return;

          const filepath = join(path, filename);

          try {
            // Check if file matches pattern
            if (!this._matchesPattern(filename)) {
              return;
            }

            // Check if file exists and is readable
            const stats = await fs.stat(filepath);
            if (!stats.isFile()) {
              return;
            }

            // Check file size
            if (stats.size > (this._config.maxFileSize || 10 * 1024 * 1024)) {
              this.emit('error', new Error(`File ${filename} exceeds maximum size`));
              return;
            }

            // Read and emit the file content
            const content = await this._readFile(filepath);

            this._emitEvent({
              id: uuidv4(),
              type: eventType === 'rename' ? 'file_created' : 'file_modified',
              timestamp: Date.now(),
              payload: content,
              metadata: {
                filepath,
                filename,
                size: stats.size,
                modified: stats.mtime.toISOString(),
              },
            });
          } catch (error: unknown) {
            if (error.code !== 'ENOENT') {
              this.emit('error', error);
            }
          }
        }
      );

      this.watcher.on('error', (error) => {
        this.emit('error', error);
      });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Schedules periodic reads when operating in polling mode.
   *
   * Useful for environments without reliable filesystem notifications (e.g., network shares).
   */
  private async _startPolling(path: string): Promise<void> {
    const poll = async () => {
      try {
        await this._readOnce(path);
      } catch (error) {
        this.emit('error', error);
      }
    };

    // Initial read
    await poll();

    // Set up polling
    this.pollInterval = setInterval(poll, this._config.pollInterval);
  }

  /**
   * Reads a single file or directory snapshot.
   *
   * Applies pattern matching, change detection, and size limits before emitting `file_read` events.
   */
  private async _readOnce(path: string): Promise<void> {
    try {
      const stats = await fs.stat(path);

      if (stats.isFile()) {
        // Read single file
        const content = await this._readFile(path);
        this._emitEvent({
          id: uuidv4(),
          type: 'file_read',
          timestamp: Date.now(),
          payload: content,
          metadata: {
            filepath: path,
            filename: basename(path),
            size: stats.size,
            modified: stats.mtime.toISOString(),
          },
        });
      } else if (stats.isDirectory()) {
        // Read all files in directory
        const files = await fs.readdir(path);
        
        for (const filename of files) {
          if (!this._matchesPattern(filename)) {
            continue;
          }

          const filepath = join(path, filename);
          const fileStats = await fs.stat(filepath);

          if (!fileStats.isFile()) {
            continue;
          }

          // Check if file was modified since last read
          const lastRead = this.lastReadTime.get(filepath) || 0;
          if (fileStats.mtime.getTime() <= lastRead) {
            continue;
          }

          // Check file size
          if (fileStats.size > (this._config.maxFileSize || 10 * 1024 * 1024)) {
            this.emit('error', new Error(`File ${filename} exceeds maximum size`));
            continue;
          }

          const content = await this._readFile(filepath);
          this.lastReadTime.set(filepath, fileStats.mtime.getTime());

          this._emitEvent({
            id: uuidv4(),
            type: 'file_read',
            timestamp: Date.now(),
            payload: content,
            metadata: {
              filepath,
              filename,
              size: fileStats.size,
              modified: fileStats.mtime.toISOString(),
            },
          });
        }
      }
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Loads and parses a file based on configured format.
   *
   * Supports binary, JSON, CSV, and text formats. Extend to plug custom parsers (e.g., Avro).
   */
  private async _readFile(filepath: string): Promise<unknown> {
    if (this._config.format === 'binary') {
      return fs.readFile(filepath);
    }

    const encoding: BufferEncoding = this._config.encoding ?? 'utf8';
    const content = await fs.readFile(filepath, { encoding });

    switch (this._config.format) {
      case 'json':
        return JSON.parse(content);
      case 'csv':
        return this._parseCsv(content);
      case 'text':
      default:
        return content;
    }
  }

  /**
   * Serializes outbound data for file persistence.
   *
   * Mirrors `_readFile()` formats to ensure round-trip compatibility for pipelines.
   */
  private _formatData(data: unknown): string | Buffer {
    switch (this._config.format) {
      case 'json':
        return JSON.stringify(data, null, 2);
      case 'csv':
        return this._formatCsv(data);
      case 'binary':
        return Buffer.isBuffer(data) ? data : Buffer.from(String(data));
      case 'text':
      default:
        return typeof data === 'string' ? data : JSON.stringify(data);
    }
  }

  /**
   * Parses CSV file content into objects.
   */
  private _parseCsv(content: string): unknown[] {
    const lines = content.split('\n').filter(line => line.trim());
    if (lines.length === 0) return [];

    const headers = lines[0].split(',').map(h => h.trim());
    const rows = lines.slice(1).map(line => {
      const values = line.split(',').map(v => v.trim());
      const row: Record<string, string> = {};
      headers.forEach((header, index) => {
        row[header] = values[index] || '';
      });
      return row;
    });

    return rows;
  }

  /**
   * Serializes array of records into CSV format.
   */
  private _formatCsv(data: unknown): string {
    if (!Array.isArray(data) || data.length === 0) {
      return '';
    }

    const headers = Object.keys(data[0]);
    const rows = data.map(row => 
      headers.map(header => String(row[header] || '')).join(',')
    );

    return [headers.join(','), ...rows].join('\n');
  }

  /**
   * Evaluates filename against configured glob pattern.
   */
  private _matchesPattern(filename: string): boolean {
    const pattern = this._config.pattern || '*';
    
    if (pattern === '*') {
      return true;
    }

    // Simple glob pattern matching
    const regex = new RegExp(
      '^' + pattern.replace(/\*/g, '.*').replace(/\?/g, '.') + '$'
    );
    
    return regex.test(filename);
  }

  /**
   * Resolves file extension to use for writes.
   */
  private _getFileExtension(): string {
    switch (this._config.format) {
      case 'json':
        return 'json';
      case 'csv':
        return 'csv';
      case 'text':
        return 'txt';
      case 'binary':
        return 'bin';
      default:
        return 'txt';
    }
  }

  /** @inheritdoc */
  public override async destroy(): Promise<void> {
    this.lastReadTime.clear();
    await super.destroy();
  }
}
