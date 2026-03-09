/**
 * TutorPutor Mobile - Offline Hooks
 *
 * React hooks for offline-first functionality in React Native.
 * Uses MMKV and SQLite for persistence, NetInfo for connectivity.
 *
 * @doc.type module
 * @doc.purpose React Native offline hooks
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { database } from '../storage/SQLiteStorage';
import { cacheStorage, syncStorage, CacheKeys } from '../storage/MMKVStorage';
import { syncService, SyncResult } from '../services/BackgroundSyncService';
import type { Module } from '@ghatana/tutorputor-contracts';

// ============================================================================
// Network Status Hook
// ============================================================================

export interface NetworkStatus {
  isConnected: boolean;
  isInternetReachable: boolean | null;
  connectionType: string;
}

/**
 * Hook to monitor network connectivity.
 */
export function useNetworkStatus(): NetworkStatus {
  const [status, setStatus] = useState<NetworkStatus>({
    isConnected: true,
    isInternetReachable: true,
    connectionType: 'unknown',
  });

  useEffect(() => {
    // Get initial state
    NetInfo.fetch().then((state) => {
      setStatus({
        isConnected: state.isConnected ?? false,
        isInternetReachable: state.isInternetReachable,
        connectionType: state.type,
      });
    });

    // Subscribe to changes
    const unsubscribe = NetInfo.addEventListener((state) => {
      setStatus({
        isConnected: state.isConnected ?? false,
        isInternetReachable: state.isInternetReachable,
        connectionType: state.type,
      });
    });

    return unsubscribe;
  }, []);

  return status;
}

// ============================================================================
// Offline State Hook
// ============================================================================

export interface OfflineState {
  isOnline: boolean;
  pendingMutations: number;
  lastSyncAt: Date | null;
  syncStatus: 'idle' | 'syncing' | 'error';
}

/**
 * Hook to manage offline state.
 */
export function useOfflineState(): OfflineState & {
  sync: () => Promise<SyncResult>;
  queueMutation: (type: string, payload: unknown) => Promise<string>;
} {
  const { isConnected } = useNetworkStatus();
  const [pendingMutations, setPendingMutations] = useState(0);
  const [lastSyncAt, setLastSyncAt] = useState<Date | null>(null);
  const [syncStatus, setSyncStatus] = useState<'idle' | 'syncing' | 'error'>('idle');

  // Load initial state
  useEffect(() => {
    const loadState = async () => {
      const count = await database.getMutationCount();
      setPendingMutations(count);

      const lastSync = syncStorage.get<number>(CacheKeys.lastSync());
      if (lastSync) {
        setLastSyncAt(new Date(lastSync));
      }
    };

    loadState();
  }, []);

  const sync = useCallback(async () => {
    setSyncStatus('syncing');
    try {
      const result = await syncService.sync();
      setSyncStatus(result.success ? 'idle' : 'error');
      setLastSyncAt(new Date());

      // Refresh pending count
      const count = await database.getMutationCount();
      setPendingMutations(count);

      return result;
    } catch (error) {
      setSyncStatus('error');
      throw error;
    }
  }, []);

  const queueMutation = useCallback(async (type: string, payload: unknown) => {
    const id = await database.queueMutation(type, payload);
    const count = await database.getMutationCount();
    setPendingMutations(count);
    return id;
  }, []);

  return {
    isOnline: isConnected,
    pendingMutations,
    lastSyncAt,
    syncStatus,
    sync,
    queueMutation,
  };
}

// ============================================================================
// Cached Module Hook
// ============================================================================

