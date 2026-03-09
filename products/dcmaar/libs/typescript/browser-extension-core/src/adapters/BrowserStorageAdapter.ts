/**
 * @fileoverview Browser Storage Adapter
 *
 * Thin wrapper over browser.storage API providing type-safe storage access.
 * No encryption, caching, or complex logic - just direct storage access.
 *
 * @module browser/storage/BrowserStorageAdapter
 */

import browser from "webextension-polyfill";

import type {
  StorageAdapter,
  PrefixedStorageAdapter,
  StorageChange,
  StorageChangeListener,
  StorageOptions,
  StorageQuota,
} from "./StorageAdapter.interface";

/**
 * Browser Storage Adapter implementation
 *
 * Provides type-safe access to browser.storage.local and browser.storage.sync.
 *
 * @example
 * ```typescript
 * const storage = new BrowserStorageAdapter();
 *
 * // Store data
 * await storage.set('user-preferences', { theme: 'dark' });
 *
 * // Retrieve data
 * const prefs = await storage.get<{ theme: string }>('user-preferences');
 *
 * // Listen for changes
 * storage.onChange((changes, area) => {
 *   console.log('Storage changed:', changes);
 * });
 * ```
 */
export class BrowserStorageAdapter implements StorageAdapter {
  private changeListeners = new Set<StorageChangeListener>();

  constructor() {
    // Set up storage change listener
    browser.storage.onChanged.addListener(this.handleStorageChange.bind(this));
  }

  /**
   * Get the appropriate storage area
   */
  private getStorageArea(area: "local" | "sync") {
    return area === "sync" ? browser.storage.sync : browser.storage.local;
  }

  /**
   * Get value from storage
   */
  async get<T>(key: string, options?: StorageOptions): Promise<T | undefined> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    const result = await storageArea.get(key);
    return result[key] as T | undefined;
  }

  /**
   * Get multiple values from storage
   */
  async getMany<T extends Record<string, unknown>>(
    keys: string[],
    options?: StorageOptions
  ): Promise<Partial<T>> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    const result = await storageArea.get(keys);
    return result as Partial<T>;
  }

  /**
   * Get all values from storage
   */
  async getAll<T extends Record<string, unknown>>(
    options?: StorageOptions
  ): Promise<T> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    const result = await storageArea.get(null);
    return result as T;
  }

  /**
   * Set value in storage
   */
  async set<T>(key: string, value: T, options?: StorageOptions): Promise<void> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    await storageArea.set({ [key]: value });
  }

  /**
   * Set multiple values in storage
   */
  async setMany<T extends Record<string, unknown>>(
    items: T,
    options?: StorageOptions
  ): Promise<void> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    await storageArea.set(items);
  }

  /**
   * Remove value from storage
   */
  async remove(key: string, options?: StorageOptions): Promise<void> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    await storageArea.remove(key);
  }

  /**
   * Remove multiple values from storage
   */
  async removeMany(keys: string[], options?: StorageOptions): Promise<void> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    await storageArea.remove(keys);
  }

  /**
   * Clear all values from storage
   */
  async clear(options?: StorageOptions): Promise<void> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    await storageArea.clear();
  }

  /**
   * Check if key exists in storage
   */
  async has(key: string, options?: StorageOptions): Promise<boolean> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);
    const result = await storageArea.get(key);
    return key in result;
  }

  /**
   * Get storage quota information
   */
  async getQuota(options?: StorageOptions): Promise<StorageQuota> {
    const area = options?.area || "local";
    const storageArea = this.getStorageArea(area);

    // Get bytes in use
    const bytesInUse = await storageArea.getBytesInUse();

    // Storage quotas (from Chrome/Firefox docs)
    const quota =
      area === "local"
        ? 10 * 1024 * 1024 // 10MB for local storage
        : 100 * 1024; // 100KB for sync storage

    return {
      quota,
      used: bytesInUse || 0,
      remaining: quota - (bytesInUse || 0),
    };
  }

  /**
   * Listen for storage changes
   */
  onChange(listener: StorageChangeListener): void {
    this.changeListeners.add(listener);
  }

  /**
   * Remove storage change listener
   */
  offChange(listener: StorageChangeListener): void {
    this.changeListeners.delete(listener);
  }

  /**
   * Handle storage change events from browser
   */
  private handleStorageChange(
    changes: Record<string, browser.Storage.StorageChange>,
    areaName: string
  ): void {
    const transformedChanges: Record<string, StorageChange> = {};

    for (const [key, change] of Object.entries(changes)) {
      transformedChanges[key] = {
        oldValue: (change as { oldValue?: unknown; newValue?: unknown })
          .oldValue,
        newValue: (change as { oldValue?: unknown; newValue?: unknown })
          .newValue,
      };
    }

    // Notify all listeners
    for (const listener of this.changeListeners) {
      listener(transformedChanges, areaName as "local" | "sync" | "managed");
    }
  }
}

