/**
 * Storage Plugin Interface
 * Defines the contract for data storage plugins
 */

import { IPlugin } from '@ghatana/dcmaar-types';

export interface IStorage extends IPlugin {
  /**
   * Store data
   * @param key - Storage key
   * @param value - Data to store
   * @param ttl - Time-to-live in milliseconds (optional)
   */
  set(key: string, value: unknown, ttl?: number): Promise<void>;

  /**
   * Retrieve data
   * @param key - Storage key
   * @returns Promise with stored data or null
   */
  get(key: string): Promise<unknown | null>;

  /**
   * Delete data
   * @param key - Storage key
   */
  delete(key: string): Promise<void>;

  /**
   * Check if key exists
   * @param key - Storage key
   */
  exists(key: string): Promise<boolean>;

  /**
   * Clear all data
   */
  clear(): Promise<void>;
}
