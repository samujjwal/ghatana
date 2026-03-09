/**
 * @file Type definitions for background persistence module
 */

export interface PersistenceOptions {
  /** Whether to use sync storage (default: false) */
  sync?: boolean;
  /** Time to live in milliseconds (optional) */
  ttl?: number;
}

export function saveData<T>(key: string, data: T, options?: PersistenceOptions): Promise<void>;
export function loadData<T>(key: string, options?: Omit<PersistenceOptions, 'sync'>): Promise<T | null>;
export function removeData(key: string): Promise<void>;
export function clearData(): Promise<void>;
