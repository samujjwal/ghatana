/**
 * @fileoverview Extension-specific storage provider using chrome.storage.local API.
 *
 * Implements StorageProvider interface for browser extensions, providing
 * persistent storage across browser sessions using the chrome.storage.local API.
 *
 * **Platform**: Browser Extensions (Chrome, Firefox, Edge, Safari)
 * **API**: chrome.storage.local or browser.storage.local (webextension-polyfill)
 *
 * @module connectors/platform/ExtensionStorageProvider
 * @since Phase 2.1
 */

/* eslint-disable @typescript-eslint/no-unsafe-assignment */
/* eslint-disable @typescript-eslint/no-unsafe-member-access */

import browser from 'webextension-polyfill';

// TODO: Import from @ghatana/dcmaar-connectors when available
// import type { StorageProvider, StorageSetOptions, StorageStats } from '@ghatana/dcmaar-connectors';

/**
 * Temporary storage types until @ghatana/dcmaar-connectors exports are available
 * FIXME: Replace with actual imports once exports are available
 */
export interface StorageSetOptions {
  /** Time-to-live in milliseconds */
  ttl?: number;
  /** Whether to overwrite existing value */
  overwrite?: boolean;
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

export interface StorageStats {
  /** Number of keys stored */
  keys: number;
  /** Bytes used */
  bytesUsed: number;
  /** Bytes available (if known) */
  bytesAvailable?: number;
  /** Storage type */
  type: string;
}

export interface StorageProvider {
  get<T = unknown>(key: string): Promise<T | undefined>;
  set<T = unknown>(key: string, value: T, options?: StorageSetOptions): Promise<void>;
  delete(key: string): Promise<boolean>;
  has(key: string): Promise<boolean>;
  keys(prefix?: string): Promise<string[]>;
  clear(prefix?: string): Promise<number>;
  getMany<T = unknown>(keys: string[]): Promise<Map<string, T>>;
  setMany<T = unknown>(entries: Map<string, T>, options?: StorageSetOptions): Promise<void>;
  getStats(): Promise<StorageStats>;
}

/**
 * Storage entry with expiration metadata.
 */
interface StorageEntry<T = unknown> {
  value: T;
  createdAt: number;
  expiresAt?: number;
  metadata?: Record<string, unknown>;
}

/**
 * Extension storage provider using chrome.storage.local.
 *
 * **Features**:
 * - Persistent across browser sessions
 * - Synchronized across browser windows/tabs
 * - TTL support with automatic cleanup
 * - Batch operations
 * - Storage quota management
 *
 * **Limitations**:
 * - Subject to browser storage quotas (typically 5-10MB)
 * - Chrome: ~5MB (QUOTA_BYTES)
 * - Firefox: ~10MB
 * - Edge: ~5MB
 *
 * **Usage**:
 * ```typescript
 * import { ConnectorManager } from '@ghatana/dcmaar-connectors';
 * import { ExtensionStorageProvider } from './platform/ExtensionStorageProvider';
 *
 * const manager = new ConnectorManager({
 *   storage: new ExtensionStorageProvider()
 * });
 * ```
 */
export class ExtensionStorageProvider implements StorageProvider {
  private readonly prefix: string;

  /**
   * Creates a new ExtensionStorageProvider.
   *
   * @param options - Configuration options
   */
  constructor(options?: {
    /**
     * Key prefix for namespacing (default: 'dcmaar:')
     */
    prefix?: string;
  }) {
    this.prefix = options?.prefix ?? 'dcmaar:';
  }

  /**
   * Wraps key with prefix.
   */
  private prefixKey(key: string): string {
    return `${this.prefix}${key}`;
  }

  /**
   * Unwraps prefixed key.
   */
  private unprefixKey(key: string): string {
    if (key.startsWith(this.prefix)) {
      return key.slice(this.prefix.length);
    }
    return key;
  }

  /**
   * Checks if entry is expired.
   */
  private isExpired(entry: StorageEntry<unknown>): boolean {
    if (!entry.expiresAt) {
      return false;
    }
    return entry.expiresAt < Date.now();
  }

  async get<T = unknown>(key: string): Promise<T | undefined> {
    const prefixedKey = this.prefixKey(key);

    try {
      const result = await browser.storage.local.get(prefixedKey);
      const entry = result[prefixedKey] as StorageEntry<T> | undefined;

      if (!entry) {
        return undefined;
      }

      // Check expiration
      if (this.isExpired(entry)) {
        await this.delete(key);
        return undefined;
      }

      return entry.value;
    } catch (error) {
      console.error(`[ExtensionStorageProvider] Failed to get key '${key}':`, error);
      throw error;
    }
  }

  async set<T = unknown>(key: string, value: T, options?: StorageSetOptions): Promise<void> {
    const prefixedKey = this.prefixKey(key);

    // Check overwrite option
    if (options?.overwrite === false) {
      const exists = await this.has(key);
      if (exists) {
        throw new Error(`Key '${key}' already exists and overwrite is disabled`);
      }
    }

    const entry: StorageEntry<T> = {
      value,
      createdAt: Date.now(),
      metadata: options?.metadata as Record<string, unknown> | undefined,
    };

    if (options?.ttl) {
      entry.expiresAt = Date.now() + options.ttl;
    }

    try {
      await browser.storage.local.set({ [prefixedKey]: entry });
    } catch (error) {
      console.error(`[ExtensionStorageProvider] Failed to set key '${key}':`, error);
      throw error;
    }
  }

