/**
 * Global State Hooks
 *
 * React hooks for accessing and managing global state through StateManager.
 * Provides type-safe access to atoms with various utility patterns.
 *
 * Features:
 * - Read/write state access
 * - Read-only value access
 * - Setter-only access (no subscriptions)
 * - State reset functionality
 * - Toggle, counter, array, and object utilities
 * - Batch update support
 * - Debug utilities
 *
 * @module state/useGlobalState
 * @doc.type module
 * @doc.purpose React hooks for global state management
 * @doc.layer product
 * @doc.pattern Hook, Factory
 *
 * @example
 * ```typescript
 * // Basic state access
 * const [count, setCount] = useGlobalState('counter');
 *
 * // Read-only access
 * const count = useGlobalStateValue('counter');
 *
 * // Setter only
 * const setCount = useSetGlobalState('counter');
 *
 * // Toggle boolean
 * const [isOpen, toggle] = useToggleGlobalState('modalOpen');
 *
 * // Counter operations
 * const [count, increment, decrement] = useCounterGlobalState('counter');
 *
 * // Array operations
 * const [items, { push, remove, clear }] = useArrayGlobalState('items');
 *
 * // Object operations
 * const [user, { update, set, reset }] = useObjectGlobalState('user');
 * ```
 */

import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { RESET } from 'jotai/utils';
import { useCallback } from 'react';

import { StateManager } from './StateManager';

import type { AtomKey } from './StateManager';
import type { WritableAtom } from 'jotai';

// ============================================================================
// Main Global State Hook
// ============================================================================

/**
 * Hook to use global state by key
 *
 * @example
 * const [count, setCount] = useGlobalState('counter')
 * setCount(count + 1)
 */
export function useGlobalState<T>(key: AtomKey): [T, (value: T | ((prev: T) => T)) => void] {
  const atom = StateManager.getAtom<T>(key);

  if (!atom) {
    throw new Error(
      `[useGlobalState] Atom with key "${key}" not found. Make sure to create it first using StateManager.createAtom()`
    );
  }

  return useAtom(atom as unknown as WritableAtom<T, [T | ((prev: T) => T)], void>);
}

/**
 * Hook to read global state value (read-only)
 *
 * @example
 * const count = useGlobalStateValue('counter')
 */
export function useGlobalStateValue<T>(key: AtomKey): T {
  const atom = StateManager.getAtom<T>(key);

  if (!atom) {
    throw new Error(
      `[useGlobalStateValue] Atom with key "${key}" not found`
    );
  }

  return useAtomValue(atom);
}

/**
 * Hook to get setter function only (doesn't subscribe to changes)
 *
 * @example
 * const setCount = useSetGlobalState('counter')
 * setCount(10)
 */
export function useSetGlobalState<T>(key: AtomKey): (value: T | ((prev: T) => T)) => void {
  const atom = StateManager.getAtom<T>(key);

  if (!atom) {
    throw new Error(
      `[useSetGlobalState] Atom with key "${key}" not found`
    );
  }

  return useSetAtom(atom as unknown as WritableAtom<T, [T | ((prev: T) => T)], void>);
}

/**
 * Hook to reset global state to default value
 *
 * @example
 * const resetCount = useResetGlobalState('counter')
 * resetCount()
 */
export function useResetGlobalState(key: AtomKey): () => void {
  const atom = StateManager.getAtom(key);

  if (!atom) {
    throw new Error(
      `[useResetGlobalState] Atom with key "${key}" not found`
    );
  }

  const setAtom = useSetAtom(atom as unknown as WritableAtom<unknown, [unknown], void>);

  return useCallback(() => {
    setAtom(RESET);
  }, [setAtom]);
}

// ============================================================================
// Utility Hooks
// ============================================================================

/**
 * Hook to toggle boolean state
 *
 * @example
 * const [isOpen, toggle] = useToggleGlobalState('modalOpen')
 * toggle() // flips between true/false
 */
