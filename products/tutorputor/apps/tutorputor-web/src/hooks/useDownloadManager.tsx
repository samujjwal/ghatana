/**
 * TutorPutor - Download Manager Hook
 *
 * React hook for using the Content Download Manager.
 * Provides reactive state updates for download progress and queue.
 *
 * @doc.type module
 * @doc.purpose Download manager React hook
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  ContentDownloadManager,
  DownloadProgress,
  DownloadQueueItem,
  StorageQuota,
  DownloadStorage,
} from '../services/ContentDownloadManager';

// Type stubs
type Module = any;

// ============================================================================
// IndexedDB Storage Adapter
// ============================================================================

/**
 * Create an IndexedDB-based download storage adapter.
 */
function createIndexedDBStorage(): DownloadStorage {
  const DB_NAME = 'tutorputor-downloads';
  const DB_VERSION = 1;
  const STORE_NAME = 'modules';

  let db: IDBDatabase | null = null;

  const openDatabase = (): Promise<IDBDatabase> => {
    return new Promise((resolve, reject) => {
      if (db) {
        resolve(db);
        return;
      }

      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onerror = () => reject(request.error);
      request.onsuccess = () => {
        db = request.result;
        resolve(db);
      };

      request.onupgradeneeded = (event) => {
        const database = (event.target as IDBOpenDBRequest).result;
        if (!database.objectStoreNames.contains(STORE_NAME)) {
          database.createObjectStore(STORE_NAME, { keyPath: 'id' });
        }
      };
    });
  };

  return {
    async saveModule(module: Module & { downloadedAt: string }): Promise<void> {
      const database = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = database.transaction(STORE_NAME, 'readwrite');
        const store = tx.objectStore(STORE_NAME);
        const request = store.put(module);
        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve();
      });
    },

    async getModule(moduleId: string): Promise<Module | null> {
      const database = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = database.transaction(STORE_NAME, 'readonly');
        const store = tx.objectStore(STORE_NAME);
        const request = store.get(moduleId);
        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve(request.result || null);
      });
    },

    async deleteModule(moduleId: string): Promise<void> {
      const database = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = database.transaction(STORE_NAME, 'readwrite');
        const store = tx.objectStore(STORE_NAME);
        const request = store.delete(moduleId);
        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve();
      });
    },

    async getAllModules(): Promise<Module[]> {
      const database = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = database.transaction(STORE_NAME, 'readonly');
        const store = tx.objectStore(STORE_NAME);
        const request = store.getAll();
        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve(request.result || []);
      });
    },

    async getStorageUsed(): Promise<number> {
      const modules = await this.getAllModules();
      let totalSize = 0;
      for (const module of modules) {
        totalSize += JSON.stringify(module).length;
      }
      return totalSize;
    },
  };
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Hook result type.
 */
export interface UseDownloadManagerResult {
  // State
  queue: DownloadQueueItem[];
  downloads: Map<string, DownloadProgress>;
  downloadedModules: Module[];
  storageQuota: StorageQuota | null;
  isLoading: boolean;

  // Actions
  download: (moduleId: string, priority?: number) => Promise<void>;
  cancel: (moduleId: string) => void;
  retry: (moduleId: string) => Promise<void>;
  remove: (moduleId: string) => Promise<void>;
  pause: () => void;
  resume: () => void;
  clearAll: () => Promise<void>;

  // Queries
  isDownloaded: (moduleId: string) => boolean;
  isDownloading: (moduleId: string) => boolean;
  getProgress: (moduleId: string) => DownloadProgress | null;
  refresh: () => Promise<void>;
}

/**
 * Hook for managing content downloads.
 */
