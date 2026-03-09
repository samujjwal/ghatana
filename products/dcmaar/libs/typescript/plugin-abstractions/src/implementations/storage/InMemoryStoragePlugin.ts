/**
 * @doc.type class
 * @doc.purpose In-memory storage plugin for browsers/Node.js
 * @doc.layer product
 * @doc.pattern Adapter
 * 
 * Provides reliable in-memory key-value storage with TTL support.
 * Suitable for development, testing, and caching scenarios.
 * 
 * @see {@link IStorage}
 */

import { IStorage } from '../../interfaces/Storage';

/**
 * Storage entry with metadata
 */
interface StorageEntry {
  value: unknown;
  expiresAt?: number;
}

/**
 * In-Memory Storage Plugin
 * 
 * Simple, reliable storage using JavaScript Map. Suitable for:
 * - Development and testing
 * - Caching layer
 * - In-process state management
 * - Node.js applications
 * 
 * NOT suitable for:
 * - Long-term persistence
 * - Sharing between processes
 * - Browser page reloads
 * 
 * Usage:
 * ```typescript
 * const storage = new InMemoryStoragePlugin();
 * await storage.initialize();
 * 
 * await storage.set('key', { value: 'data' }, 60000); // 60 second TTL
 * const data = await storage.get('key');
 * 
 * await storage.shutdown();
 * ```
 */
export class InMemoryStoragePlugin implements IStorage {
  // IPlugin interface implementation
  readonly id = 'memory-storage';
  readonly name = 'In-Memory Storage';
  readonly version = '0.1.0';
  readonly description = 'In-memory key-value storage with TTL support';
  enabled = false;
  metadata: Record<string, unknown> = {};

  // Storage
  private store: Map<string, StorageEntry> = new Map();
  
  // Cleanup timer
  private cleanupTimer?: ReturnType<typeof setInterval>;

  /**
   * Initialize the storage plugin
   * 
   * Starts cleanup interval to remove expired entries.
   */
  async initialize(): Promise<void> {
    // Start cleanup timer (every 30 seconds)
    this.cleanupTimer = setInterval(() => {
      this.cleanup();
    }, 30000);

    this.enabled = true;
  }

  /**
   * Shutdown the storage plugin
   * 
   * Clears all data and stops cleanup timer.
   */
  async shutdown(): Promise<void> {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
    }
    this.store.clear();
    this.enabled = false;
  }

  /**
   * Store a value
   * 
   * @param key - Storage key
   * @param value - Value to store (any type)
   * @param ttl - Time-to-live in milliseconds
   */
  async set(key: string, value: unknown, ttl?: number): Promise<void> {
    if (!this.enabled) {
      throw new Error('Storage not initialized');
    }

    const entry: StorageEntry = {
      value,
    };

    if (ttl && ttl > 0) {
      entry.expiresAt = Date.now() + ttl;
    }

    this.store.set(key, entry);
  }

  /**
   * Retrieve a value
   * 
   * @param key - Storage key
   * @returns Stored value or null if not found or expired
   */
  async get(key: string): Promise<unknown | null> {
    if (!this.enabled) {
      throw new Error('Storage not initialized');
    }

    const entry = this.store.get(key);

    if (!entry) {
      return null;
    }

    // Check expiration
    if (entry.expiresAt && entry.expiresAt < Date.now()) {
      this.store.delete(key);
      return null;
    }

    return entry.value;
  }

  /**
   * Delete a value
   * 
   * @param key - Storage key
   */
  async delete(key: string): Promise<void> {
    if (!this.enabled) {
      throw new Error('Storage not initialized');
    }

    this.store.delete(key);
  }

  /**
   * Check if key exists
   * 
   * @param key - Storage key
   * @returns True if key exists and is not expired
   */
  async exists(key: string): Promise<boolean> {
    if (!this.enabled) {
      throw new Error('Storage not initialized');
    }

    const entry = this.store.get(key);

    if (!entry) {
      return false;
    }

    // Check expiration
    if (entry.expiresAt && entry.expiresAt < Date.now()) {
      this.store.delete(key);
      return false;
    }

    return true;
  }

  /**
   * Clear all storage
   */
  async clear(): Promise<void> {
    if (!this.enabled) {
      throw new Error('Storage not initialized');
    }

    this.store.clear();
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
   * @returns Object with key count and storage size estimate
   */
  async getStats(): Promise<Record<string, unknown>> {
    let storageSize = 0;
    
    for (const [key, entry] of this.store.entries()) {
      storageSize += key.length;
      storageSize += JSON.stringify(entry.value).length;
    }

    return {
      keyCount: this.store.size,
      storageSize,
      keys: Array.from(this.store.keys()),
    };
  }

  /**
   * Clean up expired entries
   * 
   * Called periodically and on demand.
   * 
   * @private
   */
  private cleanup(): void {
    const now = Date.now();
    let cleanedCount = 0;

    for (const [key, entry] of this.store.entries()) {
      if (entry.expiresAt && entry.expiresAt < now) {
        this.store.delete(key);
        cleanedCount++;
      }
    }

    if (cleanedCount > 0) {
      // Emit or log cleanup event (optional)
    }
  }
}
