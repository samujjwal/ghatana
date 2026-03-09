/**
 * Data Caching Layer
 * Provides in-memory caching with TTL and persistence
 */

/**
 * Cache entry with TTL metadata
 */
export interface CacheEntry<T> {
  data: T;
  timestamp: number;
  ttl: number;
}

/**
 * In-memory cache manager with TTL support
 * 
 * Provides fast caching with automatic expiration.
 * Use for temporary data that doesn't need persistence across sessions.
 * 
 * @example
 * ```ts
 * const cache = new CacheManager();
 * 
 * // Set with default TTL (5 minutes)
 * cache.set('user-123', userData);
 * 
 * // Set with custom TTL (1 hour)
 * cache.set('config', configData, 60 * 60 * 1000);
 * 
 * // Get cached data
 * const user = cache.get<User>('user-123');
 * 
 * // Invalidate by pattern
 * cache.invalidatePattern('^user-');
 * ```
 */
export class CacheManager {
  private cache: Map<string, CacheEntry<unknown>> = new Map();
  private defaultTTL = 5 * 60 * 1000; // 5 minutes

  /**
   * Get a cached value by key
   * 
   * @template T - Type of cached data
   * @param key - Cache key
   * @returns Cached data or null if not found or expired
   */
  get<T>(key: string): T | null {
    const entry = this.cache.get(key) as CacheEntry<T> | undefined;

    if (!entry) {
      return null;
    }

    // Check if expired
    if (Date.now() - entry.timestamp > entry.ttl) {
      this.cache.delete(key);
      return null;
    }

    return entry.data;
  }

  /**
   * Set a cached value with optional TTL
   * 
   * @template T - Type of data to cache
   * @param key - Cache key
   * @param data - Data to cache
   * @param ttl - Time to live in milliseconds (default: 5 minutes)
   */
  set<T>(key: string, data: T, ttl: number = this.defaultTTL): void {
    this.cache.set(key, {
      data,
      timestamp: Date.now(),
      ttl,
    });
  }

  /**
   * Delete a cached value by key
   * 
   * @param key - Cache key to delete
   */
  delete(key: string): void {
    this.cache.delete(key);
  }

  /**
   * Clear all cached entries
   */
  clear(): void {
    this.cache.clear();
  }

  /**
   * Get the number of cached entries
   * 
   * @returns Cache size
   */
  size(): number {
    return this.cache.size;
  }

  /**
   * Get all cache keys
   * 
   * @returns Array of cache keys
   */
  keys(): string[] {
    return Array.from(this.cache.keys());
  }

  /**
   * Invalidate cache entries matching a pattern
   * 
   * @param pattern - RegEx pattern to match keys
   * @example
   * ```ts
   * // Invalidate all user-related cache
   * cache.invalidatePattern('^user-');
   * 
   * // Invalidate all cache ending with '-temp'
   * cache.invalidatePattern('-temp$');
   * ```
   */
  invalidatePattern(pattern: string): void {
    const regex = new RegExp(pattern);
    const keysToDelete: string[] = [];

    this.cache.forEach((_, key) => {
      if (regex.test(key)) {
        keysToDelete.push(key);
      }
    });

    keysToDelete.forEach(key => this.cache.delete(key));
  }
}

/**
 * Persistent cache using localStorage
 * 
 * Provides caching with localStorage persistence across sessions.
 * Use for data that should survive page reloads.
 * 
 * @example
 * ```ts
 * const persistentCache = new PersistentCache();
 * 
 * // Set with 1 hour TTL
 * persistentCache.set('user-prefs', preferences, 60 * 60 * 1000);
 * 
 * // Get persisted data (survives page reload)
 * const prefs = persistentCache.get<UserPreferences>('user-prefs');
 * 
 * // Clear all persisted cache
 * persistentCache.clear();
 * ```
 */
export class PersistentCache {
  private prefix = 'devsecops-cache:';

  /**
   * Get a persisted value by key
   * 
   * @template T - Type of persisted data
   * @param key - Cache key
   * @returns Persisted data or null if not found or expired
   */
  get<T>(key: string): T | null {
    try {
      const stored = localStorage.getItem(this.prefix + key);
      if (!stored) return null;

      const entry = JSON.parse(stored);
      if (Date.now() - entry.timestamp > entry.ttl) {
        localStorage.removeItem(this.prefix + key);
        return null;
      }

      return entry.data;
    } catch (error) {
      console.warn('Failed to get persisted cache:', error);
      return null;
    }
  }

  /**
   * Set a persisted value with optional TTL
   * 
   * @template T - Type of data to persist
   * @param key - Cache key
   * @param data - Data to persist
   * @param ttl - Time to live in milliseconds (default: 24 hours)
   */
  set<T>(key: string, data: T, ttl: number = 24 * 60 * 60 * 1000): void {
    try {
      const entry = {
        data,
        timestamp: Date.now(),
        ttl,
      };
      localStorage.setItem(this.prefix + key, JSON.stringify(entry));
    } catch (error) {
      console.warn('Failed to set persisted cache:', error);
    }
  }

  /**
   * Delete a persisted value by key
   * 
   * @param key - Cache key to delete
   */
  delete(key: string): void {
    localStorage.removeItem(this.prefix + key);
  }

  /**
   * Clear all persisted cache entries with this cache's prefix
   */
  clear(): void {
    const keys: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(this.prefix)) {
        keys.push(key);
      }
    }
    keys.forEach(key => localStorage.removeItem(key));
  }
}

// Export singleton instances
export const memoryCache = new CacheManager();
export const persistentCache = new PersistentCache();