export function useToggleGlobalState(key: AtomKey): [boolean, () => void] {
  const [value, setValue] = useGlobalState<boolean>(key);

  const toggle = useCallback(() => {
    setValue((prev) => !prev);
  }, [setValue]);

  return [value, toggle];
}

/**
 * Hook to increment/decrement number state
 *
 * @example
 * const [count, increment, decrement] = useCounterGlobalState('counter')
 * increment(5) // add 5
 * decrement(2) // subtract 2
 */
export function useCounterGlobalState(
  key: AtomKey
): [number, (amount?: number) => void, (amount?: number) => void] {
  const [value, setValue] = useGlobalState<number>(key);

  const increment = useCallback(
    (amount: number = 1) => {
      setValue((prev) => prev + amount);
    },
    [setValue]
  );

  const decrement = useCallback(
    (amount: number = 1) => {
      setValue((prev) => prev - amount);
    },
    [setValue]
  );

  return [value, increment, decrement];
}

/**
 * Hook to manage array state
 *
 * @example
 * const [items, { push, remove, clear }] = useArrayGlobalState('items')
 * push({ id: 1, name: 'Item 1' })
 * remove(0) // remove by index
 */
export function useArrayGlobalState<T>(key: AtomKey): [
  T[],
  {
    push: (item: T) => void;
    remove: (index: number) => void;
    update: (index: number, item: T) => void;
    clear: () => void;
    filter: (predicate: (item: T) => boolean) => void;
  }
] {
  const [value, setValue] = useGlobalState<T[]>(key);

  const push = useCallback(
    (item: T) => {
      setValue((prev) => [...prev, item]);
    },
    [setValue]
  );

  const remove = useCallback(
    (index: number) => {
      setValue((prev) => prev.filter((_, i) => i !== index));
    },
    [setValue]
  );

  const update = useCallback(
    (index: number, item: T) => {
      setValue((prev) => prev.map((v, i) => (i === index ? item : v)));
    },
    [setValue]
  );

  const clear = useCallback(() => {
    setValue([]);
  }, [setValue]);

  const filter = useCallback(
    (predicate: (item: T) => boolean) => {
      setValue((prev) => prev.filter(predicate));
    },
    [setValue]
  );

  return [value, { push, remove, update, clear, filter }];
}

/**
 * Hook to manage object state
 *
 * @example
 * const [user, { update, reset }] = useObjectGlobalState('user')
 * update({ name: 'John' }) // merge update
 */
export function useObjectGlobalState<T extends Record<string, unknown>>(key: AtomKey): [
  T,
  {
    update: (partial: Partial<T>) => void;
    set: (newValue: T) => void;
    reset: () => void;
  }
] {
  const [value, setValue] = useGlobalState<T>(key);
  const resetFn = useResetGlobalState(key);

  const update = useCallback(
    (partial: Partial<T>) => {
      setValue((prev) => ({ ...prev, ...partial }));
    },
    [setValue]
  );

  const set = useCallback(
    (newValue: T) => {
      setValue(newValue);
    },
    [setValue]
  );

  return [value, { update, set, reset: resetFn }];
}

// ============================================================================
// Batch Update Hook
// ============================================================================

/**
 * Hook to batch multiple state updates
 *
 * @example
 * const batchUpdate = useBatchGlobalStateUpdate()
 * batchUpdate(() => {
 *   setCount(10)
 *   setName('John')
 *   setEmail('john@example.com')
 * })
 */
export function useBatchGlobalStateUpdate(): (fn: () => void) => void {
  return useCallback((fn: () => void) => {
    // React 18 automatic batching handles this
    // For React 17, would need to use unstable_batchedUpdates
    fn();
  }, []);
}

// ============================================================================
// Debug Hooks
// ============================================================================

/**
 * Hook to get all global state keys
 */
export function useGlobalStateKeys(): string[] {
  return StateManager.getAllKeys();
}

/**
 * Hook to get state manager statistics
 */
export function useGlobalStateStatistics() {
  return StateManager.getStatistics();
}
