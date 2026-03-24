/**
 * State Persistence
 *
 * Handles persistence of state to various storage backends.
 * Supports localStorage, sessionStorage, and IndexedDB.
 *
 * @module state/StatePersistence
 */

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export type StorageType = 'local' | 'session' | 'indexeddb' | 'memory';

/**
 *
 */
export interface StorageAdapter {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
  clear(): Promise<void>;
}

/**
 *
 */
export interface PersistenceOptions {
  storage: StorageType;
  key: string;
  serialize?: (value: unknown) => string;
  deserialize?: (value: string) => any;
  version?: number;
  migrate?: (oldData: unknown, oldVersion: number) => any;
}

// ============================================================================
// Storage Adapters
// ============================================================================

/**
 * LocalStorage Adapter
 */
export class LocalStorageAdapter implements StorageAdapter {
  /**
   *
   */
  async getItem(key: string): Promise<string | null> {
    try {
      return localStorage.getItem(key);
    } catch (error) {
      console.error('[LocalStorageAdapter] Error reading:', error);
      return null;
    }
  }

  /**
   *
   */
  async setItem(key: string, value: string): Promise<void> {
    try {
      localStorage.setItem(key, value);
    } catch (error) {
      console.error('[LocalStorageAdapter] Error writing:', error);
      // Handle quota exceeded
      if (error instanceof DOMException && error.name === 'QuotaExceededError') {
        console.warn('[LocalStorageAdapter] Storage quota exceeded');
      }
    }
  }

  /**
   *
   */
  async removeItem(key: string): Promise<void> {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      console.error('[LocalStorageAdapter] Error removing:', error);
    }
  }

  /**
   *
   */
  async clear(): Promise<void> {
    try {
      localStorage.clear();
    } catch (error) {
      console.error('[LocalStorageAdapter] Error clearing:', error);
    }
  }
}

/**
 * SessionStorage Adapter
 */
export class SessionStorageAdapter implements StorageAdapter {
  /**
   *
   */
  async getItem(key: string): Promise<string | null> {
    try {
      return sessionStorage.getItem(key);
    } catch (error) {
      console.error('[SessionStorageAdapter] Error reading:', error);
      return null;
    }
  }

  /**
   *
   */
  async setItem(key: string, value: string): Promise<void> {
    try {
      sessionStorage.setItem(key, value);
    } catch (error) {
      console.error('[SessionStorageAdapter] Error writing:', error);
    }
  }

  /**
   *
   */
  async removeItem(key: string): Promise<void> {
    try {
      sessionStorage.removeItem(key);
    } catch (error) {
      console.error('[SessionStorageAdapter] Error removing:', error);
    }
  }

  /**
   *
   */
  async clear(): Promise<void> {
    try {
      sessionStorage.clear();
    } catch (error) {
      console.error('[SessionStorageAdapter] Error clearing:', error);
    }
  }
}

/**
 * IndexedDB Adapter (for large data)
 */
export class IndexedDBAdapter implements StorageAdapter {
  private dbName: string;
  private storeName: string;
  private version: number;
  private db: IDBDatabase | null = null;

  /**
   *
   */
  constructor(dbName = 'ui-state-db', storeName = 'state', version = 1) {
    this.dbName = dbName;
    this.storeName = storeName;
    this.version = version;
  }

  /**
   *
   */
  private async getDB(): Promise<IDBDatabase> {
    if (this.db) return this.db;

    return new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, this.version);

      request.onerror = () => reject(request.error);
      request.onsuccess = () => {
        this.db = request.result;
        resolve(this.db);
      };

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;
        if (!db.objectStoreNames.contains(this.storeName)) {
          db.createObjectStore(this.storeName);
        }
      };
    });
  }

  /**
   *
   */
  async getItem(key: string): Promise<string | null> {
    try {
      const db = await this.getDB();
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(this.storeName, 'readonly');
        const store = transaction.objectStore(this.storeName);
        const request = store.get(key);

        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve(request.result || null);
      });
    } catch (error) {
      console.error('[IndexedDBAdapter] Error reading:', error);
      return null;
    }
  }

  /**
   *
   */
  async setItem(key: string, value: string): Promise<void> {
    try {
      const db = await this.getDB();
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(this.storeName, 'readwrite');
        const store = transaction.objectStore(this.storeName);
        const request = store.put(value, key);

        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve();
      });
    } catch (error) {
      console.error('[IndexedDBAdapter] Error writing:', error);
    }
  }

  /**
   *
   */
  async removeItem(key: string): Promise<void> {
    try {
      const db = await this.getDB();
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(this.storeName, 'readwrite');
        const store = transaction.objectStore(this.storeName);
        const request = store.delete(key);

        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve();
      });
    } catch (error) {
      console.error('[IndexedDBAdapter] Error removing:', error);
    }
  }

  /**
   *
   */
  async clear(): Promise<void> {
    try {
      const db = await this.getDB();
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(this.storeName, 'readwrite');
        const store = transaction.objectStore(this.storeName);
        const request = store.clear();

        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve();
      });
    } catch (error) {
      console.error('[IndexedDBAdapter] Error clearing:', error);
    }
  }
}

