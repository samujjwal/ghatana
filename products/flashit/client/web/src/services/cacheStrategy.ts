/**
 * Cache Strategy Service for Flashit Web
 * Multi-layer caching with intelligent invalidation
 *
 * @doc.type service
 * @doc.purpose Multi-layer caching strategy with TTL and invalidation
 * @doc.layer product
 * @doc.pattern CacheService
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

export type CacheLayer = 'memory' | 'session' | 'local' | 'indexeddb';
export type CachePolicy = 'cache-first' | 'network-first' | 'stale-while-revalidate';

export interface CacheConfig {
  defaultTTL: number;
  maxMemoryItems: number;
  maxLocalStorageSize: number;
  enableCompression: boolean;
  enableEncryption: boolean;
}

export interface CacheEntry<T = unknown> {
  key: string;
  value: T;
  ttl: number;
  createdAt: number;
  expiresAt: number;
  accessCount: number;
  lastAccessedAt: number;
  size: number;
  tags: string[];
  layer: CacheLayer;
}

export interface CacheOptions {
  ttl?: number;
  layer?: CacheLayer;
  tags?: string[];
  policy?: CachePolicy;
  compress?: boolean;
}

export interface CacheStats {
  hits: number;
  misses: number;
  hitRate: number;
  totalEntries: number;
  memoryUsage: number;
  localStorageUsage: number;
}

export interface InvalidationOptions {
  pattern?: string;
  tags?: string[];
  olderThan?: Date;
}

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_CONFIG: CacheConfig = {
  defaultTTL: 300000, // 5 minutes
  maxMemoryItems: 1000,
  maxLocalStorageSize: 5 * 1024 * 1024, // 5MB
  enableCompression: true,
  enableEncryption: false,
};

const CACHE_PREFIX = 'flashit_cache_';
const METADATA_KEY = 'flashit_cache_meta';

// ============================================================================
// Memory Cache (LRU)
// ============================================================================

class LRUCache<T> {
  private cache: Map<string, CacheEntry<T>> = new Map();
  private maxItems: number;

  constructor(maxItems: number) {
    this.maxItems = maxItems;
  }

  get(key: string): CacheEntry<T> | undefined {
    const entry = this.cache.get(key);
    if (!entry) return undefined;

    // Check expiration
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return undefined;
    }

    // Update access stats
    entry.accessCount++;
    entry.lastAccessedAt = Date.now();

    // Move to end (most recently used)
    this.cache.delete(key);
    this.cache.set(key, entry);

    return entry;
  }

  set(key: string, entry: CacheEntry<T>): void {
    // Evict if at capacity
    if (this.cache.size >= this.maxItems && !this.cache.has(key)) {
      const firstKey = this.cache.keys().next().value;
      if (firstKey) this.cache.delete(firstKey);
    }

    this.cache.set(key, entry);
  }

  delete(key: string): boolean {
    return this.cache.delete(key);
  }

  clear(): void {
    this.cache.clear();
  }

  has(key: string): boolean {
    return this.cache.has(key);
  }

  size(): number {
    return this.cache.size;
  }

  entries(): IterableIterator<[string, CacheEntry<T>]> {
    return this.cache.entries();
  }

  keys(): IterableIterator<string> {
    return this.cache.keys();
  }
}

// ============================================================================
// Cache Strategy Service
// ============================================================================

/**
 * CacheStrategyService provides multi-layer caching
 */
class CacheStrategyService {
  private static instance: CacheStrategyService | null = null;

  private config: CacheConfig;
  private memoryCache: LRUCache<unknown>;
  private stats: CacheStats;
  private dbPromise: Promise<IDBDatabase> | null = null;

  private constructor(config: Partial<CacheConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.memoryCache = new LRUCache(this.config.maxMemoryItems);
    this.stats = {
      hits: 0,
      misses: 0,
      hitRate: 0,
      totalEntries: 0,
      memoryUsage: 0,
      localStorageUsage: 0,
    };
  }

  /**
   * Get singleton instance
   */
  static getInstance(config?: Partial<CacheConfig>): CacheStrategyService {
    if (!this.instance) {
      this.instance = new CacheStrategyService(config);
    }
    return this.instance;
  }

  /**
   * Get value from cache
   */
  async get<T>(key: string, options: CacheOptions = {}): Promise<T | null> {
    const fullKey = this.getFullKey(key);
    const layer = options.layer || 'memory';

    let entry: CacheEntry<T> | null = null;

    // Try each layer based on strategy
    switch (layer) {
      case 'memory':
        entry = this.memoryCache.get(fullKey) as CacheEntry<T> | undefined ?? null;
        break;
      case 'session':
        entry = this.getFromSessionStorage<T>(fullKey);
        break;
      case 'local':
        entry = this.getFromLocalStorage<T>(fullKey);
        break;
      case 'indexeddb':
        entry = await this.getFromIndexedDB<T>(fullKey);
        break;
    }

    if (entry) {
      this.recordHit();
      return entry.value;
    }

    this.recordMiss();
    return null;
  }

