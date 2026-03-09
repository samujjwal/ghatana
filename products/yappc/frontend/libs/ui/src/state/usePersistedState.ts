/**
 * Persisted State Hook
 *
 * React hook for state that persists across page reloads and syncs across tabs.
 *
 * @module state/usePersistedState
 */

import { useState, useEffect, useCallback, useRef } from 'react';

import { StatePersistence } from './StatePersistence';
import { getStateSync } from './StateSync';

import type { StorageType } from './StatePersistence';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface UsePersistedStateOptions {
  /**
   * Storage type
   */
  storage?: StorageType;

  /**
   * Enable cross-tab synchronization
   */
  sync?: boolean;

  /**
   * Serializer function
   */
  serialize?: (value: unknown) => string;

  /**
   * Deserializer function
   */
  deserialize?: (value: string) => any;

  /**
   * Data version for migration
   */
  version?: number;

  /**
   * Migration function
   */
  migrate?: (oldData: unknown, oldVersion: number) => any;
}

// ============================================================================
// usePersistedState Hook
// ============================================================================

/**
 * Hook for persisted state with optional cross-tab sync
 *
 * @example
 * const [theme, setTheme] = usePersistedState('theme', 'light', {
 *   storage: 'local',
 *   sync: true,
 * })
 */
export function usePersistedState<T>(
  key: string,
  defaultValue: T,
  options: UsePersistedStateOptions = {}
): [T, (value: T | ((prev: T) => T)) => void, () => void] {
  const {
    storage = 'local',
    sync = false,
    serialize = JSON.stringify,
    deserialize = JSON.parse,
    version,
    migrate,
  } = options;

  const [state, setState] = useState<T>(defaultValue);
  const [isLoaded, setIsLoaded] = useState(false);
  const syncRef = useRef(sync ? getStateSync() : null);

  // Load initial state from storage
  useEffect(() => {
    const loadState = async () => {
      try {
        const loaded = await StatePersistence.load<T>({
          key,
          storage,
          serialize,
          deserialize,
          version,
          migrate,
        });

        if (loaded !== null) {
          setState(loaded);
        }
      } catch (error) {
        console.error('[usePersistedState] Error loading state:', error);
      } finally {
        setIsLoaded(true);
      }
    };

    loadState();
  }, [key, storage, serialize, deserialize, version, migrate]);

  // Save state to storage whenever it changes
  useEffect(() => {
    if (!isLoaded) return;

    const saveState = async () => {
      try {
        await StatePersistence.save(
          {
            key,
            storage,
            serialize,
            deserialize,
            version,
          },
          state
        );

        // Broadcast update if sync is enabled
        if (sync && syncRef.current) {
          syncRef.current.broadcastUpdate(key, state);
        }
      } catch (error) {
        console.error('[usePersistedState] Error saving state:', error);
      }
    };

    saveState();
  }, [state, key, storage, serialize, deserialize, version, sync, isLoaded]);

  // Subscribe to sync updates
  useEffect(() => {
    if (!sync || !syncRef.current) return;

    const unsubscribe = syncRef.current.subscribe(key, (newValue) => {
      setState(newValue);
    });

    return unsubscribe;
  }, [key, sync]);

  // Custom setState that handles both value and function updates
  const setPersistedState = useCallback(
    (valueOrUpdater: T | ((prev: T) => T)) => {
      if (typeof valueOrUpdater === 'function') {
        setState((prev) => (valueOrUpdater as (prev: T) => T)(prev));
      } else {
        setState(valueOrUpdater);
      }
    },
    []
  );

  // Reset to default value
  const reset = useCallback(async () => {
    setState(defaultValue);
    try {
      await StatePersistence.remove({ key, storage });
    } catch (error) {
      console.error('[usePersistedState] Error resetting state:', error);
    }
  }, [key, storage, defaultValue]);

  return [state, setPersistedState, reset];
}

// ============================================================================
// usePersistedObject Hook
// ============================================================================

/**
 * Hook for persisted object state with partial updates
 */
export function usePersistedObject<T extends Record<string, unknown>>(
  key: string,
  defaultValue: T,
  options: UsePersistedStateOptions = {}
): [
  T,
  {
    update: (partial: Partial<T>) => void;
    set: (value: T) => void;
    reset: () => void;
  }
] {
  const [state, setState, reset] = usePersistedState(key, defaultValue, options);

  const update = useCallback(
    (partial: Partial<T>) => {
      setState((prev) => ({ ...prev, ...partial }));
    },
    [setState]
  );

  const set = useCallback(
    (value: T) => {
      setState(value);
    },
    [setState]
  );

  return [state, { update, set, reset }];
}

// ============================================================================
// usePersistedArray Hook
// ============================================================================

/**
 * Hook for persisted array state with array operations
 */
export function usePersistedArray<T>(
  key: string,
  defaultValue: T[] = [],
  options: UsePersistedStateOptions = {}
): [
  T[],
  {
    push: (item: T) => void;
    remove: (index: number) => void;
    update: (index: number, item: T) => void;
    clear: () => void;
    set: (items: T[]) => void;
  }
] {
  const [state, setState, reset] = usePersistedState(key, defaultValue, options);

  const push = useCallback(
    (item: T) => {
      setState((prev) => [...prev, item]);
    },
    [setState]
  );

  const remove = useCallback(
    (index: number) => {
      setState((prev) => prev.filter((_, i) => i !== index));
    },
    [setState]
  );

  const update = useCallback(
    (index: number, item: T) => {
      setState((prev) => prev.map((v, i) => (i === index ? item : v)));
    },
    [setState]
  );

  const clear = useCallback(() => {
    setState([]);
  }, [setState]);

  const set = useCallback(
    (items: T[]) => {
      setState(items);
    },
    [setState]
  );

  return [state, { push, remove, update, clear, set }];
}