export function useDownloadManager(): UseDownloadManagerResult {
  const [queue, setQueue] = useState<DownloadQueueItem[]>([]);
  const [downloads, setDownloads] = useState<Map<string, DownloadProgress>>(new Map());
  const [downloadedModules, setDownloadedModules] = useState<Module[]>([]);
  const [storageQuota, setStorageQuota] = useState<StorageQuota | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const managerRef = useRef<ContentDownloadManager | null>(null);
  const downloadedIdsRef = useRef<Set<string>>(new Set());

  // Initialize manager
  useEffect(() => {
    const storage = createIndexedDBStorage();

    const manager = new ContentDownloadManager({
      apiBaseUrl: '',
      maxConcurrent: 2,
      storageQuotaBytes: 500 * 1024 * 1024, // 500 MB
      chunkSizeBytes: 1024 * 1024, // 1 MB chunks
      getAuthToken: async () => {
        // Get token from wherever it's stored
        return localStorage.getItem('auth_token');
      },
      storage,
    });

    manager.setCallbacks({
      onQueueChange: (newQueue) => setQueue([...newQueue]),
      onProgressChange: (progress) => {
        setDownloads((prev) => {
          const next = new Map(prev);
          next.set(progress.moduleId, progress);
          return next;
        });
      },
      onDownloadComplete: async (moduleId) => {
        const module = await storage.getModule(moduleId);
        if (module) {
          setDownloadedModules((prev) => [...prev.filter((m) => m.id !== moduleId), module]);
          downloadedIdsRef.current.add(moduleId);
        }
        refreshQuota();
      },
      onDownloadError: (moduleId, error) => {
        console.error(`Download failed for ${moduleId}:`, error);
      },
      onStorageWarning: (quota) => {
        console.warn('Storage quota warning:', quota);
      },
    });

    managerRef.current = manager;

    // Load initial state
    const loadInitialState = async () => {
      setIsLoading(true);
      try {
        const modules = await storage.getAllModules();
        setDownloadedModules(modules);
        downloadedIdsRef.current = new Set(modules.map((m) => m.id));

        const quota = await manager.getStorageQuota();
        setStorageQuota(quota);
      } finally {
        setIsLoading(false);
      }
    };

    loadInitialState();
  }, []);

  const refreshQuota = useCallback(async () => {
    if (managerRef.current) {
      const quota = await managerRef.current.getStorageQuota();
      setStorageQuota(quota);
    }
  }, []);

  const refresh = useCallback(async () => {
    if (!managerRef.current) return;

    setIsLoading(true);
    try {
      const modules = await managerRef.current.getDownloadedModules();
      setDownloadedModules(modules);
      downloadedIdsRef.current = new Set(modules.map((m) => m.id));
      await refreshQuota();
    } finally {
      setIsLoading(false);
    }
  }, [refreshQuota]);

  const download = useCallback(async (moduleId: string, priority = 0) => {
    if (managerRef.current) {
      await managerRef.current.queueDownload(moduleId, priority);
    }
  }, []);

  const cancel = useCallback((moduleId: string) => {
    managerRef.current?.cancel(moduleId);
  }, []);

  const retry = useCallback(async (moduleId: string) => {
    if (managerRef.current) {
      await managerRef.current.retry(moduleId);
    }
  }, []);

  const remove = useCallback(async (moduleId: string) => {
    if (managerRef.current) {
      await managerRef.current.deleteDownload(moduleId);
      setDownloadedModules((prev) => prev.filter((m) => m.id !== moduleId));
      downloadedIdsRef.current.delete(moduleId);
      await refreshQuota();
    }
  }, [refreshQuota]);

  const pause = useCallback(() => {
    managerRef.current?.pause();
  }, []);

  const resume = useCallback(() => {
    managerRef.current?.resume();
  }, []);

  const clearAll = useCallback(async () => {
    if (managerRef.current) {
      await managerRef.current.clearAllDownloads();
      setDownloadedModules([]);
      downloadedIdsRef.current.clear();
      setDownloads(new Map());
      await refreshQuota();
    }
  }, [refreshQuota]);

  const isDownloaded = useCallback((moduleId: string) => {
    return downloadedIdsRef.current.has(moduleId);
  }, []);

  const isDownloading = useCallback((moduleId: string) => {
    return managerRef.current?.isDownloading(moduleId) ?? false;
  }, []);

  const getProgress = useCallback((moduleId: string) => {
    return downloads.get(moduleId) ?? null;
  }, [downloads]);

  return {
    queue,
    downloads,
    downloadedModules,
    storageQuota,
    isLoading,
    download,
    cancel,
    retry,
    remove,
    pause,
    resume,
    clearAll,
    isDownloaded,
    isDownloading,
    getProgress,
    refresh,
  };
}

// ============================================================================
// Download Card Component
// ============================================================================

export interface DownloadCardProps {
  module: {
    id: string;
    title: string;
    totalSizeBytes?: number;
  };
  progress: DownloadProgress | null;
  isDownloaded: boolean;
  onDownload: () => void;
  onCancel: () => void;
  onRetry: () => void;
  onRemove: () => void;
}

/**
 * Component showing download status for a module.
 */
export function DownloadCard({
  module,
  progress,
  isDownloaded,
  onDownload,
  onCancel,
  onRetry,
  onRemove,
}: DownloadCardProps) {
  const formatBytes = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const renderAction = () => {
    if (isDownloaded) {
      return (
        <button
          onClick={onRemove}
          className="text-red-600 hover:text-red-700"
        >
          Remove
        </button>
      );
    }

    if (!progress) {
      return (
        <button
          onClick={onDownload}
          className="text-blue-600 hover:text-blue-700"
        >
          Download
        </button>
      );
    }

    switch (progress.status) {
      case 'queued':
        return (
          <button
            onClick={onCancel}
            className="text-gray-600 hover:text-gray-700"
          >
            Cancel
          </button>
        );

      case 'downloading':
        return (
          <button
            onClick={onCancel}
            className="text-red-600 hover:text-red-700"
          >
            Cancel
          </button>
        );

      case 'failed':
        return (
          <button
            onClick={onRetry}
            className="text-blue-600 hover:text-blue-700"
          >
            Retry
          </button>
        );

      case 'completed':
        return (
          <button
            onClick={onRemove}
            className="text-red-600 hover:text-red-700"
          >
            Remove
          </button>
        );

      default:
        return null;
    }
  };

  return (
    <div className="flex items-center gap-4 p-4 bg-white rounded-lg shadow">
      <div className="flex-1">
        <h3 className="font-medium">{module.title}</h3>
        <div className="text-sm text-gray-500">
          {module.totalSizeBytes && formatBytes(module.totalSizeBytes)}
          {progress?.currentItem && ` • ${progress.currentItem}`}
        </div>

        {progress?.status === 'downloading' && (
          <div className="mt-2">
            <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
              <div
                className="h-full bg-blue-500 transition-all"
                style={{ width: `${progress.progress}%` }}
              />
            </div>
            <div className="text-xs text-gray-500 mt-1">
              {formatBytes(progress.bytesDownloaded)} / {formatBytes(progress.totalBytes)}
            </div>
          </div>
        )}

        {progress?.error && (
          <div className="text-sm text-red-600 mt-1">
            {progress.error}
          </div>
        )}
      </div>

      <div className="flex-shrink-0">
        {renderAction()}
      </div>
    </div>
  );
}
