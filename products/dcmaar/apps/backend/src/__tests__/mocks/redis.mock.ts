/**
 * Redis Mock for Testing
 *
 * Provides in-memory Redis mock for testing cache and session functionality
 */

export interface RedisValue {
  value: string;
  expiry?: number; // Timestamp when value expires
}

/**
 * Redis mock class
 */
class RedisMock {
  private store: Map<string, RedisValue> = new Map();
  private connected: boolean = false;

  /**
   * Mock connect to Redis
   */
  async connect(): Promise<void> {
    this.connected = true;
  }

  /**
   * Mock disconnect from Redis
   */
  async disconnect(): Promise<void> {
    this.connected = false;
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   * Mock GET command
   */
  async get(key: string): Promise<string | null> {
    const item = this.store.get(key);
    if (!item) return null;

    // Check if expired
    if (item.expiry && item.expiry < Date.now()) {
      this.store.delete(key);
      return null;
    }

    return item.value;
  }

  /**
   * Mock SET command
   */
  async set(key: string, value: string, options?: { EX?: number }): Promise<void> {
    const expiry = options?.EX ? Date.now() + options.EX * 1000 : undefined;
    this.store.set(key, { value, expiry });
  }

  /**
   * Mock DEL command
   */
  async del(key: string): Promise<number> {
    const existed = this.store.has(key);
    this.store.delete(key);
    return existed ? 1 : 0;
  }

  /**
   * Mock EXISTS command
   */
  async exists(key: string): Promise<number> {
    const item = this.store.get(key);
    if (!item) return 0;

    // Check if expired
    if (item.expiry && item.expiry < Date.now()) {
      this.store.delete(key);
      return 0;
    }

    return 1;
  }

  /**
   * Mock EXPIRE command
   */
  async expire(key: string, seconds: number): Promise<number> {
    const item = this.store.get(key);
    if (!item) return 0;

    item.expiry = Date.now() + seconds * 1000;
    return 1;
  }

  /**
   * Mock TTL command (Time To Live)
   */
  async ttl(key: string): Promise<number> {
    const item = this.store.get(key);
    if (!item) return -2; // Key doesn't exist
    if (!item.expiry) return -1; // No expiry set

    const remaining = Math.floor((item.expiry - Date.now()) / 1000);
    return remaining > 0 ? remaining : -2;
  }

  /**
   * Mock KEYS command (pattern matching)
   */
  async keys(pattern: string): Promise<string[]> {
    const regex = new RegExp(pattern.replace('*', '.*'));
    return Array.from(this.store.keys()).filter(key => regex.test(key));
  }

  /**
   * Mock INCR command
   */
  async incr(key: string): Promise<number> {
    const item = this.store.get(key);
    const currentValue = item ? parseInt(item.value, 10) : 0;
    const newValue = currentValue + 1;
    await this.set(key, newValue.toString());
    return newValue;
  }

  /**
   * Mock DECR command
   */
  async decr(key: string): Promise<number> {
    const item = this.store.get(key);
    const currentValue = item ? parseInt(item.value, 10) : 0;
    const newValue = currentValue - 1;
    await this.set(key, newValue.toString());
    return newValue;
  }

  /**
   * Mock FLUSHALL command (clear all keys)
   */
  async flushAll(): Promise<void> {
    this.store.clear();
  }

  /**
   * Get all keys (for testing)
   */
  getAllKeys(): string[] {
    return Array.from(this.store.keys());
  }

  /**
   * Get store size (for testing)
   */
  getSize(): number {
    return this.store.size;
  }

  /**
   * Clear expired keys (for testing)
   */
  clearExpired(): void {
    const now = Date.now();
    for (const [key, item] of this.store.entries()) {
      if (item.expiry && item.expiry < now) {
        this.store.delete(key);
      }
    }
  }
}

export const redisMock = new RedisMock();

export default redisMock;
