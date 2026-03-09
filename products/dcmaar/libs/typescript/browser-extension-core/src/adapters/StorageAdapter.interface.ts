/**
 * @fileoverview Storage Adapter Interface
 *
 * Thin wrapper over browser.storage API for extension storage.
 * Implementations should delegate directly to browser APIs with minimal logic.
 *
 * @module core/interfaces/StorageAdapter
 */

/**
 * Storage change event
 */
export interface StorageChange<T = unknown> {
  /** Old value (undefined if key didn't exist) */
  oldValue?: T;
  /** New value (undefined if key was deleted) */
  newValue?: T;
}

/**
 * Storage change listener
 */
export type StorageChangeListener = (
  changes: Record<string, StorageChange>,
  areaName: 'local' | 'sync' | 'managed'
) => void;

/**
 * Storage options
 */
export interface StorageOptions {
  /** Storage area ('local' or 'sync') */
  area?: 'local' | 'sync';
}

/**
 * Storage quota information
 */
export interface StorageQuota {
  /** Maximum bytes available */
  quota: number;
  /** Bytes currently used */
  used: number;
  /** Bytes remaining */
  remaining: number;
}

/**
 * Storage Adapter Interface
 *
 * Provides a simple, type-safe interface to browser extension storage.
 * Implementations should be thin wrappers over browser.storage API.
 *
 * No encryption, caching, or complex logic - just direct storage access.
 * For advanced features, use @ghatana/dcmaar-connectors storage abstraction.
 *
 * @example
 * ```typescript
 * class BrowserStorageAdapter implements StorageAdapter {
 *   async get<T>(key: string, options?: StorageOptions): Promise<T | undefined> {
 *     const area = options?.area || 'local';
 *     const result = await browser.storage[area].get(key);
 *     return result[key] as T | undefined;
 *   }
 *
 *   async set<T>(key: string, value: T, options?: StorageOptions): Promise<void> {
 *     const area = options?.area || 'local';
 *     await browser.storage[area].set({ [key]: value });
 *   }
 * }
 * ```
 */
export interface StorageAdapter {
  /**
   * Get value from storage
   *
   * @param key - Storage key
   * @param options - Storage options (area: local/sync)
   * @returns Promise resolving to value or undefined if not found
   */
  get<T>(key: string, options?: StorageOptions): Promise<T | undefined>;

  /**
   * Get multiple values from storage
   *
   * @param keys - Array of storage keys
   * @param options - Storage options (area: local/sync)
   * @returns Promise resolving to object with key-value pairs
   */
  getMany<T extends Record<string, unknown>>(
    keys: string[],
    options?: StorageOptions
  ): Promise<Partial<T>>;

  /**
   * Get all values from storage
   *
   * @param options - Storage options (area: local/sync)
   * @returns Promise resolving to object with all key-value pairs
   */
  getAll<T extends Record<string, unknown>>(options?: StorageOptions): Promise<T>;

  /**
   * Set value in storage
   *
   * @param key - Storage key
   * @param value - Value to store
   * @param options - Storage options (area: local/sync)
   */
  set<T>(key: string, value: T, options?: StorageOptions): Promise<void>;

  /**
   * Set multiple values in storage
   *
   * @param items - Object with key-value pairs to store
   * @param options - Storage options (area: local/sync)
   */
  setMany<T extends Record<string, unknown>>(items: T, options?: StorageOptions): Promise<void>;

  /**
   * Remove value from storage
   *
   * @param key - Storage key to remove
   * @param options - Storage options (area: local/sync)
   */
  remove(key: string, options?: StorageOptions): Promise<void>;

  /**
   * Remove multiple values from storage
   *
   * @param keys - Array of storage keys to remove
   * @param options - Storage options (area: local/sync)
   */
  removeMany(keys: string[], options?: StorageOptions): Promise<void>;

  /**
   * Clear all values from storage
   *
   * @param options - Storage options (area: local/sync)
   */
  clear(options?: StorageOptions): Promise<void>;

  /**
   * Check if key exists in storage
   *
   * @param key - Storage key to check
   * @param options - Storage options (area: local/sync)
   * @returns Promise resolving to true if key exists
   */
  has(key: string, options?: StorageOptions): Promise<boolean>;

  /**
   * Get storage quota information
   *
   * @param options - Storage options (area: local/sync)
   * @returns Promise resolving to quota information
   */
  getQuota(options?: StorageOptions): Promise<StorageQuota>;

  /**
   * Listen for storage changes
   *
   * @param listener - Function to call when storage changes
   */
  onChange(listener: StorageChangeListener): void;

  /**
   * Remove storage change listener
   *
   * @param listener - Listener to remove
   */
  offChange(listener: StorageChangeListener): void;
}

/**
 * Storage adapter with prefix support
 */
export interface PrefixedStorageAdapter extends StorageAdapter {
  /**
   * Key prefix for namespacing
   */
  readonly prefix: string;

  /**
   * Create a new adapter with a different prefix
   *
   * @param prefix - New prefix
   * @returns New storage adapter with the prefix
   */
  withPrefix(prefix: string): PrefixedStorageAdapter;
}
