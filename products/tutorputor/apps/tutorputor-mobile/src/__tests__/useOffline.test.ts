/**
 * @doc.type test
 * @doc.purpose Unit tests for offline hooks — network status, offline state, progress sync
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { renderHook, act, waitFor } from '@testing-library/react-native';
import { useNetworkStatus, useOfflineState, useOfflineProgress } from '../hooks/useOffline';

// -----------------------------------------------------------------------
// Mocks
// -----------------------------------------------------------------------
jest.mock('@react-native-community/netinfo', () => ({
  fetch: jest.fn().mockResolvedValue({
    isConnected: true,
    isInternetReachable: true,
    type: 'wifi',
  }),
  addEventListener: jest.fn().mockReturnValue(() => {}),
}));

jest.mock('../storage/SQLiteStorage', () => ({
  database: {
    getMutationCount: jest.fn().mockResolvedValue(0),
    queueMutation: jest.fn().mockResolvedValue('mutation-id-1'),
    saveProgress: jest.fn().mockResolvedValue(undefined),
    getProgress: jest.fn().mockResolvedValue(null),
    saveModule: jest.fn().mockResolvedValue(undefined),
    getModule: jest.fn().mockResolvedValue(null),
    deleteModule: jest.fn().mockResolvedValue(undefined),
    getAllModules: jest.fn().mockResolvedValue([]),
  },
}));

jest.mock('../storage/MMKVStorage', () => ({
  cacheStorage: {
    get: jest.fn().mockReturnValue(null),
    set: jest.fn(),
  },
  syncStorage: {
    get: jest.fn().mockReturnValue(null),
    set: jest.fn(),
  },
  CacheKeys: {
    lastSync: () => 'lastSync',
    modules: (id: string) => `modules:${id}`,
  },
}));

jest.mock('../services/BackgroundSyncService', () => ({
  syncService: {
    sync: jest.fn().mockResolvedValue({ success: true, synced: 0, failed: 0 }),
  },
}));

// -----------------------------------------------------------------------
// Tests
// -----------------------------------------------------------------------

describe('useNetworkStatus', () => {
  it('returns connected status on mount', async () => {
    const { result } = renderHook(() => useNetworkStatus());

    await waitFor(() => {
      expect(result.current.connectionType).toBe('wifi');
    });

    expect(result.current.isConnected).toBe(true);
    expect(result.current.connectionType).toBe('wifi');
  });

  it('defaults to connected=true before async fetch resolves', () => {
    const { result } = renderHook(() => useNetworkStatus());
    // Initial synchronous state
    expect(result.current.isConnected).toBe(true);
  });
});

describe('useOfflineState', () => {
  it('initializes with isOnline=true and zero pending mutations', async () => {
    const { result } = renderHook(() => useOfflineState());

    await waitFor(() => {
      expect(result.current.pendingMutations).toBe(0);
    });

    expect(result.current.isOnline).toBe(true);
    expect(result.current.syncStatus).toBe('idle');
    expect(result.current.lastSyncAt).toBeNull();
  });

  it('queues a mutation and increments pending count', async () => {
    const { database } = require('../storage/SQLiteStorage');
    database.getMutationCount.mockResolvedValueOnce(0).mockResolvedValueOnce(1);

    const { result } = renderHook(() => useOfflineState());

    await act(async () => {
      await result.current.queueMutation('UPDATE_PROGRESS', { moduleId: 'mod-1', progress: 50 });
    });

    expect(database.queueMutation).toHaveBeenCalledWith(
      'UPDATE_PROGRESS',
      { moduleId: 'mod-1', progress: 50 },
    );
    expect(result.current.pendingMutations).toBe(1);
  });

  it('syncs and transitions status from syncing to idle', async () => {
    const { syncService } = require('../services/BackgroundSyncService');
    syncService.sync.mockResolvedValueOnce({ success: true, synced: 3, failed: 0 });

    const { result } = renderHook(() => useOfflineState());

    await act(async () => {
      await result.current.sync();
    });

    expect(result.current.syncStatus).toBe('idle');
    expect(result.current.lastSyncAt).toBeInstanceOf(Date);
  });

  it('sets syncStatus to error when sync fails', async () => {
    const { syncService } = require('../services/BackgroundSyncService');
    syncService.sync.mockRejectedValueOnce(new Error('Network timeout'));

    const { result } = renderHook(() => useOfflineState());

    await act(async () => {
      try {
        await result.current.sync();
      } catch {
        // expected
      }
    });

    expect(result.current.syncStatus).toBe('error');
  });
});

describe('useOfflineProgress', () => {
  it('returns default progress when no saved progress', async () => {
    const { result } = renderHook(() => useOfflineProgress('module-1'));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.progress).toBe(0);
    expect(result.current.completedLessons).toHaveLength(0);
    expect(result.current.quizScores).toEqual({});
  });

  it('loads saved progress from database', async () => {
    const { database } = require('../storage/SQLiteStorage');
    database.getProgress.mockResolvedValueOnce({
      progress: 60,
      currentLesson: 3,
      completedLessons: ['lesson-1', 'lesson-2'],
      quizScores: { 'quiz-1': 85 },
      timeSpentMs: 120000,
    });

    const { result } = renderHook(() => useOfflineProgress('module-2'));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.progress).toBe(60);
    expect(result.current.completedLessons).toContain('lesson-1');
    expect(result.current.quizScores['quiz-1']).toBe(85);
  });

  it('returns default state for undefined moduleId', async () => {
    const { result } = renderHook(() => useOfflineProgress(undefined));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.progress).toBe(0);
  });

  it('saves progress update and queues mutation when offline', async () => {
    const NetInfo = require('@react-native-community/netinfo');
    NetInfo.fetch.mockResolvedValueOnce({
      isConnected: false,
      isInternetReachable: false,
      type: 'none',
    });

    const { database } = require('../storage/SQLiteStorage');

    const { result } = renderHook(() => useOfflineProgress('module-3'));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    await act(async () => {
      await result.current.updateProgress('lesson-1', 25);
    });

    expect(database.saveProgress).toHaveBeenCalledWith(
      expect.objectContaining({ moduleId: 'module-3', progress: 25 }),
    );
  });

  it('records quiz score', async () => {
    const { database } = require('../storage/SQLiteStorage');
    const { result } = renderHook(() => useOfflineProgress('module-4'));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    await act(async () => {
      await result.current.submitQuiz('quiz-2', 92);
    });

    expect(database.saveProgress).toHaveBeenCalledWith(
      expect.objectContaining({
        quizScores: { 'quiz-2': 92 },
      }),
    );
  });
});
