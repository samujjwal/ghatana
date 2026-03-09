/**
 * TutorPutor Mobile - MMKV Storage Adapter
 *
 * High-performance key-value storage using react-native-mmkv.
 * Used for caching small data like user preferences and sync metadata.
 *
 * @doc.type module
 * @doc.purpose MMKV storage for React Native
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { MMKV } from 'react-native-mmkv';

/**
 * MMKV storage instance configuration.
 */
export interface MMKVConfig {
  /** Storage ID for isolation */
  id: string;
  /** Encryption key (optional) */
  encryptionKey?: string;
}

/**
 * Cached item with metadata.
 */
export interface CachedItem<T> {
  data: T;
  cachedAt: number;
  expiresAt: number;
  version: number;
}

/**
 * MMKV-based storage adapter for offline functionality.
 */
export class MMKVStorageAdapter {
  private storage: MMKV;
  private readonly defaultTtlMs: number = 24 * 60 * 60 * 1000; // 24 hours

  constructor(config: MMKVConfig = { id: 'tutorputor-cache' }) {
    this.storage = new MMKV({
      id: config.id,
      encryptionKey: config.encryptionKey,
    });
  }

  /**
   * Get an item from storage.
   */
  get<T>(key: string): T | null {
    try {
      const json = this.storage.getString(key);
      if (!json) return null;

      const item = JSON.parse(json) as CachedItem<T>;
      
      // Check expiration
      if (Date.now() > item.expiresAt) {
        this.remove(key);
        return null;
      }

      return item.data;
    } catch (error) {
      console.error(`[MMKV] Failed to get key ${key}:`, error);
      return null;
    }
  }

  /**
   * Get item with metadata.
   */
  getWithMeta<T>(key: string): CachedItem<T> | null {
    try {
      const json = this.storage.getString(key);
      if (!json) return null;

      const item = JSON.parse(json) as CachedItem<T>;
      
      // Check expiration
      if (Date.now() > item.expiresAt) {
        this.remove(key);
        return null;
      }

      return item;
    } catch (error) {
      console.error(`[MMKV] Failed to get key ${key}:`, error);
      return null;
    }
  }

  /**
   * Set an item in storage.
   */
  set<T>(key: string, data: T, ttlMs: number = this.defaultTtlMs): void {
    try {
      const existing = this.getWithMeta<T>(key);
      
      const item: CachedItem<T> = {
        data,
        cachedAt: Date.now(),
        expiresAt: Date.now() + ttlMs,
        version: existing ? existing.version + 1 : 1,
      };

      this.storage.set(key, JSON.stringify(item));
    } catch (error) {
      console.error(`[MMKV] Failed to set key ${key}:`, error);
    }
  }

  /**
   * Remove an item from storage.
   */
  remove(key: string): void {
    try {
      this.storage.delete(key);
    } catch (error) {
      console.error(`[MMKV] Failed to remove key ${key}:`, error);
    }
  }

  /**
   * Check if a key exists.
   */
  has(key: string): boolean {
    return this.storage.contains(key);
  }

  /**
   * Get all keys.
   */
  getAllKeys(): string[] {
    return this.storage.getAllKeys();
  }

  /**
   * Get keys matching a prefix.
   */
  getKeysByPrefix(prefix: string): string[] {
    return this.getAllKeys().filter((key) => key.startsWith(prefix));
  }

  /**
   * Clear all items.
   */
  clear(): void {
    this.storage.clearAll();
  }

  /**
   * Evict expired items.
   */
  evictExpired(): number {
    const keys = this.getAllKeys();
    let evictedCount = 0;

    for (const key of keys) {
      try {
        const json = this.storage.getString(key);
        if (!json) continue;

        const item = JSON.parse(json) as CachedItem<unknown>;
        if (Date.now() > item.expiresAt) {
          this.storage.delete(key);
          evictedCount++;
        }
      } catch (error) {
        // Skip invalid entries
      }
    }

    return evictedCount;
  }

  /**
   * Get storage size in bytes (approximate).
   */
  getStorageSize(): number {
    const keys = this.getAllKeys();
    let totalSize = 0;

    for (const key of keys) {
      const json = this.storage.getString(key);
      if (json) {
        totalSize += key.length + json.length;
      }
    }

    return totalSize;
  }
}

/**
 * Pre-configured storage instances.
 */
export const cacheStorage = new MMKVStorageAdapter({ id: 'tutorputor-cache' });
export const userStorage = new MMKVStorageAdapter({ id: 'tutorputor-user' });
export const syncStorage = new MMKVStorageAdapter({ id: 'tutorputor-sync' });

/**
 * Cache key utilities.
 */
export const CacheKeys = {
  module: (id: string) => `module:${id}`,
  lesson: (moduleId: string, lessonId: string) => `lesson:${moduleId}:${lessonId}`,
  progress: (moduleId: string) => `progress:${moduleId}`,
  userProfile: () => 'user:profile',
  lastSync: () => 'sync:lastSync',
  pendingCount: () => 'sync:pendingCount',
} as const;
