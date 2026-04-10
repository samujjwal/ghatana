/**
 * @ghatana/state — Persistence Utilities
 *
 * Helpers for reading/writing state to localStorage and sessionStorage
 * with versioned migration support.
 */

import type { PersistenceOptions } from "./types";

// ---------------------------------------------------------------------------
// Envelope stored in the backend
// ---------------------------------------------------------------------------

interface StorageEnvelope<T> {
  readonly v: number;
  readonly data: T;
  readonly savedAt: number;
}

// ---------------------------------------------------------------------------
// In-memory backend (for SSR / tests)
// ---------------------------------------------------------------------------

const memoryStore = new Map<string, string>();

// ---------------------------------------------------------------------------
// Read from storage
// ---------------------------------------------------------------------------

/**
 * Reads and deserializes a value from the given storage backend.
 * Applies migration if the stored version does not match options.version.
 * Returns `null` if the key is not found or deserialization fails.
 */
export function readFromStorage<T>(
  key: string,
  options: Pick<PersistenceOptions, "storage" | "version" | "migrate">
): T | null {
  const raw = getBackend(options.storage).getItem(key);
  if (raw === null) return null;

  let envelope: StorageEnvelope<T>;
  try {
    envelope = JSON.parse(raw) as StorageEnvelope<T>;
  } catch {
    return null;
  }

  const storedVersion = envelope.v ?? 0;
  const targetVersion = options.version ?? 1;

  if (storedVersion !== targetVersion && options.migrate) {
    const migrated = options.migrate(envelope.data, storedVersion) as T;
    return migrated;
  }

  return envelope.data;
}

// ---------------------------------------------------------------------------
// Write to storage
// ---------------------------------------------------------------------------

/**
 * Serializes and writes a value to the given storage backend with a versioned envelope.
 */
export function writeToStorage<T>(
  key: string,
  value: T,
  options: Pick<PersistenceOptions, "storage" | "version">
): void {
  const envelope: StorageEnvelope<T> = {
    v: options.version ?? 1,
    data: value,
    savedAt: Date.now(),
  };
  try {
    getBackend(options.storage).setItem(key, JSON.stringify(envelope));
  } catch {
    // Quota exceeded or private browsing — silently ignore
  }
}

// ---------------------------------------------------------------------------
// Remove from storage
// ---------------------------------------------------------------------------

/**
 * Removes a key from the given storage backend.
 */
export function removeFromStorage(
  key: string,
  storage: PersistenceOptions["storage"]
): void {
  getBackend(storage).removeItem(key);
}

// ---------------------------------------------------------------------------
// Storage backend resolver
// ---------------------------------------------------------------------------

interface SimpleStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

function getBackend(
  storage: PersistenceOptions["storage"] = "memory"
): SimpleStorage {
  if (storage === "localStorage" && typeof localStorage !== "undefined") {
    return localStorage;
  }
  if (storage === "sessionStorage" && typeof sessionStorage !== "undefined") {
    return sessionStorage;
  }
  // In-memory fallback for SSR / tests
  return {
    getItem: (k) => memoryStore.get(k) ?? null,
    setItem: (k, v) => { memoryStore.set(k, v); },
    removeItem: (k) => { memoryStore.delete(k); },
  };
}

// ---------------------------------------------------------------------------
// clearMemoryStore — test utility
// ---------------------------------------------------------------------------

/** Clears the in-memory storage backend. Useful in tests. */
export function clearMemoryStore(): void {
  memoryStore.clear();
}
