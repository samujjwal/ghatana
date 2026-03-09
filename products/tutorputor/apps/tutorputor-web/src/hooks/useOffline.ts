/**
 * TutorPutor Offline Hooks
 *
 * React hooks for offline-first functionality.
 * Wraps @ghatana/state offline infrastructure with TutorPutor-specific logic.
 *
 * @doc.type module
 * @doc.purpose TutorPutor offline hooks
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import type { Module } from '@ghatana/tutorputor-contracts';

// Re-export types from @ghatana/state for convenience
// In a real implementation, these would come from the state library
interface OfflineState {
  isOnline: boolean;
  lastSyncAt: string | null;
  pendingMutationsCount: number;
  syncStatus: 'synced' | 'pending' | 'error' | 'offline';
}

interface CachedItem<T> {
  key: string;
  data: T;
  cachedAt: string;
  expiresAt: string;
  version: number;
}

/**
 * Service worker registration state.
 */
interface ServiceWorkerState {
  isSupported: boolean;
  isRegistered: boolean;
  isUpdateAvailable: boolean;
  registration: ServiceWorkerRegistration | null;
}

/**
 * Hook to manage service worker registration.
 */
export function useServiceWorker(): ServiceWorkerState & {
  register: () => Promise<void>;
  update: () => Promise<void>;
} {
  const [state, setState] = useState<ServiceWorkerState>({
    isSupported: typeof navigator !== 'undefined' && 'serviceWorker' in navigator,
    isRegistered: false,
    isUpdateAvailable: false,
    registration: null,
  });

  const register = useCallback(async () => {
    if (!state.isSupported) return;

    try {
      const registration = await navigator.serviceWorker.register('/sw.js', {
        scope: '/',
      });

      setState((prev) => ({
        ...prev,
        isRegistered: true,
        registration,
      }));

      // Check for updates
      registration.addEventListener('updatefound', () => {
        const newWorker = registration.installing;
        if (newWorker) {
          newWorker.addEventListener('statechange', () => {
            if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
              setState((prev) => ({ ...prev, isUpdateAvailable: true }));
            }
          });
        }
      });
    } catch (error) {
      console.error('Service worker registration failed:', error);
    }
  }, [state.isSupported]);

  const update = useCallback(async () => {
    if (state.registration) {
      await state.registration.update();
      // Skip waiting to activate new service worker
      state.registration.waiting?.postMessage({ type: 'SKIP_WAITING' });
      window.location.reload();
    }
  }, [state.registration]);

  return { ...state, register, update };
}

/**
 * Hook to monitor online/offline status.
 */
