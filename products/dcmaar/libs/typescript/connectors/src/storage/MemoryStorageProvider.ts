/**
 * @fileoverview Memory-based storage provider for testing and development.
 *
 * Simple in-memory storage implementation using Map. Suitable for:
 * - Unit testing
 * - Development/debugging
 * - Ephemeral storage needs
 * - Default fallback when no platform storage available
 *
 * **Limitations**:
 * - Data lost on restart
 * - No persistence
 * - Limited by memory
 * - No cross-process sharing
 *
 * @module storage/MemoryStorageProvider
 * @since 1.1.0
 */

import { StorageProvider, StorageSetOptions, StorageStats } from './StorageProvider';

/**
 * Storage entry with metadata and TTL.
 */
interface StorageEntry<T = any> {
  value: T;
  createdAt: number;
  expiresAt?: number;
  metadata?: Record<string, any>;
}

/**
 * In-memory storage provider implementation.
 *
 * **Features**:
 * - Fast O(1) operations
 * - TTL support with automatic cleanup
 * - Full batch operations
 * - Prefix-based operations
 * - Memory statistics
 *
 * **Usage**:
 * ```typescript
 * const storage = new MemoryStorageProvider();
 *
 * await storage.set('key', { value: 123 });
 * const value = await storage.get('key');
 *
 * // With TTL
 * await storage.set('temp', data, { ttl: 60000 }); // Expires in 1 minute
 * ```
 */
export class MemoryStorageProvider implements StorageProvider {
  private store: Map<string, StorageEntry> = new Map();
  private cleanupInterval: NodeJS.Timeout | null = null;

  /**
   * Creates a new MemoryStorageProvider.
   *
   * @param options - Configuration options
   */
  constructor(
    options: {
      /**
       * Enable automatic cleanup of expired entries.
       * @default true
       */
      autoCleanup?: boolean;

      /**
       * Cleanup interval in milliseconds.
       * @default 60000 (1 minute)
       */
      cleanupIntervalMs?: number;
    } = {}
  ) {
    const autoCleanup = options.autoCleanup ?? true;
    const cleanupIntervalMs = options.cleanupIntervalMs ?? 60000;

    if (autoCleanup) {
      this.startAutoCleanup(cleanupIntervalMs);
    }
  }

  /**
   * Starts automatic cleanup of expired entries.
   */
  private startAutoCleanup(intervalMs: number): void {
    this.cleanupInterval = setInterval(() => {
      this.cleanupExpired();
    }, intervalMs);

    // Allow process to exit even if interval is running
    if (this.cleanupInterval.unref) {
      this.cleanupInterval.unref();
    }
  }

  /**
   * Stops automatic cleanup.
   */
  stopAutoCleanup(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = null;
    }
  }

  /**
   * Removes expired entries from storage.
   *
   * @returns Number of entries removed
   */
  private cleanupExpired(): number {
    const now = Date.now();
    let removed = 0;

    for (const [key, entry] of this.store.entries()) {
      if (entry.expiresAt && entry.expiresAt < now) {
        this.store.delete(key);
        removed++;
      }
    }

    return removed;
  }

  /**
   * Checks if an entry is expired.
   */
  private isExpired(entry: StorageEntry): boolean {
    if (!entry.expiresAt) {
      return false;
    }
    return entry.expiresAt < Date.now();
  }

  async get<T = any>(key: string): Promise<T | undefined> {
    const entry = this.store.get(key);

    if (!entry) {
      return undefined;
    }

    if (this.isExpired(entry)) {
      this.store.delete(key);
      return undefined;
    }

    return entry.value as T;
  }

  async set<T = any>(key: string, value: T, options?: StorageSetOptions): Promise<void> {
    // Check overwrite option
    if (options?.overwrite === false && this.store.has(key)) {
      throw new Error(`Key '${key}' already exists and overwrite is disabled`);
    }

    const entry: StorageEntry<T> = {
      value,
      createdAt: Date.now(),
      metadata: options?.metadata,
    };

    if (options?.ttl) {
      entry.expiresAt = Date.now() + options.ttl;
    }

    this.store.set(key, entry);
  }

  async delete(key: string): Promise<boolean> {
    return this.store.delete(key);
  }

  async has(key: string): Promise<boolean> {
    const entry = this.store.get(key);

    if (!entry) {
      return false;
    }

    if (this.isExpired(entry)) {
      this.store.delete(key);
      return false;
    }

    return true;
  }

  async keys(prefix?: string): Promise<string[]> {
    const allKeys = Array.from(this.store.keys());

    if (!prefix) {
      return allKeys;
    }

    return allKeys.filter((key) => key.startsWith(prefix));
  }

  async clear(prefix?: string): Promise<number> {
    if (!prefix) {
      const count = this.store.size;
      this.store.clear();
      return count;
    }

    const keysToDelete = await this.keys(prefix);
    keysToDelete.forEach((key) => this.store.delete(key));
    return keysToDelete.length;
  }

  async getMany<T = any>(keys: string[]): Promise<Map<string, T>> {
    const result = new Map<string, T>();

    for (const key of keys) {
      const value = await this.get<T>(key);
      if (value !== undefined) {
        result.set(key, value);
      }
    }

    return result;
  }

  async setMany<T = any>(entries: Map<string, T>, options?: StorageSetOptions): Promise<void> {
    for (const [key, value] of entries.entries()) {
      await this.set(key, value, options);
    }
  }

  async getStats(): Promise<StorageStats> {
    // Clean up expired entries first
    this.cleanupExpired();

    // Calculate approximate size
    let bytesUsed = 0;
    for (const [key, entry] of this.store.entries()) {
      // Rough estimate: key length + JSON.stringify length
      bytesUsed += key.length;
      bytesUsed += JSON.stringify(entry.value).length;
    }

    return {
      keys: this.store.size,
      bytesUsed,
      type: 'memory',
    };
  }

  /**
   * Destroys the storage provider and cleans up resources.
   */
  destroy(): void {
    this.stopAutoCleanup();
    this.store.clear();
  }
}