  /**
   * Set value in cache
   */
  async set<T>(
    key: string,
    value: T,
    options: CacheOptions = {}
  ): Promise<void> {
    const fullKey = this.getFullKey(key);
    const ttl = options.ttl || this.config.defaultTTL;
    const layer = options.layer || 'memory';
    const now = Date.now();

    const entry: CacheEntry<T> = {
      key: fullKey,
      value,
      ttl,
      createdAt: now,
      expiresAt: now + ttl,
      accessCount: 0,
      lastAccessedAt: now,
      size: this.estimateSize(value),
      tags: options.tags || [],
      layer,
    };

    switch (layer) {
      case 'memory':
        this.memoryCache.set(fullKey, entry);
        break;
      case 'session':
        this.setInSessionStorage(fullKey, entry);
        break;
      case 'local':
        this.setInLocalStorage(fullKey, entry);
        break;
      case 'indexeddb':
        await this.setInIndexedDB(fullKey, entry);
        break;
    }
  }

  /**
   * Get or set with callback
   */
  async getOrSet<T>(
    key: string,
    factory: () => Promise<T>,
    options: CacheOptions = {}
  ): Promise<T> {
    const cached = await this.get<T>(key, options);
    if (cached !== null) {
      // Stale-while-revalidate
      if (options.policy === 'stale-while-revalidate') {
        this.revalidateInBackground(key, factory, options);
      }
      return cached;
    }

    const value = await factory();
    await this.set(key, value, options);
    return value;
  }

  /**
   * Delete from cache
   */
  async delete(key: string, layer?: CacheLayer): Promise<void> {
    const fullKey = this.getFullKey(key);

    if (!layer || layer === 'memory') {
      this.memoryCache.delete(fullKey);
    }
    if (!layer || layer === 'session') {
      sessionStorage.removeItem(fullKey);
    }
    if (!layer || layer === 'local') {
      localStorage.removeItem(fullKey);
    }
    if (!layer || layer === 'indexeddb') {
      await this.deleteFromIndexedDB(fullKey);
    }
  }

  /**
   * Invalidate cache entries
   */
  async invalidate(options: InvalidationOptions): Promise<number> {
    let invalidated = 0;

    // Memory cache
    for (const [key, entry] of this.memoryCache.entries()) {
      if (this.shouldInvalidate(key, entry, options)) {
        this.memoryCache.delete(key);
        invalidated++;
      }
    }

    // Storage caches
    invalidated += await this.invalidateStorage('session', options);
    invalidated += await this.invalidateStorage('local', options);
    invalidated += await this.invalidateIndexedDB(options);

    return invalidated;
  }

  /**
   * Invalidate by tags
   */
  async invalidateByTags(tags: string[]): Promise<number> {
    return this.invalidate({ tags });
  }

  /**
   * Clear all caches
   */
  async clear(layer?: CacheLayer): Promise<void> {
    if (!layer || layer === 'memory') {
      this.memoryCache.clear();
    }
    if (!layer || layer === 'session') {
      this.clearSessionStorage();
    }
    if (!layer || layer === 'local') {
      this.clearLocalStorage();
    }
    if (!layer || layer === 'indexeddb') {
      await this.clearIndexedDB();
    }
  }

  /**
   * Get cache statistics
   */
  getStats(): CacheStats {
    this.updateStats();
    return { ...this.stats };
  }

  /**
   * Prune expired entries
   */
  async prune(): Promise<number> {
    let pruned = 0;
    const now = Date.now();

    // Memory cache
    for (const [key, entry] of this.memoryCache.entries()) {
      if (now > entry.expiresAt) {
        this.memoryCache.delete(key);
        pruned++;
      }
    }

    // Storage caches
    pruned += await this.pruneStorage('session');
    pruned += await this.pruneStorage('local');
    pruned += await this.pruneIndexedDB();

    return pruned;
  }

  // ============================================================================
  // Session Storage Methods
  // ============================================================================

  private getFromSessionStorage<T>(key: string): CacheEntry<T> | null {
    try {
      const data = sessionStorage.getItem(key);
      if (!data) return null;

      const entry = JSON.parse(data) as CacheEntry<T>;
      if (Date.now() > entry.expiresAt) {
        sessionStorage.removeItem(key);
        return null;
      }

      return entry;
    } catch {
      return null;
    }
  }

