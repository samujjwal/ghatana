/**
 * @fileoverview Storage abstraction for platform-agnostic configuration and data persistence.
 *
 * Provides a unified interface for storage operations across different platforms
 * (browser extensions, Electron, Node.js, etc.). Products inject platform-specific
 * implementations while connectors use the abstract interface.
 *
 * @module storage/StorageProvider
 * @since 1.1.0
 */

/**
 * Storage provider interface for configuration and data persistence.
 *
 * This interface abstracts storage operations to work across different platforms:
 * - Browser extensions: chrome.storage.local
 * - Electron: electron-store or native storage
 * - Node.js: filesystem or database
 * - Agent: filesystem or system config
 *
 * **Key Design Principles**:
 * - Async-first API (works everywhere)
 * - Simple key-value semantics
 * - Type-safe operations
 * - Optional TTL support
 * - Batch operations
 *
 * **Usage**:
 * ```typescript
 * // Extension implementation
 * class ExtensionStorageProvider implements StorageProvider {
 *   async get(key) {
 *     const result = await chrome.storage.local.get(key);
 *     return result[key];
 *   }
 * }
 *
 * // Use in connectors
 * const storage = new ExtensionStorageProvider();
 * await storage.set('config', {...});
 * const config = await storage.get('config');
 * ```
 */
export interface StorageProvider {
  /**
   * Retrieves a value by key.
   *
   * @param key - Storage key
   * @returns Value or undefined if not found
   * @throws Error if storage operation fails
   *
   * @example
   * ```typescript
   * const config = await storage.get('bootstrap-config');
   * if (config) {
   *   console.log('Found config:', config);
   * }
   * ```
   */
  get<T = any>(key: string): Promise<T | undefined>;

  /**
   * Stores a value by key.
   *
   * @param key - Storage key
   * @param value - Value to store (must be JSON-serializable)
   * @param options - Optional storage options
   * @throws Error if storage operation fails
   *
   * @example
   * ```typescript
   * await storage.set('config', { version: '1.0' });
   * await storage.set('cache', data, { ttl: 3600000 }); // 1 hour
   * ```
   */
  set<T = any>(key: string, value: T, options?: StorageSetOptions): Promise<void>;

  /**
   * Deletes a value by key.
   *
   * @param key - Storage key
   * @returns true if key was deleted, false if not found
   * @throws Error if storage operation fails
   *
   * @example
   * ```typescript
   * const deleted = await storage.delete('old-config');
   * console.log(`Deleted: ${deleted}`);
   * ```
   */
  delete(key: string): Promise<boolean>;

  /**
   * Checks if a key exists.
   *
   * @param key - Storage key
   * @returns true if key exists
   *
   * @example
   * ```typescript
   * if (await storage.has('config')) {
   *   console.log('Config exists');
   * }
   * ```
   */
  has(key: string): Promise<boolean>;

  /**
   * Lists all keys matching a prefix.
   *
   * @param prefix - Key prefix to match (optional)
   * @returns Array of matching keys
   *
   * @example
   * ```typescript
   * const keys = await storage.keys('cache:');
   * console.log(`Found ${keys.length} cache entries`);
   * ```
   */
  keys(prefix?: string): Promise<string[]>;

  /**
   * Clears all storage entries matching a prefix.
   *
   * @param prefix - Key prefix to match (optional, clears all if omitted)
   * @returns Number of keys cleared
   *
   * @example
   * ```typescript
   * await storage.clear('temp:'); // Clear temp entries
   * await storage.clear(); // Clear everything
   * ```
   */
  clear(prefix?: string): Promise<number>;

  /**
   * Gets multiple values by keys (batch operation).
   *
   * @param keys - Array of storage keys
   * @returns Map of key to value (missing keys omitted)
   *
   * @example
   * ```typescript
   * const values = await storage.getMany(['key1', 'key2', 'key3']);
   * console.log(`Found ${values.size} values`);
   * ```
   */
  getMany<T = any>(keys: string[]): Promise<Map<string, T>>;

  /**
   * Sets multiple values (batch operation).
   *
   * @param entries - Map of key to value
   * @param options - Optional storage options
   *
   * @example
   * ```typescript
   * await storage.setMany(new Map([
   *   ['key1', value1],
   *   ['key2', value2]
   * ]));
   * ```
   */
  setMany<T = any>(entries: Map<string, T>, options?: StorageSetOptions): Promise<void>;

  /**
   * Gets storage statistics.
   *
   * @returns Storage statistics (implementation-dependent)
   *
   * @example
   * ```typescript
   * const stats = await storage.getStats();
   * console.log(`Used: ${stats.bytesUsed} / ${stats.bytesAvailable}`);
   * ```
   */
  getStats?(): Promise<StorageStats>;
}

/**
 * Options for storage set operations.
 */
export interface StorageSetOptions {
  /**
   * Time-to-live in milliseconds.
   * After this time, the value should be considered expired.
   * Implementation may remove expired values automatically or on access.
   */
  ttl?: number;

  /**
   * Whether to overwrite existing value.
   * @default true
   */
  overwrite?: boolean;

  /**
   * Custom metadata for the storage entry.
   */
  metadata?: Record<string, any>;
}

/**
 * Storage statistics.
 */
export interface StorageStats {
  /**
   * Number of keys stored.
   */
  keys: number;

  /**
   * Approximate bytes used (if available).
   */
  bytesUsed?: number;

  /**
   * Approximate bytes available (if available).
   */
  bytesAvailable?: number;

  /**
   * Implementation-specific stats.
   */
  [key: string]: unknown;
}
