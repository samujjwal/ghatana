/**
 * Typed Storage Service
 *
 * Type-safe wrapper around browser Storage with governance enforcement.
 * Replaces ad-hoc `localStorage.getItem('key')` calls with a validated API.
 *
 * @doc.type service
 * @doc.purpose Provide governed, typed, and observable browser storage access
 * @doc.layer product
 * @doc.pattern Service
 */

import { logger } from '../../utils/Logger';
import {
  type KnownStorageKey,
  STORAGE_REGISTRY,
  getStorageMeta,
  type StorageKeyMeta,
} from './StorageRegistry';

function canUseStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

const WARNED_KEYS = new Set<string>();

function warnUngoverned(key: string): void {
  if (WARNED_KEYS.has(key)) return;
  WARNED_KEYS.add(key);
  logger.warn(
    'Ungoverned localStorage key accessed. Add to StorageRegistry.',
    'storage',
    { key }
  );
}

function guardProductionTestKeys(meta: StorageKeyMeta | null, operation: string): void {
  if (!meta) return;
  if (meta.sensitivity === 'TEST' && !import.meta.env.DEV) {
    logger.error(
      `TEST storage key "${meta.key}" accessed in production during ${operation}`,
      'storage'
    );
  }
}

export interface StorageChangeEvent<T = unknown> {
  key: string;
  oldValue: T | null;
  newValue: T | null;
}

type StorageListener<T> = (event: StorageChangeEvent<T>) => void;

const listeners = new Map<string, Set<StorageListener<unknown>>>();

/**
 * Subscribe to changes for a specific key.
 */
export function subscribeStorage<T>(key: KnownStorageKey, listener: StorageListener<T>): () => void {
  const set = listeners.get(key) ?? new Set();
  set.add(listener as StorageListener<unknown>);
  listeners.set(key, set);
  return () => {
    set.delete(listener as StorageListener<unknown>);
  };
}

/**
 * Deserialize a stored value with safe JSON parsing.
 */
function deserialize<T>(raw: string | null): T | null {
  if (raw === null) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    // If parsing fails, return the raw string as a fallback
    return raw as unknown as T;
  }
}

/**
 * Serialize a value for storage.
 */
function serialize<T>(value: T): string {
  return JSON.stringify(value);
}

/**
 * Read a governed storage key. Returns null if missing or unparseable.
 * Warns on ungoverned keys.
 */
export function readStorage<T>(key: KnownStorageKey | string): T | null {
  if (!canUseStorage()) return null;

  const meta = getStorageMeta(key);
  if (!meta) {
    warnUngoverned(key);
  } else {
    guardProductionTestKeys(meta, 'read');
  }

  const raw = window.localStorage.getItem(key);
  return deserialize<T>(raw);
}

/**
 * Write a governed storage key.
 * Warns on ungoverned keys and logs HIGH sensitivity writes.
 */
export function writeStorage<T>(key: KnownStorageKey | string, value: T): void {
  if (!canUseStorage()) return;

  const meta = getStorageMeta(key);
  if (!meta) {
    warnUngoverned(key);
  } else {
    guardProductionTestKeys(meta, 'write');
    if (meta.sensitivity === 'HIGH') {
      logger.warn(`HIGH sensitivity storage key "${key}" written to localStorage`, 'storage', {
        key,
        targetBackend: meta.targetBackend,
      });
    }
  }

  const oldValue = deserialize<unknown>(window.localStorage.getItem(key));
  window.localStorage.setItem(key, serialize(value));

  const keyListeners = listeners.get(key);
  if (keyListeners) {
    const newValue = deserialize<unknown>(window.localStorage.getItem(key));
    keyListeners.forEach((listener) =>
      listener({ key, oldValue, newValue })
    );
  }
}

/**
 * Remove a governed storage key.
 */
export function removeStorage(key: KnownStorageKey | string): void {
  if (!canUseStorage()) return;

  const meta = getStorageMeta(key);
  if (!meta) {
    warnUngoverned(key);
  } else {
    guardProductionTestKeys(meta, 'remove');
  }

  const oldValue = deserialize<unknown>(window.localStorage.getItem(key));
  window.localStorage.removeItem(key);

  const keyListeners = listeners.get(key);
  if (keyListeners) {
    keyListeners.forEach((listener) =>
      listener({ key, oldValue, newValue: null })
    );
  }
}

/**
 * Convenience: read a boolean flag.
 */
export function readFlag(key: KnownStorageKey | string): boolean {
  const raw = readStorage<string>(key);
  return raw === 'true';
}

/**
 * Convenience: write a boolean flag.
 */
export function writeFlag(key: KnownStorageKey | string, value: boolean): void {
  writeStorage(key, value ? 'true' : 'false');
}

/**
 * Convenience: read a number with fallback.
 */
export function readNumber(key: KnownStorageKey | string, fallback = 0): number {
  const raw = readStorage<number>(key);
  return typeof raw === 'number' ? raw : fallback;
}

/**
 * Convenience: read an object with fallback.
 */
export function readObject<T>(key: KnownStorageKey | string, fallback: T): T {
  const raw = readStorage<T>(key);
  return raw !== null ? raw : fallback;
}