  private setInSessionStorage<T>(key: string, entry: CacheEntry<T>): void {
    try {
      sessionStorage.setItem(key, JSON.stringify(entry));
    } catch (e) {
      // Storage full - evict oldest
      this.evictFromStorage('session');
      try {
        sessionStorage.setItem(key, JSON.stringify(entry));
      } catch {
        console.warn('Session storage full, unable to cache');
      }
    }
  }

  private clearSessionStorage(): void {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i);
      if (key?.startsWith(CACHE_PREFIX)) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach((key) => sessionStorage.removeItem(key));
  }

  // ============================================================================
  // Local Storage Methods
  // ============================================================================

  private getFromLocalStorage<T>(key: string): CacheEntry<T> | null {
    try {
      const data = localStorage.getItem(key);
      if (!data) return null;

      const entry = JSON.parse(data) as CacheEntry<T>;
      if (Date.now() > entry.expiresAt) {
        localStorage.removeItem(key);
        return null;
      }

      return entry;
    } catch {
      return null;
    }
  }

  private setInLocalStorage<T>(key: string, entry: CacheEntry<T>): void {
    try {
      localStorage.setItem(key, JSON.stringify(entry));
    } catch (e) {
      this.evictFromStorage('local');
      try {
        localStorage.setItem(key, JSON.stringify(entry));
      } catch {
        console.warn('Local storage full, unable to cache');
      }
    }
  }

  private clearLocalStorage(): void {
    const keysToRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(CACHE_PREFIX)) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach((key) => localStorage.removeItem(key));
  }

  // ============================================================================
  // IndexedDB Methods
  // ============================================================================

  private async getDB(): Promise<IDBDatabase> {
    if (this.dbPromise) return this.dbPromise;

    this.dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open('flashit_cache', 1);

      request.onerror = () => reject(request.error);
      request.onsuccess = () => resolve(request.result);

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;
        if (!db.objectStoreNames.contains('cache')) {
          const store = db.createObjectStore('cache', { keyPath: 'key' });
          store.createIndex('expiresAt', 'expiresAt');
          store.createIndex('tags', 'tags', { multiEntry: true });
        }
      };
    });

    return this.dbPromise;
  }

  private async getFromIndexedDB<T>(key: string): Promise<CacheEntry<T> | null> {
    try {
      const db = await this.getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('cache', 'readonly');
        const store = tx.objectStore('cache');
        const request = store.get(key);

        request.onsuccess = () => {
          const entry = request.result as CacheEntry<T> | undefined;
          if (!entry) {
            resolve(null);
            return;
          }

          if (Date.now() > entry.expiresAt) {
            this.deleteFromIndexedDB(key);
            resolve(null);
            return;
          }

          resolve(entry);
        };

        request.onerror = () => resolve(null);
      });
    } catch {
      return null;
    }
  }

  private async setInIndexedDB<T>(
    key: string,
    entry: CacheEntry<T>
  ): Promise<void> {
    try {
      const db = await this.getDB();
      return new Promise((resolve, reject) => {
        const tx = db.transaction('cache', 'readwrite');
        const store = tx.objectStore('cache');
        const request = store.put(entry);

        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } catch (e) {
      console.warn('IndexedDB write failed:', e);
    }
  }

  private async deleteFromIndexedDB(key: string): Promise<void> {
    try {
      const db = await this.getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('cache', 'readwrite');
        const store = tx.objectStore('cache');
        store.delete(key);
        tx.oncomplete = () => resolve();
      });
    } catch {
      // Ignore errors
    }
  }

  private async clearIndexedDB(): Promise<void> {
    try {
      const db = await this.getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('cache', 'readwrite');
        const store = tx.objectStore('cache');
        store.clear();
        tx.oncomplete = () => resolve();
      });
    } catch {
      // Ignore errors
    }
  }

  private async invalidateIndexedDB(options: InvalidationOptions): Promise<number> {
    try {
      const db = await this.getDB();
      return new Promise((resolve) => {
        const tx = db.transaction('cache', 'readwrite');
        const store = tx.objectStore('cache');
        const request = store.openCursor();
        let count = 0;

        request.onsuccess = () => {
          const cursor = request.result;
          if (cursor) {
            const entry = cursor.value as CacheEntry<unknown>;
            if (this.shouldInvalidate(entry.key, entry, options)) {
              cursor.delete();
              count++;
            }
            cursor.continue();
          }
        };

        tx.oncomplete = () => resolve(count);
      });
    } catch {
      return 0;
    }
  }

  private async pruneIndexedDB(): Promise<number> {
    try {
      const db = await this.getDB();
      const now = Date.now();

      return new Promise((resolve) => {
        const tx = db.transaction('cache', 'readwrite');
        const store = tx.objectStore('cache');
        const index = store.index('expiresAt');
        const range = IDBKeyRange.upperBound(now);
        const request = index.openCursor(range);
        let count = 0;

        request.onsuccess = () => {
          const cursor = request.result;
          if (cursor) {
            cursor.delete();
            count++;
            cursor.continue();
          }
        };

        tx.oncomplete = () => resolve(count);
      });
    } catch {
      return 0;
    }
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private getFullKey(key: string): string {
    return `${CACHE_PREFIX}${key}`;
  }

  private estimateSize(value: unknown): number {
    try {
      return new Blob([JSON.stringify(value)]).size;
    } catch {
      return 0;
    }
  }

  private shouldInvalidate(
    key: string,
    entry: CacheEntry<unknown>,
    options: InvalidationOptions
  ): boolean {
    if (options.pattern) {
      const regex = new RegExp(options.pattern);
      if (!regex.test(key)) return false;
    }

    if (options.tags && options.tags.length > 0) {
      const hasTag = options.tags.some((tag) => entry.tags.includes(tag));
      if (!hasTag) return false;
    }

    if (options.olderThan) {
      if (entry.createdAt > options.olderThan.getTime()) return false;
    }

    return true;
  }

  private async invalidateStorage(
    type: 'session' | 'local',
    options: InvalidationOptions
  ): Promise<number> {
    const storage = type === 'session' ? sessionStorage : localStorage;
    const keysToRemove: string[] = [];

    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (!key?.startsWith(CACHE_PREFIX)) continue;

      try {
        const data = storage.getItem(key);
        if (!data) continue;

        const entry = JSON.parse(data) as CacheEntry<unknown>;
        if (this.shouldInvalidate(key, entry, options)) {
          keysToRemove.push(key);
        }
      } catch {
        // Skip invalid entries
      }
    }

    keysToRemove.forEach((key) => storage.removeItem(key));
    return keysToRemove.length;
  }

  private async pruneStorage(type: 'session' | 'local'): Promise<number> {
    const storage = type === 'session' ? sessionStorage : localStorage;
    const now = Date.now();
    const keysToRemove: string[] = [];

    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (!key?.startsWith(CACHE_PREFIX)) continue;

      try {
        const data = storage.getItem(key);
        if (!data) continue;

        const entry = JSON.parse(data) as CacheEntry<unknown>;
        if (now > entry.expiresAt) {
          keysToRemove.push(key);
        }
      } catch {
        // Skip invalid entries
      }
    }

    keysToRemove.forEach((key) => storage.removeItem(key));
    return keysToRemove.length;
  }

  private evictFromStorage(type: 'session' | 'local'): void {
    const storage = type === 'session' ? sessionStorage : localStorage;
    const entries: Array<{ key: string; lastAccessed: number }> = [];

    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (!key?.startsWith(CACHE_PREFIX)) continue;

      try {
        const data = storage.getItem(key);
        if (!data) continue;

        const entry = JSON.parse(data) as CacheEntry<unknown>;
        entries.push({ key, lastAccessed: entry.lastAccessedAt });
      } catch {
        // Skip invalid entries
      }
    }

    // Sort by last accessed and remove oldest 10%
    entries.sort((a, b) => a.lastAccessed - b.lastAccessed);
    const toRemove = Math.max(1, Math.floor(entries.length * 0.1));
    
    for (let i = 0; i < toRemove; i++) {
      storage.removeItem(entries[i].key);
    }
  }

  private recordHit(): void {
    this.stats.hits++;
    this.updateHitRate();
  }

  private recordMiss(): void {
    this.stats.misses++;
    this.updateHitRate();
  }

  private updateHitRate(): void {
    const total = this.stats.hits + this.stats.misses;
    this.stats.hitRate = total > 0 ? this.stats.hits / total : 0;
  }

  private updateStats(): void {
    this.stats.totalEntries = this.memoryCache.size();
    
    // Estimate memory usage
    let memorySize = 0;
    for (const [, entry] of this.memoryCache.entries()) {
      memorySize += entry.size;
    }
    this.stats.memoryUsage = memorySize;

    // Estimate local storage usage
    let localSize = 0;
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(CACHE_PREFIX)) {
        localSize += (localStorage.getItem(key)?.length || 0) * 2; // UTF-16
      }
    }
    this.stats.localStorageUsage = localSize;
  }

  private async revalidateInBackground<T>(
    key: string,
    factory: () => Promise<T>,
    options: CacheOptions
  ): Promise<void> {
    try {
      const value = await factory();
      await this.set(key, value, options);
    } catch {
      // Silently fail background revalidation
    }
  }
}

/**
 * Get the cache strategy service instance
 */
export function getCacheStrategy(
  config?: Partial<CacheConfig>
): CacheStrategyService {
  return CacheStrategyService.getInstance(config);
}

/**
 * React hook for cache strategy
 */
export function useCacheStrategy(): CacheStrategyService {
  return getCacheStrategy();
}

export default CacheStrategyService;