export function useOnlineStatus(): {
  isOnline: boolean;
  wasOffline: boolean;
  offlineSince: Date | null;
} {
  const [isOnline, setIsOnline] = useState(
    typeof navigator !== 'undefined' ? navigator.onLine : true
  );
  const [wasOffline, setWasOffline] = useState(false);
  const [offlineSince, setOfflineSince] = useState<Date | null>(null);

  useEffect(() => {
    const handleOnline = () => {
      setIsOnline(true);
      setWasOffline(true);
      setOfflineSince(null);
    };

    const handleOffline = () => {
      setIsOnline(false);
      setOfflineSince(new Date());
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return { isOnline, wasOffline, offlineSince };
}

/**
 * Hook for caching modules for offline access.
 */
export function useCachedModule(moduleId: string | undefined): {
  module: Module | null;
  isLoading: boolean;
  isFromCache: boolean;
  error: Error | null;
  cacheModule: (module: Module) => Promise<void>;
  clearCache: () => Promise<void>;
} {
  const [module, setModule] = useState<Module | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isFromCache, setIsFromCache] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const dbRef = useRef<IDBDatabase | null>(null);

  // Initialize IndexedDB
  useEffect(() => {
    if (!moduleId) {
      setIsLoading(false);
      return;
    }

    const initDB = async () => {
      try {
        const db = await openDatabase();
        dbRef.current = db;
        
        // Try to get from cache first
        const cached = await getFromCache(db, moduleId);
        if (cached) {
          setModule(cached);
          setIsFromCache(true);
        }
        setIsLoading(false);
      } catch (err) {
        setError(err instanceof Error ? err : new Error('Failed to initialize cache'));
        setIsLoading(false);
      }
    };

    initDB();

    return () => {
      dbRef.current?.close();
    };
  }, [moduleId]);

  const cacheModule = useCallback(async (moduleData: Module) => {
    if (!dbRef.current) return;

    try {
      await saveToCache(dbRef.current, moduleData);
      setModule(moduleData);
      setIsFromCache(false);
    } catch (err) {
      console.error('Failed to cache module:', err);
    }
  }, []);

  const clearCache = useCallback(async () => {
    if (!dbRef.current || !moduleId) return;

    try {
      await removeFromCache(dbRef.current, moduleId);
      setModule(null);
      setIsFromCache(false);
    } catch (err) {
      console.error('Failed to clear cache:', err);
    }
  }, [moduleId]);

  return { module, isLoading, isFromCache, error, cacheModule, clearCache };
}

/**
 * Hook for managing downloaded modules.
 */
export function useDownloadedModules(): {
  modules: Module[];
  isLoading: boolean;
  totalSize: number;
  downloadModule: (module: Module) => Promise<void>;
  removeModule: (moduleId: string) => Promise<void>;
  isDownloaded: (moduleId: string) => boolean;
} {
  const [modules, setModules] = useState<Module[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [downloadedIds, setDownloadedIds] = useState<Set<string>>(new Set());

  const dbRef = useRef<IDBDatabase | null>(null);

  useEffect(() => {
    const loadModules = async () => {
      try {
        const db = await openDatabase();
        dbRef.current = db;

        const allModules = await getAllModules(db);
        setModules(allModules);
        setDownloadedIds(new Set(allModules.map((m) => m.id)));
      } catch (err) {
        console.error('Failed to load downloaded modules:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadModules();

    return () => {
      dbRef.current?.close();
    };
  }, []);

  const downloadModule = useCallback(async (module: Module) => {
    if (!dbRef.current) return;

    await saveToCache(dbRef.current, module);
    setModules((prev) => [...prev.filter((m) => m.id !== module.id), module]);
    setDownloadedIds((prev) => new Set([...prev, module.id]));
  }, []);

  const removeModule = useCallback(async (moduleId: string) => {
    if (!dbRef.current) return;

    await removeFromCache(dbRef.current, moduleId);
    setModules((prev) => prev.filter((m) => m.id !== moduleId));
    setDownloadedIds((prev) => {
      const next = new Set(prev);
      next.delete(moduleId);
      return next;
    });
  }, []);

  const isDownloaded = useCallback(
    (moduleId: string) => downloadedIds.has(moduleId),
    [downloadedIds]
  );

  const totalSize = modules.reduce((sum, m) => sum + (m.totalSizeBytes || 0), 0);

  return {
    modules,
    isLoading,
    totalSize,
    downloadModule,
    removeModule,
    isDownloaded,
  };
}

/**
 * Hook for offline-capable progress updates.
 */
export function useOfflineProgress(): {
  updateProgress: (moduleId: string, lessonId: string, progress: number) => Promise<void>;
  completeLesson: (moduleId: string, lessonId: string, timeSpentMs: number) => Promise<void>;
  pendingUpdates: number;
  syncProgress: () => Promise<void>;
} {
  const [pendingUpdates, setPendingUpdates] = useState(0);
  const { isOnline } = useOnlineStatus();

  const queueMutation = useCallback(async (type: string, payload: unknown) => {
    try {
      const db = await openDatabase();
      const tx = db.transaction('mutations', 'readwrite');
      const store = tx.objectStore('mutations');

      const mutation = {
        id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        type,
        payload,
        createdAt: new Date().toISOString(),
        retryCount: 0,
        maxRetries: 3,
      };

      await new Promise<void>((resolve, reject) => {
        const request = store.add(mutation);
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });

      setPendingUpdates((prev) => prev + 1);
      db.close();
    } catch (err) {
      console.error('Failed to queue mutation:', err);
      throw err;
    }
  }, []);

  const updateProgress = useCallback(
    async (moduleId: string, lessonId: string, progress: number) => {
      const payload = { moduleId, lessonId, progress, timestamp: Date.now() };

      if (isOnline) {
        try {
          // Try immediate sync
          await fetch('/api/progress', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
          });
        } catch {
          // Fall back to queue
          await queueMutation('UPDATE_PROGRESS', payload);
        }
      } else {
        await queueMutation('UPDATE_PROGRESS', payload);
      }
    },
    [isOnline, queueMutation]
  );

  const completeLesson = useCallback(
    async (moduleId: string, lessonId: string, timeSpentMs: number) => {
      const payload = { moduleId, lessonId, timeSpentMs, completedAt: new Date().toISOString() };

      if (isOnline) {
        try {
          await fetch('/api/progress/complete-lesson', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
          });
        } catch {
          await queueMutation('COMPLETE_LESSON', payload);
        }
      } else {
        await queueMutation('COMPLETE_LESSON', payload);
      }
    },
    [isOnline, queueMutation]
  );

  const syncProgress = useCallback(async () => {
    if (!isOnline) return;

    try {
      const db = await openDatabase();
      const tx = db.transaction('mutations', 'readonly');
      const store = tx.objectStore('mutations');

      const mutations = await new Promise<unknown[]>((resolve, reject) => {
        const request = store.getAll();
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      });

      // Process mutations in order
      for (const mutation of mutations) {
        // ... sync logic would go here
      }

      db.close();
      setPendingUpdates(0);
    } catch (err) {
      console.error('Failed to sync progress:', err);
    }
  }, [isOnline]);

  return { updateProgress, completeLesson, pendingUpdates, syncProgress };
}

// ============================================================================
// IndexedDB Helper Functions
// ============================================================================

const DB_NAME = 'tutorputor-offline';
const DB_VERSION = 1;

async function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;

      if (!db.objectStoreNames.contains('modules')) {
        const store = db.createObjectStore('modules', { keyPath: 'id' });
        store.createIndex('downloadedAt', 'downloadedAt');
        store.createIndex('category', 'category');
      }

      if (!db.objectStoreNames.contains('mutations')) {
        const store = db.createObjectStore('mutations', { keyPath: 'id' });
        store.createIndex('createdAt', 'createdAt');
        store.createIndex('type', 'type');
      }

      if (!db.objectStoreNames.contains('cache')) {
        const store = db.createObjectStore('cache', { keyPath: 'key' });
        store.createIndex('expiresAt', 'expiresAt');
      }
    };
  });
}

async function getFromCache(db: IDBDatabase, moduleId: string): Promise<Module | null> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction('modules', 'readonly');
    const store = tx.objectStore('modules');
    const request = store.get(moduleId);

    request.onsuccess = () => resolve(request.result || null);
    request.onerror = () => reject(request.error);
  });
}

async function saveToCache(db: IDBDatabase, module: Module): Promise<void> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction('modules', 'readwrite');
    const store = tx.objectStore('modules');

    const entry = {
      ...module,
      downloadedAt: new Date().toISOString(),
    };

    const request = store.put(entry);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

async function removeFromCache(db: IDBDatabase, moduleId: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction('modules', 'readwrite');
    const store = tx.objectStore('modules');
    const request = store.delete(moduleId);

    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

async function getAllModules(db: IDBDatabase): Promise<Module[]> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction('modules', 'readonly');
    const store = tx.objectStore('modules');
    const request = store.getAll();

    request.onsuccess = () => resolve(request.result || []);
    request.onerror = () => reject(request.error);
  });
}
