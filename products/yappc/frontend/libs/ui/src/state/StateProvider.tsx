/**
 * State Provider Component
 *
 * Root provider for global state management.
 * Wraps the application with Jotai Provider and initializes state.
 *
 * @module state/StateProvider
 */

import { Provider, createStore, useSetAtom } from 'jotai';
// import { DevTools } from 'jotai-devtools';
import React, { useEffect, useMemo, useCallback } from 'react';

import { StateManager } from './StateManager';
import {
  syncStateAcrossTabs,
  subscribeToSync,
  type StorageEvent
} from './cross-tab-sync';
// import 'jotai-devtools/styles.css';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface StateProviderProps {
  /**
   * Children components
   */
  children: React.ReactNode;

  /**
   * Initial state values
   */
  initialState?: Record<string, unknown>;

  /**
   * Enable DevTools (default: true in development)
   */
  devTools?: boolean;

  /**
   * DevTools position
   */
  devToolsPosition?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';

  /**
   * Store instance (optional, for advanced use cases)
   */
  store?: ReturnType<typeof createStore>;

  /**
   * Configuration for cross-tab state synchronization
   */
  syncConfig?: {
    enabled?: boolean;
    debug?: boolean;
    excludeKeys?: string[];
    debounceDelay?: number;
  };
}

// ============================================================================
// State Provider Component
// ============================================================================

/**
 * Root provider for global state management
 *
 * @example
 * <StateProvider initialState={{ theme: 'dark' }}>
 *   <App />
 * </StateProvider>
 */
export const StateProvider: React.FC<StateProviderProps> = ({
  children,
  initialState,
  devTools = process.env.NODE_ENV === 'development',
  devToolsPosition = 'bottom-right',
  store: externalStore,
  syncConfig = { enabled: true },
}) => {
  // Create or use provided store
  const store = useMemo(() => {
    return externalStore || createStore();
  }, [externalStore]);

  // Initialize state
  useEffect(() => {
    if (initialState) {
      for (const [key, value] of Object.entries(initialState)) {
        const atom = StateManager.getAtom(key);
        if (atom) {
          // Check if atom is writable before setting
          try {
            store.set(atom as unknown, value);
          } catch (error) {
            if (process.env.NODE_ENV === 'development') {
              console.warn(`[StateProvider] Failed to initialize atom "${key}":`, error);
            }
          }
        }
      }
    }
  }, [initialState, store]);

  // Initialize cross-tab synchronization
  useEffect(() => {
    if (!syncConfig.enabled) return;

    const cleanup = syncStateAcrossTabs({
      debug: syncConfig.debug ?? process.env.NODE_ENV === 'development',
      excludeKeys: [
        'temp-ui-state',
        'devtools-state',
        'session-only',
        ...(syncConfig.excludeKeys ?? [])
      ],
      debounceDelay: syncConfig.debounceDelay ?? 50,
    });

    // Subscribe to sync events from other tabs
    const unsubscribe = subscribeToSync((event: StorageEvent) => {
      const atom = StateManager.getAtom(event.key);
      if (atom) {
        // Only update if atom is writable
        try {
          store.set(atom as unknown, event.value);
        } catch (error) {
          if (syncConfig.debug || process.env.NODE_ENV === 'development') {
            console.warn(`[StateProvider] Failed to sync atom "${event.key}":`, error);
          }
        }
      }
    });

    return () => {
      cleanup();
      unsubscribe();
    };
  }, [store, syncConfig]);

  return (
    <Provider store={store}>
      {/* {devTools && (
        <DevTools
          store={store}
          position={devToolsPosition}
          theme="dark"
        />
      )} */}
      <StateInitializer />
      {children}
    </Provider>
  );
};

// ============================================================================
// State Initializer Component
// ============================================================================

/**
 * Internal component to handle state initialization
 */
const StateInitializer: React.FC = () => {
  // This component can be used to initialize atoms on mount
  // Currently a placeholder for future initialization logic

  useEffect(() => {
    // Log state manager statistics in development
    if (process.env.NODE_ENV === 'development') {
      const stats = StateManager.getStatistics();
      console.log('[StateProvider] Initialized with atoms:', stats);
    }
  }, []);

  return null;
};

// ============================================================================
// Context Hook for Store Access
// ============================================================================

/**
 * Hook to access the Jotai store directly
 * (Advanced use case - most components should use useGlobalState instead)
 */
export function useStore() {
  // Jotai doesn't expose a useStore hook by default
  // This is a placeholder for advanced use cases
  throw new Error(
    'Direct store access is not recommended. Use useGlobalState hooks instead.'
  );
}
