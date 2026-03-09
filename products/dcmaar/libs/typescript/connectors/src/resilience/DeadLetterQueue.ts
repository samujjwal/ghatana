import { EventEmitter } from 'events';
import { Event } from '../types';

/**
 * @fileoverview In-memory dead letter queue for failed connector events.
 *
 * Captures failed deliveries, tracks retry metadata, and provides observability hooks. Pair with
 * `RetryPolicy` for automated replay and `Telemetry` to trace recovery workflows. Supports optional
 * persistence for durable storage across restarts.
 *
 * @see {@link DeadLetterQueue}
 * @see {@link DeadLetterEntry}
 * @see {@link ../resilience/RetryPolicy.RetryPolicy | RetryPolicy}
 */

/**
 * Stored entry capturing failed event and retry metadata.
 */
export interface DeadLetterEntry<T = any> {
  id: string;
  event: Event<T>;
  error: {
    message: string;
    stack?: string;
    code?: string;
  };
  attempts: number;
  firstAttempt: number;
  lastAttempt: number;
  metadata?: Record<string, any>;
}

/**
 * Configuration options for `DeadLetterQueue`.
 */
export interface DeadLetterQueueConfig {
  /** Maximum stored entries (default 1000). */
  maxSize?: number;
  /** Entry TTL in ms (default 24h). */
  ttl?: number;
  /** Enable periodic cleanup (default true). */
  autoCleanup?: boolean;
  /** Cleanup interval ms (default 1h). */
  cleanupInterval?: number;
  /** Optional persistence strategy. */
  persistence?: {
    enabled: boolean;
    path?: string;
    saveInterval?: number;
  };
}

/**
 * In-memory dead letter queue for failed connector events.
 *
 * **Example (basic usage):**
 * ```ts
 * const dlq = new DeadLetterQueue({ maxSize: 500 });
 * dlq.add(event, new Error('Failed to deliver'));
 * const stats = dlq.getStats();
 * console.log(stats.total);
 * ```
 *
 * **Example (persistent replay loop):**
 * ```ts
 * const dlq = new DeadLetterQueue({ persistence: { enabled: true, path: '/tmp/dlq.json' } });
 * await dlq.load();
 * await dlq.retryAll(async event => send(event));
 * await dlq.destroy();
 * ```
 */
export class DeadLetterQueue extends EventEmitter {
  private entries: Map<string, DeadLetterEntry> = new Map();
  private config: Required<Omit<DeadLetterQueueConfig, 'persistence'>> & Pick<DeadLetterQueueConfig, 'persistence'>;
  private cleanupTimer: NodeJS.Timeout | null = null;
  private saveTimer: NodeJS.Timeout | null = null;

  /**
   * @param {DeadLetterQueueConfig} config - Queue configuration
   */
  constructor(config: DeadLetterQueueConfig = {}) {
    super();
    this.config = {
      maxSize: config.maxSize ?? 1000,
      ttl: config.ttl ?? 86400000, // 24 hours
      autoCleanup: config.autoCleanup ?? true,
      cleanupInterval: config.cleanupInterval ?? 3600000, // 1 hour
      persistence: config.persistence,
    };

    if (this.config.autoCleanup) {
      this._startCleanup();
    }

    if (this.config.persistence?.enabled) {
      this._startPersistence();
    }
  }

  /**
   * Adds failed event entry to queue (evicting oldest if at capacity).
   *
   * Persists error metadata and emit events to drive dashboards or alerting pipelines.
   *
   * @param {Event<T>} event - Original event
   * @param {Error} error - Failure reason
   * @param {number} [attempts=1] - Attempt count
   * @param {Record<string, any>} [metadata]
   * @returns {string} Entry ID
   * @fires DeadLetterQueue#entryAdded
   * @fires DeadLetterQueue#entryEvicted
   */
  add<T = any>(event: Event<T>, error: Error, attempts: number = 1, metadata?: Record<string, any>): string {
    // Check if we're at capacity
    if (this.entries.size >= this.config.maxSize) {
      // Remove oldest entry
      const oldestKey = this.entries.keys().next().value;
      if (oldestKey) {
        this.entries.delete(oldestKey);
        this.emit('entryEvicted', { id: oldestKey });
      }
    }

    const entry: DeadLetterEntry<T> = {
      id: event.id,
      event,
      error: {
        message: error.message,
        stack: error.stack,
        code: (error as any).code,
      },
      attempts,
      firstAttempt: Date.now(),
      lastAttempt: Date.now(),
      metadata,
    };

    this.entries.set(entry.id, entry);
    this.emit('entryAdded', { entry });

    return entry.id;
  }

