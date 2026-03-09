/**
 * Centralized storage helpers for FileSystem handles and metadata
 * 
 * This module provides a typed, unified interface for persisting FileSystem API handles
 * and associated metadata using IndexedDB with chrome.storage.local fallback.
 * 
 * Features:
 * - Type-safe handle storage and retrieval
 * - Metadata persistence with fallback mechanisms
 * - Promise-based async interface
 * - Error handling and logging
 * - Chrome extension storage integration
 */

export interface HandleStorageOptions {
  dbName?: string;
  storeName?: string;
  version?: number;
}

export interface StoredHandle<T = unknown> {
  key: string;
  handle: FileSystemHandle;
  meta?: T;
  timestamp?: number;
}

export interface HandleMetadata {
  source?: string;
  sink?: string;
  version?: string;
  [key: string]: unknown;
}

const DEFAULT_OPTIONS: Required<HandleStorageOptions> = {
  dbName: 'dcmaar-fs-handles',
  storeName: 'handles',
  version: 1,
};

/**
 * Opens IndexedDB connection with proper error handling
 */
async function openDatabase(options: HandleStorageOptions = {}): Promise<IDBDatabase> {
  const { dbName, storeName, version } = { ...DEFAULT_OPTIONS, ...options };

  return new Promise((resolve, reject) => {
    const request = indexedDB.open(dbName, version);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(storeName)) {
        db.createObjectStore(storeName, { keyPath: 'key' });
      }
    };

    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onerror = () => {
      reject(new Error(`Failed to open IndexedDB: ${request.error?.message}`));
    };
  });
}

/**
 * Stores metadata in chrome.storage.local as fallback mechanism
 */
async function storeMetadataFallback(key: string, meta: unknown): Promise<void> {
  try {
    const chrome = (globalThis as unknown as { chrome?: typeof globalThis.chrome }).chrome;
    if (chrome?.storage?.local) {
      await new Promise<void>((resolve) => {
        chrome.storage.local.set({ [`dcmaar:meta:${key}`]: meta || null }, () => {
          resolve();
        });
      });
    }
  } catch (error) {
    // Silently ignore chrome.storage errors as this is fallback functionality
    console.debug('[HandleStorage] Chrome storage fallback failed:', error);
  }
}

/**
 * Retrieves metadata from chrome.storage.local fallback
 */
async function getMetadataFallback(key: string): Promise<unknown> {
  try {
    const chrome = (globalThis as unknown as { chrome?: typeof globalThis.chrome }).chrome;
    if (chrome?.storage?.local) {
      return new Promise((resolve) => {
        chrome.storage.local.get([`dcmaar:meta:${key}`], (result) => {
          resolve(result?.[`dcmaar:meta:${key}`] || undefined);
        });
      });
    }
  } catch (error) {
    console.debug('[HandleStorage] Chrome storage fallback retrieval failed:', error);
  }
  return undefined;
}

/**
 * Removes metadata from chrome.storage.local fallback
 */
async function removeMetadataFallback(key: string): Promise<void> {
  try {
    const chrome = (globalThis as unknown as { chrome?: typeof globalThis.chrome }).chrome;
    if (chrome?.storage?.local) {
      await new Promise<void>((resolve) => {
        chrome.storage.local.remove([`dcmaar:meta:${key}`], () => {
          resolve();
        });
      });
    }
  } catch (error) {
    console.debug('[HandleStorage] Chrome storage fallback removal failed:', error);
  }
}

/**
 * Stores a FileSystem handle with optional metadata
 * 
 * @param key - Unique identifier for the handle
 * @param handle - FileSystem handle to store
 * @param meta - Optional metadata to associate with the handle
 * @param options - Storage configuration options
 */
export async function setHandle<T = HandleMetadata>(
  key: string,
  handle: FileSystemHandle,
  meta?: T,
  options?: HandleStorageOptions
): Promise<void> {
  if (!key || !handle) {
    throw new Error('Key and handle are required for setHandle');
  }

  try {
    const db = await openDatabase(options);
    const { storeName } = { ...DEFAULT_OPTIONS, ...options };

    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readwrite');
      const store = transaction.objectStore(storeName);
      
      const storedHandle: StoredHandle<T> = {
        key,
        handle,
        meta,
        timestamp: Date.now(),
      };

      store.put(storedHandle);

      transaction.oncomplete = async () => {
        db.close();
        // Store metadata in chrome.storage as fallback
        await storeMetadataFallback(key, meta);
        resolve();
      };

      transaction.onerror = () => {
        db.close();
        reject(new Error(`Transaction failed: ${transaction.error?.message}`));
      };
    });
  } catch (error) {
    console.warn('[HandleStorage] setHandle failed:', error);
    throw error;
  }
}

/**
 * Retrieves a FileSystem handle by key
 * 
 * @param key - Unique identifier for the handle
 * @param options - Storage configuration options
 * @returns Promise resolving to the handle or undefined if not found
 */
export async function getHandle<T extends FileSystemHandle = FileSystemHandle>(
  key: string,
  options?: HandleStorageOptions
): Promise<T | undefined> {
  if (!key) {
    return undefined;
  }

  try {
    const db = await openDatabase(options);
    const { storeName } = { ...DEFAULT_OPTIONS, ...options };

    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readonly');
      const store = transaction.objectStore(storeName);
      const request = store.get(key);

      request.onsuccess = () => {
        db.close();
        const result = request.result as StoredHandle | undefined;
        resolve(result?.handle as T | undefined);
      };

      request.onerror = () => {
        db.close();
        reject(new Error(`Failed to get handle: ${request.error?.message}`));
      };
    });
  } catch (error) {
    console.warn('[HandleStorage] getHandle failed:', error);
    return undefined;
  }
}