  async delete(key: string): Promise<boolean> {
    const prefixedKey = this.prefixKey(key);

    try {
      const exists = await this.has(key);
      if (!exists) {
        return false;
      }

      await browser.storage.local.remove(prefixedKey);
      return true;
    } catch (error) {
      console.error(`[ExtensionStorageProvider] Failed to delete key '${key}':`, error);
      throw error;
    }
  }

  async has(key: string): Promise<boolean> {
    const prefixedKey = this.prefixKey(key);

    try {
      const result = await browser.storage.local.get(prefixedKey);
      const entry = result[prefixedKey] as StorageEntry<unknown> | undefined;

      if (!entry) {
        return false;
      }

      if (this.isExpired(entry)) {
        await this.delete(key);
        return false;
      }

      return true;
    } catch (error) {
      console.error(`[ExtensionStorageProvider] Failed to check key '${key}':`, error);
      throw error;
    }
  }

  async keys(prefix?: string): Promise<string[]> {
    try {
      const allData = await browser.storage.local.get(null);
      const keys: string[] = [];

      for (const key of Object.keys(allData)) {
        if (key.startsWith(this.prefix)) {
          const unprefixed = this.unprefixKey(key);

          if (!prefix || unprefixed.startsWith(prefix)) {
            // Check if expired
            const entry = allData[key] as StorageEntry<unknown>;
            if (!this.isExpired(entry)) {
              keys.push(unprefixed);
            }
          }
        }
      }

      return keys;
    } catch (error) {
      console.error('[ExtensionStorageProvider] Failed to list keys:', error);
      throw error;
    }
  }

  async clear(prefix?: string): Promise<number> {
    try {
      const keysToDelete = await this.keys(prefix);

      if (keysToDelete.length === 0) {
        return 0;
      }

      const prefixedKeys = keysToDelete.map((k) => this.prefixKey(k));
      await browser.storage.local.remove(prefixedKeys);

      return keysToDelete.length;
    } catch (error) {
      console.error('[ExtensionStorageProvider] Failed to clear storage:', error);
      throw error;
    }
  }

  async getMany<T = unknown>(keys: string[]): Promise<Map<string, T>> {
    const prefixedKeys = keys.map((k) => this.prefixKey(k));
    const result = new Map<string, T>();

    try {
      const data = await browser.storage.local.get(prefixedKeys);

      for (const key of keys) {
        const prefixedKey = this.prefixKey(key);
        const entry = data[prefixedKey] as StorageEntry<T> | undefined;

        if (entry && !this.isExpired(entry)) {
          result.set(key, entry.value);
        }
      }

      return result;
    } catch (error) {
      console.error('[ExtensionStorageProvider] Failed to get many keys:', error);
      throw error;
    }
  }

  async setMany<T = unknown>(entries: Map<string, T>, options?: StorageSetOptions): Promise<void> {
    const storageData: Record<string, StorageEntry<T>> = {};

    for (const [key, value] of entries.entries()) {
      // Check overwrite option
      if (options?.overwrite === false) {
        const exists = await this.has(key);
        if (exists) {
          throw new Error(`Key '${key}' already exists and overwrite is disabled`);
        }
      }

      const entry: StorageEntry<T> = {
        value,
        createdAt: Date.now(),
        metadata: options?.metadata as Record<string, unknown> | undefined,
      };

      if (options?.ttl) {
        entry.expiresAt = Date.now() + options.ttl;
      }

      storageData[this.prefixKey(key)] = entry;
    }

    try {
      await browser.storage.local.set(storageData);
    } catch (error) {
      console.error('[ExtensionStorageProvider] Failed to set many keys:', error);
      throw error;
    }
  }

  async getStats(): Promise<StorageStats> {
    try {
      // Get all keys to count
      const allKeys = await this.keys();

      // Get bytes in use (Chrome/Firefox support this)
      let bytesInUse = 0;
      try {
        bytesInUse = await browser.storage.local.getBytesInUse(null);
      } catch {
        // getBytesInUse not supported (Firefox < 78)
        bytesInUse = 0;
      }

      // Chrome provides QUOTA_BYTES constant
      const quotaBytes =
        typeof chrome !== 'undefined' && chrome.storage?.local.QUOTA_BYTES
          ? chrome.storage.local.QUOTA_BYTES
          : undefined;

      return {
        keys: allKeys.length,
        bytesUsed: bytesInUse,
        bytesAvailable: quotaBytes ? quotaBytes - bytesInUse : undefined,
        type: 'extension',
      };
    } catch (error) {
      console.error('[ExtensionStorageProvider] Failed to get stats:', error);
      return {
        keys: 0,
        bytesUsed: 0,
        type: 'extension',
      };
    }
  }
}