  /**
   * Retrieves entry by identifier.
   */
  get(id: string): DeadLetterEntry | undefined {
    return this.entries.get(id);
  }

  /**
   * Returns array copy of all entries.
   */
  getAll(): DeadLetterEntry[] {
    return Array.from(this.entries.values());
  }

  /**
   * Filters entries using predicate.
   *
   * Useful for targeted replays (e.g., filter by error code) or monitoring dashboards.
   */
  filter(predicate: (entry: DeadLetterEntry) => boolean): DeadLetterEntry[] {
    return this.getAll().filter(predicate);
  }

  /**
   * Removes entry and emits removal event.
   *
   * Enables external remediation flows to purge recovered events.
   */
  remove(id: string): boolean {
    const existed = this.entries.delete(id);
    if (existed) {
      this.emit('entryRemoved', { id });
    }
    return existed;
  }

  /**
   * Clears queue and emits cleared event.
   */
  clear(): void {
    const count = this.entries.size;
    this.entries.clear();
    this.emit('cleared', { count });
  }

  /**
   * Returns number of stored entries.
   */
  size(): number {
    return this.entries.size;
  }

  /**
   * Indicates whether queue currently empty.
   */
  isEmpty(): boolean {
    return this.entries.size === 0;
  }

  /**
   * Attempts replay of specific entry using provided retry function.
   *
   * @fires DeadLetterQueue#retrySuccess
   * @fires DeadLetterQueue#retryFailed
   */
  async retry<T = any>(
    id: string,
    retryFn: (event: Event<T>) => Promise<void>
  ): Promise<boolean> {
    const entry = this.entries.get(id);
    if (!entry) {
      return false;
    }

    try {
      await retryFn(entry.event);
      this.remove(id);
      this.emit('retrySuccess', { id, entry });
      return true;
    } catch (error) {
      entry.attempts++;
      entry.lastAttempt = Date.now();
      entry.error = {
        message: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
        code: (error as any)?.code,
      };
      this.emit('retryFailed', { id, entry, error });
      return false;
    }
  }

  /**
   * Retries all entries sequentially using provided function.
   *
   * @returns {Promise<{ succeeded: number; failed: number }>} Summary
   * @fires DeadLetterQueue#retryAllCompleted
   */
  async retryAll<T = any>(
    retryFn: (event: Event<T>) => Promise<void>
  ): Promise<{ succeeded: number; failed: number }> {
    const entries = this.getAll();
    let succeeded = 0;
    let failed = 0;

    for (const entry of entries) {
      const success = await this.retry(entry.id, retryFn);
      if (success) {
        succeeded++;
      } else {
        failed++;
      }
    }

    this.emit('retryAllCompleted', { succeeded, failed, total: entries.length });
    return { succeeded, failed };
  }

  /**
   * Computes queue statistics for monitoring.
   *
   * Aggregates counts by error signature, age buckets, and attempts—ideal for Prometheus exporters or
   * operational dashboards.
   */
  getStats() {
    const entries = this.getAll();
    const now = Date.now();

    const byError = new Map<string, number>();
    const byAge = { recent: 0, old: 0, expired: 0 };
    let totalAttempts = 0;

    for (const entry of entries) {
      // Count by error message
      const errorKey = entry.error.message;
      byError.set(errorKey, (byError.get(errorKey) || 0) + 1);

      // Count by age
      const age = now - entry.firstAttempt;
      if (age > this.config.ttl) {
        byAge.expired++;
      } else if (age > this.config.ttl / 2) {
        byAge.old++;
      } else {
        byAge.recent++;
      }

      totalAttempts += entry.attempts;
    }

    return {
      total: entries.length,
      maxSize: this.config.maxSize,
      utilizationPercent: (entries.length / this.config.maxSize) * 100,
      averageAttempts: entries.length > 0 ? totalAttempts / entries.length : 0,
      byError: Object.fromEntries(byError),
      byAge,
    };
  }