/**
 * Memory Adapter (no persistence, for testing)
 */
export class MemoryStorageAdapter implements StorageAdapter {
  private storage = new Map<string, string>();

  /**
   *
   */
  async getItem(key: string): Promise<string | null> {
    return this.storage.get(key) || null;
  }

  /**
   *
   */
  async setItem(key: string, value: string): Promise<void> {
    this.storage.set(key, value);
  }

  /**
   *
   */
  async removeItem(key: string): Promise<void> {
    this.storage.delete(key);
  }

  /**
   *
   */
  async clear(): Promise<void> {
    this.storage.clear();
  }
}

// ============================================================================
// State Persistence Manager
// ============================================================================

/**
 *
 */
export class StatePersistence {
  private static adapters: Map<StorageType, StorageAdapter> = new Map([
    ['local', new LocalStorageAdapter()],
    ['session', new SessionStorageAdapter()],
    ['indexeddb', new IndexedDBAdapter()],
    ['memory', new MemoryStorageAdapter()],
  ]);

  /**
   * Get storage adapter
   */
  static getAdapter(type: StorageType): StorageAdapter {
    const adapter = this.adapters.get(type);
    if (!adapter) {
      throw new Error(`Storage adapter "${type}" not found`);
    }
    return adapter;
  }

  /**
   * Register custom storage adapter
   */
  static registerAdapter(type: StorageType, adapter: StorageAdapter): void {
    this.adapters.set(type, adapter);
  }

  /**
   * Load state from storage
   */
  static async load<T>(options: PersistenceOptions): Promise<T | null> {
    const adapter = this.getAdapter(options.storage);
    const deserialize = options.deserialize || JSON.parse;

    try {
      const raw = await adapter.getItem(options.key);
      if (!raw) return null;

      const data = deserialize(raw);

      // Handle versioning and migration
      if (options.version !== undefined && data._version !== options.version) {
        if (options.migrate) {
          const migrated = options.migrate(data, data._version || 0);
          await this.save(options, migrated);
          return migrated;
        }
      }

      return data;
    } catch (error) {
      console.error('[StatePersistence] Error loading:', error);
      return null;
    }
  }

  /**
   * Save state to storage
   */
  static async save<T>(options: PersistenceOptions, value: T): Promise<void> {
    const adapter = this.getAdapter(options.storage);
    const serialize = options.serialize || JSON.stringify;

    try {
      // Add version if specified
      const dataToSave = options.version !== undefined
        ? { ...value, _version: options.version }
        : value;

      const serialized = serialize(dataToSave);
      await adapter.setItem(options.key, serialized);
    } catch (error) {
      console.error('[StatePersistence] Error saving:', error);
    }
  }

  /**
   * Remove state from storage
   */
  static async remove(options: Pick<PersistenceOptions, 'storage' | 'key'>): Promise<void> {
    const adapter = this.getAdapter(options.storage);
    try {
      await adapter.removeItem(options.key);
    } catch (error) {
      console.error('[StatePersistence] Error removing:', error);
    }
  }

  /**
   * Clear all state from storage
   */
  static async clearAll(storageType: StorageType): Promise<void> {
    const adapter = this.getAdapter(storageType);
    try {
      await adapter.clear();
    } catch (error) {
      console.error('[StatePersistence] Error clearing:', error);
    }
  }

  /**
   * Check if storage is available
   */
  static isStorageAvailable(type: 'localStorage' | 'sessionStorage' | 'indexedDB'): boolean {
    try {
      if (type === 'localStorage' || type === 'sessionStorage') {
        const storage = type === 'localStorage' ? localStorage : sessionStorage;
        const test = '__storage_test__';
        storage.setItem(test, test);
        storage.removeItem(test);
        return true;
      }

      if (type === 'indexedDB') {
        return typeof indexedDB !== 'undefined';
      }

      return false;
    } catch {
      return false;
    }
  }

  /**
   * Get storage quota information (Chrome only)
   */
  static async getStorageQuota(): Promise<{
    usage: number;
    quota: number;
    percentage: number;
  } | null> {
    if ('storage' in navigator && 'estimate' in navigator.storage) {
      try {
        const estimate = await navigator.storage.estimate();
        const usage = estimate.usage || 0;
        const quota = estimate.quota || 0;
        const percentage = quota > 0 ? (usage / quota) * 100 : 0;

        return { usage, quota, percentage };
      } catch (error) {
        console.error('[StatePersistence] Error getting quota:', error);
      }
    }

    return null;
  }
}