/**
 * Hook for caching and retrieving modules.
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

  useEffect(() => {
    if (!moduleId) {
      setIsLoading(false);
      return;
    }

    const loadModule = async () => {
      try {
        setIsLoading(true);
        const cached = await database.getModule(moduleId);

        if (cached) {
          // Parse lessons and quizzes from JSON
          setModule({
            id: cached.id,
            title: cached.title,
            description: cached.description,
            category: cached.category,
            grade: cached.grade,
            lessons: JSON.parse(cached.lessonsJson),
            quizzes: JSON.parse(cached.quizzesJson),
            totalSizeBytes: cached.totalSizeBytes,
            version: cached.version,
          } as Module);
          setIsFromCache(true);
        }
      } catch (err) {
        setError(err instanceof Error ? err : new Error('Failed to load module'));
      } finally {
        setIsLoading(false);
      }
    };

    loadModule();
  }, [moduleId]);

  const cacheModule = useCallback(async (mod: Module) => {
    await database.saveModule({
      id: mod.id,
      title: mod.title,
      description: mod.description ?? '',
      category: mod.category ?? '',
      grade: mod.grade ?? 0,
      lessonsJson: JSON.stringify(mod.lessons ?? []),
      quizzesJson: JSON.stringify(mod.quizzes ?? []),
      downloadedAt: new Date().toISOString(),
      totalSizeBytes: mod.totalSizeBytes ?? 0,
      version: mod.version ?? '1.0.0',
    });
    setModule(mod);
    setIsFromCache(false);
  }, []);

  const clearCache = useCallback(async () => {
    if (moduleId) {
      await database.deleteModule(moduleId);
      setModule(null);
      setIsFromCache(false);
    }
  }, [moduleId]);

  return { module, isLoading, isFromCache, error, cacheModule, clearCache };
}

// ============================================================================
// Downloaded Modules Hook
// ============================================================================

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
  refresh: () => Promise<void>;
} {
  const [modules, setModules] = useState<Module[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const downloadedIds = useRef(new Set<string>());

  const loadModules = useCallback(async () => {
    try {
      setIsLoading(true);
      const records = await database.getAllModules();

      const mods: Module[] = records.map((record) => ({
        id: record.id,
        title: record.title,
        description: record.description,
        category: record.category,
        grade: record.grade,
        lessons: JSON.parse(record.lessonsJson),
        quizzes: JSON.parse(record.quizzesJson),
        totalSizeBytes: record.totalSizeBytes,
        version: record.version,
      })) as Module[];

      setModules(mods);
      downloadedIds.current = new Set(mods.map((m) => m.id));
    } catch (err) {
      console.error('Failed to load downloaded modules:', err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadModules();
  }, [loadModules]);

  const downloadModule = useCallback(async (module: Module) => {
    await database.saveModule({
      id: module.id,
      title: module.title,
      description: module.description ?? '',
      category: module.category ?? '',
      grade: module.grade ?? 0,
      lessonsJson: JSON.stringify(module.lessons ?? []),
      quizzesJson: JSON.stringify(module.quizzes ?? []),
      downloadedAt: new Date().toISOString(),
      totalSizeBytes: module.totalSizeBytes ?? 0,
      version: module.version ?? '1.0.0',
    });

    setModules((prev) => [...prev.filter((m) => m.id !== module.id), module]);
    downloadedIds.current.add(module.id);
  }, []);

  const removeModule = useCallback(async (moduleId: string) => {
    await database.deleteModule(moduleId);
    setModules((prev) => prev.filter((m) => m.id !== moduleId));
    downloadedIds.current.delete(moduleId);
  }, []);

  const isDownloaded = useCallback(
    (moduleId: string) => downloadedIds.current.has(moduleId),
    []
  );

  const totalSize = modules.reduce((sum, m) => sum + (m.totalSizeBytes ?? 0), 0);

  return {
    modules,
    isLoading,
    totalSize,
    downloadModule,
    removeModule,
    isDownloaded,
    refresh: loadModules,
  };
}

// ============================================================================
// Progress Hook
// ============================================================================

/**
 * Hook for managing offline-capable progress updates.
 */
export function useOfflineProgress(moduleId: string | undefined): {
  progress: number;
  currentLesson: number;
  completedLessons: string[];
  quizScores: Record<string, number>;
  timeSpentMs: number;
  isLoading: boolean;
  updateProgress: (lessonId: string, progress: number) => Promise<void>;
  completeLesson: (lessonId: string, timeSpentMs: number) => Promise<void>;
  submitQuiz: (quizId: string, score: number) => Promise<void>;
} {
  const { isOnline, queueMutation } = useOfflineState();
  const [progressData, setProgressData] = useState({
    progress: 0,
    currentLesson: 0,
    completedLessons: [] as string[],
    quizScores: {} as Record<string, number>,
    timeSpentMs: 0,
  });
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!moduleId) {
      setIsLoading(false);
      return;
    }

    const loadProgress = async () => {
      try {
        const record = await database.getProgress(moduleId);
        if (record) {
          setProgressData({
            progress: record.progress,
            currentLesson: record.currentLesson,
            completedLessons: record.completedLessons,
            quizScores: record.quizScores,
            timeSpentMs: record.timeSpentMs,
          });
        }
      } catch (err) {
        console.error('Failed to load progress:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadProgress();
  }, [moduleId]);

  const updateProgress = useCallback(
    async (lessonId: string, progress: number) => {
      if (!moduleId) return;

      const newData = { ...progressData, progress };
      setProgressData(newData);

      await database.saveProgress({
        moduleId,
        ...newData,
        updatedAt: new Date().toISOString(),
        syncStatus: isOnline ? 'synced' : 'pending',
      });

      if (!isOnline) {
        await queueMutation('UPDATE_PROGRESS', { moduleId, lessonId, progress });
      }
    },
    [moduleId, progressData, isOnline, queueMutation]
  );

  const completeLesson = useCallback(
    async (lessonId: string, timeSpent: number) => {
      if (!moduleId) return;

      const newData = {
        ...progressData,
        completedLessons: [...progressData.completedLessons, lessonId],
        timeSpentMs: progressData.timeSpentMs + timeSpent,
        progress: Math.min(
          100,
          ((progressData.completedLessons.length + 1) / 10) * 100 // Assume 10 lessons
        ),
      };
      setProgressData(newData);

      await database.saveProgress({
        moduleId,
        ...newData,
        updatedAt: new Date().toISOString(),
        syncStatus: isOnline ? 'synced' : 'pending',
      });

      if (!isOnline) {
        await queueMutation('COMPLETE_LESSON', {
          moduleId,
          lessonId,
          timeSpentMs: timeSpent,
        });
      }
    },
    [moduleId, progressData, isOnline, queueMutation]
  );

  const submitQuiz = useCallback(
    async (quizId: string, score: number) => {
      if (!moduleId) return;

      const newData = {
        ...progressData,
        quizScores: { ...progressData.quizScores, [quizId]: score },
      };
      setProgressData(newData);

      await database.saveProgress({
        moduleId,
        ...newData,
        updatedAt: new Date().toISOString(),
        syncStatus: isOnline ? 'synced' : 'pending',
      });

      if (!isOnline) {
        await queueMutation('SUBMIT_QUIZ', { moduleId, quizId, score });
      }
    },
    [moduleId, progressData, isOnline, queueMutation]
  );

  return {
    ...progressData,
    isLoading,
    updateProgress,
    completeLesson,
    submitQuiz,
  };
}