  /**
   * Removes entries exceeding TTL and emits cleanup events.
   *
   * Prevents unbounded memory growth while surfacing expiration metrics.
   */
  cleanup(): number {
    const now = Date.now();
    const cutoff = now - this.config.ttl;
    let removed = 0;

    for (const [id, entry] of this.entries.entries()) {
      if (entry.firstAttempt < cutoff) {
        this.entries.delete(id);
        removed++;
        this.emit('entryExpired', { id, entry });
      }
    }

    if (removed > 0) {
      this.emit('cleanupCompleted', { removed, remaining: this.entries.size });
    }

    return removed;
  }

  /**
   * Serializes entries to JSON string.
   *
   * Supports exporting DLQ state for diagnostics or manual replay via tooling.
   */
  export(): string {
    return JSON.stringify(this.getAll(), null, 2);
  }

  /**
   * Deserializes entries from JSON and appends to queue.
   *
   * Returns the number of entries successfully imported; respects `maxSize` to avoid overfilling.
   */
  import(json: string): number {
    try {
      const entries: DeadLetterEntry[] = JSON.parse(json);
      let imported = 0;

      for (const entry of entries) {
        if (this.entries.size >= this.config.maxSize) {
          break;
        }
        this.entries.set(entry.id, entry);
        imported++;
      }

      this.emit('imported', { count: imported });
      return imported;
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Starts periodic cleanup timer.
   */
  private _startCleanup(): void {
    if (this.cleanupTimer) {
      return;
    }

    this.cleanupTimer = setInterval(() => {
      this.cleanup();
    }, this.config.cleanupInterval);
  }

  /**
   * Stops cleanup timer.
   */
  private _stopCleanup(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }
  }

  /**
   * Starts persistence timer if enabled.
   *
   * Periodically invokes `_save()` to checkpoint queue contents.
   */
  private _startPersistence(): void {
    if (!this.config.persistence?.enabled || this.saveTimer) {
      return;
    }

    const interval = this.config.persistence.saveInterval || 60000; // Default 1 minute

    this.saveTimer = setInterval(() => {
      this._save();
    }, interval);
  }

  /**
   * Stops persistence timer.
   */
  private _stopPersistence(): void {
    if (this.saveTimer) {
      clearInterval(this.saveTimer);
      this.saveTimer = null;
    }
  }

  /**
   * Persists entries to disk, emitting `saved` event.
   *
   * Safe to call manually (e.g., before shutdown) and is idempotent when persistence is disabled.
   */
  private async _save(): Promise<void> {
    if (!this.config.persistence?.enabled || !this.config.persistence.path) {
      return;
    }

    try {
      const fs = await import('fs/promises');
      const data = this.export();
      await fs.writeFile(this.config.persistence.path, data, 'utf8');
      this.emit('saved', { path: this.config.persistence.path, count: this.entries.size });
    } catch (error) {
      this.emit('error', error);
    }
  }

  /**
   * Loads entries from persisted file.
   *
   * Gracefully handles missing files (`ENOENT`) so environments can bootstrap without DLQ state.
   */
  async load(): Promise<number> {
    if (!this.config.persistence?.enabled || !this.config.persistence.path) {
      return 0;
    }

    try {
      const fs = await import('fs/promises');
      const data = await fs.readFile(this.config.persistence.path, 'utf8');
      return this.import(data);
    } catch (error: unknown) {
      if (error.code !== 'ENOENT') {
        this.emit('error', error);
      }
      return 0;
    }
  }

  /**
   * Stops background tasks, persists if needed, and clears state.
   *
   * Ensures timers, file handles, and listeners are cleaned up during service shutdown.
   */
  async destroy(): Promise<void> {
    this._stopCleanup();
    this._stopPersistence();

    if (this.config.persistence?.enabled) {
      await this._save();
    }

    this.entries.clear();
    this.removeAllListeners();
  }
}