/**
 * Prefixed Browser Storage Adapter
 *
 * Adds key prefix for namespacing storage keys.
 *
 * @example
 * ```typescript
 * const storage = new PrefixedBrowserStorageAdapter('myapp:');
 *
 * // Stores as 'myapp:config'
 * await storage.set('config', { theme: 'dark' });
 *
 * // Retrieves from 'myapp:config'
 * const config = await storage.get('config');
 * ```
 */
export class PrefixedBrowserStorageAdapter
  extends BrowserStorageAdapter
  implements PrefixedStorageAdapter
{
  readonly prefix: string;

  constructor(prefix: string = "") {
    super();
    this.prefix = prefix;
  }

  /**
   * Add prefix to key
   */
  private prefixKey(key: string): string {
    return this.prefix + key;
  }

  /**
   * Remove prefix from key
   */
  private unprefixKey(key: string): string {
    return key.startsWith(this.prefix) ? key.slice(this.prefix.length) : key;
  }

  /**
   * Get value from storage (with prefix)
   */
  async get<T>(key: string, options?: StorageOptions): Promise<T | undefined> {
    return super.get<T>(this.prefixKey(key), options);
  }

  /**
   * Get multiple values from storage (with prefix)
   */
  async getMany<T extends Record<string, unknown>>(
    keys: string[],
    options?: StorageOptions
  ): Promise<Partial<T>> {
    const prefixedKeys = keys.map((k) => this.prefixKey(k));
    const result = await super.getMany<Record<string, unknown>>(
      prefixedKeys,
      options
    );

    // Remove prefixes from result keys
    const unprefixed: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(result)) {
      unprefixed[this.unprefixKey(key)] = value;
    }

    return unprefixed as Partial<T>;
  }

  /**
   * Get all values from storage (only prefixed keys)
   */
  async getAll<T extends Record<string, unknown>>(
    options?: StorageOptions
  ): Promise<T> {
    const allData = await super.getAll<Record<string, unknown>>(options);

    // Filter for only prefixed keys and remove prefix
    const filtered: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(allData)) {
      if (key.startsWith(this.prefix)) {
        filtered[this.unprefixKey(key)] = value;
      }
    }

    return filtered as T;
  }

  /**
   * Set value in storage (with prefix)
   */
  async set<T>(key: string, value: T, options?: StorageOptions): Promise<void> {
    return super.set(this.prefixKey(key), value, options);
  }

  /**
   * Set multiple values in storage (with prefix)
   */
  async setMany<T extends Record<string, unknown>>(
    items: T,
    options?: StorageOptions
  ): Promise<void> {
    const prefixed: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(items)) {
      prefixed[this.prefixKey(key)] = value;
    }
    return super.setMany(prefixed, options);
  }

  /**
   * Remove value from storage (with prefix)
   */
  async remove(key: string, options?: StorageOptions): Promise<void> {
    return super.remove(this.prefixKey(key), options);
  }

  /**
   * Remove multiple values from storage (with prefix)
   */
  async removeMany(keys: string[], options?: StorageOptions): Promise<void> {
    const prefixedKeys = keys.map((k) => this.prefixKey(k));
    return super.removeMany(prefixedKeys, options);
  }

  /**
   * Check if key exists in storage (with prefix)
   */
  async has(key: string, options?: StorageOptions): Promise<boolean> {
    return super.has(this.prefixKey(key), options);
  }

  /**
   * Create a new adapter with a different prefix
   */
  withPrefix(prefix: string): PrefixedStorageAdapter {
    return new PrefixedBrowserStorageAdapter(prefix);
  }
}
