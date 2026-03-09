/**
 * @doc.type class
 * @doc.purpose Browser LocalStorage plugin
 * @doc.layer product
 * @doc.pattern Adapter
 * 
 * Provides persistent browser storage using LocalStorage API.
 * Data persists across page reloads and browser sessions.
 * Limited to ~5MB per domain.
 * 
 * @see {@link IStorage}
 */

import { IStorage } from '../../interfaces/Storage';

/**
 * LocalStorage entry metadata
 */
interface LocalStorageEntry {
  value: unknown;
  expiresAt?: number;
}

/**
 * LocalStorage Plugin
 * 
 * Browser-based persistent storage using the LocalStorage API.
 * Suitable for:
 * - Browser applications
 * - Persisting user preferences
 * - Caching API responses
 * - Session data
 * 
 * NOT suitable for:
 * - Sensitive data (stored in plain text)
 * - Large data (5MB limit)
 * - Server-side applications (use InMemoryStorage or database instead)
 * 
 * Usage (Browser):
 * ```typescript
 * const storage = new LocalStoragePlugin({ prefix: 'myapp_' });
 * await storage.initialize();
 * 
 * await storage.set('user', { id: 123, name: 'John' });
 * const user = await storage.get('user');
 * 
 * await storage.shutdown();
 * ```
 */
export class LocalStoragePlugin implements IStorage {
  // IPlugin interface implementation
  readonly id = 'localstorage';
  readonly name = 'LocalStorage';
  readonly version = '0.1.0';
  readonly description = 'Browser LocalStorage persistent storage';
  enabled = false;
  metadata: Record<string, unknown> = {};

  // Configuration
  private prefix: string;

  /**
   * Create LocalStorage plugin
   * 
   * @param config - Configuration object
   * @param config.prefix - Key prefix for namespacing
   */
  constructor(config: { prefix?: string } = {}) {
    this.prefix = config.prefix ?? 'dcmaar_';
  }

  /**
   * Initialize the storage plugin
   * 
   * Checks if localStorage is available (may be disabled in private browsing).
   * 
   * @throws Error if localStorage is not available
   */
  async initialize(): Promise<void> {
    try {
      // Check if localStorage is available
      const test = '__localStorage_test__';
      localStorage.setItem(test, 'test');
      localStorage.removeItem(test);
      this.enabled = true;
    } catch (error) {
      throw new Error(
        'LocalStorage is not available (disabled in private browsing?)',
      );
    }
  }

  /**
   * Shutdown the storage plugin
   */
  async shutdown(): Promise<void> {
    this.enabled = false;
  }

  /**
   * Store a value
   * 
   * Values are JSON serialized. Objects must be JSON-serializable.
   * 
   * @param key - Storage key
   * @param value - Value to store
   * @param ttl - Time-to-live in milliseconds
   * @throws Error if value cannot be serialized or quota exceeded
   */
  async set(key: string, value: unknown, ttl?: number): Promise<void> {
    if (!this.enabled) {
      throw new Error('LocalStorage not initialized');
    }

    try {
      const entry: LocalStorageEntry = {
        value,
      };

      if (ttl && ttl > 0) {
        entry.expiresAt = Date.now() + ttl;
      }

      const serialized = JSON.stringify(entry);
      localStorage.setItem(this.prefixKey(key), serialized);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'QuotaExceededError') {
        throw new Error('LocalStorage quota exceeded');
      }
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Failed to store value: ${message}`);
    }
  }

  /**
   * Retrieve a value
   * 
   * @param key - Storage key
   * @returns Stored value or null if not found or expired
   */
  async get(key: string): Promise<unknown | null> {
    if (!this.enabled) {
      throw new Error('LocalStorage not initialized');
    }

    try {
      const serialized = localStorage.getItem(this.prefixKey(key));

      if (!serialized) {
        return null;
      }

      const entry: LocalStorageEntry = JSON.parse(serialized);

      // Check expiration
      if (entry.expiresAt && entry.expiresAt < Date.now()) {
        localStorage.removeItem(this.prefixKey(key));
        return null;
      }

      return entry.value;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Failed to retrieve value: ${message}`);
    }
  }

  /**
   * Delete a value
   * 
   * @param key - Storage key
   */
  async delete(key: string): Promise<void> {
    if (!this.enabled) {
      throw new Error('LocalStorage not initialized');
    }

    localStorage.removeItem(this.prefixKey(key));
  }

  /**
   * Check if key exists
   * 
   * @param key - Storage key
   * @returns True if key exists and is not expired
   */
  async exists(key: string): Promise<boolean> {
    if (!this.enabled) {
      throw new Error('LocalStorage not initialized');
    }

    const prefixedKey = this.prefixKey(key);
    const serialized = localStorage.getItem(prefixedKey);

    if (!serialized) {
      return false;
    }

    try {
      const entry: LocalStorageEntry = JSON.parse(serialized);

      // Check expiration
      if (entry.expiresAt && entry.expiresAt < Date.now()) {
        localStorage.removeItem(prefixedKey);
        return false;
      }

      return true;
    } catch {
      return false;
    }
  }

  /**
   * Clear all storage
   * 
   * Only clears entries with the configured prefix.
   */
  async clear(): Promise<void> {
    if (!this.enabled) {
      throw new Error('LocalStorage not initialized');
    }

    const keysToDelete: string[] = [];

    // Find all keys with our prefix
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(this.prefix)) {
        keysToDelete.push(key);
      }
    }

    // Delete them
    for (const key of keysToDelete) {
      localStorage.removeItem(key);
    }
  }

  /**
   * Execute command interface from IPlugin
   * 
   * @param command - Command name
   * @param params - Command parameters
   * @returns Command result
   */
  async execute(
    command: string,
    params?: Record<string, unknown>,
  ): Promise<unknown> {
    switch (command) {
      case 'set':
        return await this.set(
          String(params?.key ?? ''),
          params?.value,
          params?.ttl as number | undefined,
        );
      case 'get':
        return await this.get(String(params?.key ?? ''));
      case 'delete':
        return await this.delete(String(params?.key ?? ''));
      case 'exists':
        return await this.exists(String(params?.key ?? ''));
      case 'clear':
        return await this.clear();
      default:
        throw new Error(`Unknown command: ${command}`);
    }
  }

  /**
   * Get storage statistics
   * 
   * @returns Object with key count and approximate size
   */
  async getStats(): Promise<Record<string, unknown>> {
    const keyCount = Array.from({ length: localStorage.length })
      .map((_, i) => localStorage.key(i))
      .filter(key => key && key.startsWith(this.prefix)).length;

    let totalSize = 0;
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(this.prefix)) {
        const value = localStorage.getItem(key);
        if (value) {
          totalSize += key.length + value.length;
        }
      }
    }

    return {
      keyCount,
      approximateSizeBytes: totalSize,
    };
  }

  /**
   * Get prefixed key for namespacing
   * 
   * @private
   * @param key - Original key
   * @returns Prefixed key
   */
  private prefixKey(key: string): string {
    return `${this.prefix}${key}`;
  }
}
