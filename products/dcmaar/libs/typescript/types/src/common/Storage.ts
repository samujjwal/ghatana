/**
 * Storage type definition
 * Represents storage and persistence mechanisms
 */

export interface StorageOptions {
  ttl?: number;
  namespace?: string;
}

export interface Storage {
  get<T = unknown>(key: string): Promise<T | null>;
  set<T = unknown>(key: string, value: T, options?: StorageOptions): Promise<void>;
  delete(key: string): Promise<void>;
  clear(): Promise<void>;
  keys(): Promise<string[]>;
}

export interface CacheStorage extends Storage {
  has(key: string): Promise<boolean>;
  size(): Promise<number>;
}

export interface PersistentStorage extends Storage {
  backup(): Promise<void>;
  restore(): Promise<void>;
}
