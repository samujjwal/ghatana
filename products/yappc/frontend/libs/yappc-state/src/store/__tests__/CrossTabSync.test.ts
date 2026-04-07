import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getSyncStatistics,
  readAtomFromStorage,
  subscribeToSync,
  syncStateAcrossTabs,
  writeAtomToStorage,
} from '../cross-tab-sync';

const STORAGE_PREFIX = 'jotai-state:';
const TAB_ID_KEY = 'tab-id';

function createStorageMock(): Storage {
  const values = new Map<string, string>();

  return {
    get length() {
      return values.size;
    },
    clear() {
      values.clear();
    },
    getItem(key: string) {
      return values.has(key) ? (values.get(key) ?? null) : null;
    },
    key(index: number) {
      return Array.from(values.keys())[index] ?? null;
    },
    removeItem(key: string) {
      values.delete(key);
    },
    setItem(key: string, value: string) {
      values.set(key, value);
    },
  };
}

vi.stubGlobal('localStorage', createStorageMock());
vi.stubGlobal('sessionStorage', createStorageMock());

describe('cross-tab-sync', () => {
  let cleanup: (() => void) | null;

  beforeEach(() => {
    cleanup = null;
    localStorage.clear();
    sessionStorage.clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup?.();
    cleanup = null;
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('initializes and tears down the sync manager', () => {
    cleanup = syncStateAcrossTabs();

    expect(getSyncStatistics()).toEqual({
      isInitialized: true,
      listenerCount: 0,
      pendingWrites: 0,
      activeDebounceTimers: 0,
    });

    cleanup();
    cleanup = null;

    expect(getSyncStatistics()).toEqual({
      isInitialized: false,
      listenerCount: 0,
      pendingWrites: 0,
      activeDebounceTimers: 0,
    });
  });

  it('debounces writes and persists only the latest value', () => {
    cleanup = syncStateAcrossTabs({ debounceDelay: 25 });

    writeAtomToStorage('count', 1);
    writeAtomToStorage('count', 2);

    expect(localStorage.getItem(`${STORAGE_PREFIX}count`)).toBeNull();
    expect(getSyncStatistics().pendingWrites).toBe(1);

    // The sync manager is a singleton, so the effective debounce remains
    // the initially configured default across this test file.
    vi.advanceTimersByTime(50);

    const raw = localStorage.getItem(`${STORAGE_PREFIX}count`);
    expect(raw).not.toBeNull();
    expect(JSON.parse(raw as string)).toEqual(
      expect.objectContaining({
        key: 'count',
        value: 2,
        tabId: expect.any(String),
        timestamp: expect.any(Number),
      })
    );
    expect(getSyncStatistics().pendingWrites).toBe(0);
  });

  it('notifies subscribers for storage events from other tabs', () => {
    cleanup = syncStateAcrossTabs();

    const listener = vi.fn();
    const unsubscribe = subscribeToSync(listener);

    window.dispatchEvent(
      new StorageEvent('storage', {
        key: `${STORAGE_PREFIX}workspace`,
        newValue: JSON.stringify({
          key: 'ignored',
          value: { stage: 'execute' },
          timestamp: 123,
          tabId: 'other-tab',
        }),
      })
    );

    expect(listener).toHaveBeenCalledWith({
      key: 'workspace',
      value: { stage: 'execute' },
      timestamp: 123,
      tabId: 'other-tab',
    });

    unsubscribe();
  });

  it('ignores storage events emitted by the current tab', () => {
    sessionStorage.setItem(TAB_ID_KEY, 'current-tab');
    cleanup = syncStateAcrossTabs();

    const listener = vi.fn();
    subscribeToSync(listener);

    window.dispatchEvent(
      new StorageEvent('storage', {
        key: `${STORAGE_PREFIX}workspace`,
        newValue: JSON.stringify({
          key: 'workspace',
          value: { stage: 'execute' },
          timestamp: 123,
          tabId: 'current-tab',
        }),
      })
    );

    expect(listener).not.toHaveBeenCalled();
  });

  it('removes listeners on unsubscribe', () => {
    cleanup = syncStateAcrossTabs();

    const listener = vi.fn();
    const unsubscribe = subscribeToSync(listener);
    unsubscribe();

    window.dispatchEvent(
      new StorageEvent('storage', {
        key: `${STORAGE_PREFIX}workspace`,
        newValue: JSON.stringify({
          key: 'workspace',
          value: { stage: 'validate' },
          timestamp: 456,
          tabId: 'other-tab',
        }),
      })
    );

    expect(listener).not.toHaveBeenCalled();
    expect(getSyncStatistics().listenerCount).toBe(0);
  });

  it('returns null when no persisted atom value exists', () => {
    cleanup = syncStateAcrossTabs();

    expect(readAtomFromStorage('missing')).toBeNull();
  });
});