/**
 * Retrieves metadata associated with a handle
 * 
 * @param key - Unique identifier for the handle
 * @param options - Storage configuration options
 * @returns Promise resolving to the metadata or undefined if not found
 */
export async function getMeta<T = HandleMetadata>(
  key: string,
  options?: HandleStorageOptions
): Promise<T | undefined> {
  if (!key) {
    return undefined;
  }

  try {
    const db = await openDatabase(options);
    const { storeName } = { ...DEFAULT_OPTIONS, ...options };

    const result = await new Promise<StoredHandle<T> | undefined>((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readonly');
      const store = transaction.objectStore(storeName);
      const request = store.get(key);

      request.onsuccess = () => {
        db.close();
        resolve(request.result as StoredHandle<T> | undefined);
      };

      request.onerror = () => {
        db.close();
        reject(new Error(`Failed to get metadata: ${request.error?.message}`));
      };
    });

    if (result?.meta !== undefined) {
      return result.meta;
    }

    // Fallback to chrome.storage.local
    const fallbackMeta = await getMetadataFallback(key);
    return fallbackMeta as T | undefined;

  } catch (error) {
    console.warn('[HandleStorage] getMeta failed:', error);
    
    // Try fallback on error
    try {
      const fallbackMeta = await getMetadataFallback(key);
      return fallbackMeta as T | undefined;
    } catch (fallbackError) {
      console.debug('[HandleStorage] Fallback getMeta also failed:', fallbackError);
      return undefined;
    }
  }
}

/**
 * Removes a handle and its associated metadata
 * 
 * @param key - Unique identifier for the handle to remove
 * @param options - Storage configuration options
 */
export async function removeHandle(
  key: string,
  options?: HandleStorageOptions
): Promise<void> {
  if (!key) {
    return;
  }

  try {
    const db = await openDatabase(options);
    const { storeName } = { ...DEFAULT_OPTIONS, ...options };

    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readwrite');
      const store = transaction.objectStore(storeName);
      
      store.delete(key);

      transaction.oncomplete = async () => {
        db.close();
        // Remove from chrome.storage fallback
        await removeMetadataFallback(key);
        resolve();
      };

      transaction.onerror = () => {
        db.close();
        reject(new Error(`Failed to remove handle: ${transaction.error?.message}`));
      };
    });
  } catch (error) {
    console.warn('[HandleStorage] removeHandle failed:', error);
    throw error;
  }
}

/**
 * Lists all stored handle keys
 * 
 * @param options - Storage configuration options
 * @returns Promise resolving to an array of stored keys
 */
export async function listHandleKeys(options?: HandleStorageOptions): Promise<string[]> {
  try {
    const db = await openDatabase(options);
    const { storeName } = { ...DEFAULT_OPTIONS, ...options };

    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readonly');
      const store = transaction.objectStore(storeName);
      const request = store.getAllKeys();

      request.onsuccess = () => {
        db.close();
        resolve(request.result as string[]);
      };

      request.onerror = () => {
        db.close();
        reject(new Error(`Failed to list keys: ${request.error?.message}`));
      };
    });
  } catch (error) {
    console.warn('[HandleStorage] listHandleKeys failed:', error);
    return [];
  }
}

/**
 * Gets complete stored handle information including metadata and timestamp
 * 
 * @param key - Unique identifier for the handle
 * @param options - Storage configuration options
 * @returns Promise resolving to the complete stored handle info or undefined
 */
export async function getStoredHandle<T = HandleMetadata>(
  key: string,
  options?: HandleStorageOptions
): Promise<StoredHandle<T> | undefined> {
  if (!key) {
    return undefined;
  }

  try {
    const db = await openDatabase(options);
    const { storeName } = { ...DEFAULT_OPTIONS, ...options };

    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readonly');
      const store = transaction.objectStore(storeName);
      const request = store.get(key);

      request.onsuccess = () => {
        db.close();
        resolve(request.result as StoredHandle<T> | undefined);
      };

      request.onerror = () => {
        db.close();
        reject(new Error(`Failed to get stored handle: ${request.error?.message}`));
      };
    });
  } catch (error) {
    console.warn('[HandleStorage] getStoredHandle failed:', error);
    return undefined;
  }
}

/**
 * Checks if IndexedDB is available in the current environment
 */
export function isStorageAvailable(): boolean {
  try {
    return typeof indexedDB !== 'undefined';
  } catch {
    return false;
  }
}

/**
 * Clears all stored handles and metadata
 * 
 * @param options - Storage configuration options
 */
export async function clearAllHandles(options?: HandleStorageOptions): Promise<void> {
  try {
    const db = await openDatabase(options);
    const { storeName } = { ...DEFAULT_OPTIONS, ...options };

    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readwrite');
      const store = transaction.objectStore(storeName);
      
      store.clear();

      transaction.oncomplete = () => {
        db.close();
        resolve();
      };

      transaction.onerror = () => {
        db.close();
        reject(new Error(`Failed to clear handles: ${transaction.error?.message}`));
      };
    });
  } catch (error) {
    console.warn('[HandleStorage] clearAllHandles failed:', error);
    throw error;
  }
}