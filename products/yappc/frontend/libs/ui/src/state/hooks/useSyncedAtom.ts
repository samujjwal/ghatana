/**
 * Synced Atom Hook
 *
 * Hook that wraps Jotai atoms with automatic cross-tab synchronization.
 * Writes atom changes to storage for other tabs to consume.
 *
 * @module state/hooks/useSyncedAtom
 * @doc.type hook
 * @doc.purpose Atom hook with cross-tab sync
 * @doc.layer platform
 * @doc.pattern Hook
 */

import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import type { Atom, WritableAtom } from 'jotai';
import { useCallback, useEffect } from 'react';
import { writeAtomToStorage } from '../cross-tab-sync';

// ============================================================================
// Synced Atom Hook
// ============================================================================

/**
 * Use atom with automatic cross-tab synchronization
 *
 * Works like `useAtom` but writes changes to storage for cross-tab sync.
 *
 * @param atom - Jotai atom
 * @param key - Atom key for storage
 * @returns Tuple of [value, setter]
 *
 * @doc.type hook
 * @doc.purpose Use atom with cross-tab sync
 * @doc.layer platform
 *
 * @example
 * ```tsx
 * function Counter() {
 *   const [count, setCount] = useSyncedAtom(countAtom, 'counter');
 *
 *   return (
 *     <button onClick={() => setCount(count + 1)}>
 *       Count: {count}
 *     </button>
 *   );
 * }
 * ```
 */
export function useSyncedAtom<T>(
  atom: WritableAtom<T, [T], void>,
  key: string
): [T, (update: T | ((prev: T) => T)) => void] {
  const [value, setValue] = useAtom(atom);

  // Wrapped setter that also writes to storage
  const setSyncedValue = useCallback(
    (update: T | ((prev: T) => T)) => {
      // Calculate new value first
      const newValue =
        typeof update === 'function'
          ? (update as (prev: T) => T)(value)
          : update;

      // Update local atom with the computed value
      setValue(newValue);

      // Write to storage for other tabs
      writeAtomToStorage(key, newValue);
    },
    [setValue, key, value]
  );

  return [value, setSyncedValue];
}

/**
 * Use atom value with cross-tab sync (read-only)
 *
 * Like `useAtomValue` but optimized for synced atoms.
 *
 * @param atom - Jotai atom
 * @returns Current atom value
 *
 * @doc.type hook
 * @doc.purpose Read synced atom value
 * @doc.layer platform
 *
 * @example
 * ```tsx
 * function Display() {
 *   const count = useSyncedAtomValue(countAtom);
 *   return <div>Count: {count}</div>;
 * }
 * ```
 */
export function useSyncedAtomValue<T>(atom: Atom<T>): T {
  return useAtomValue(atom);
}

/**
 * Use atom setter with cross-tab sync (write-only)
 *
 * Like `useSetAtom` but writes to storage for cross-tab sync.
 *
 * @param atom - Jotai atom
 * @param key - Atom key for storage
 * @returns Setter function
 *
 * @doc.type hook
 * @doc.purpose Set synced atom value
 * @doc.layer platform
 *
 * @example
 * ```tsx
 * function IncrementButton() {
 *   const setCount = useSyncedSetAtom(countAtom, 'counter');
 *
 *   return (
 *     <button onClick={() => setCount((prev) => prev + 1)}>
 *       Increment
 *     </button>
 *   );
 * }
 * ```
 */
export function useSyncedSetAtom<T>(
  atom: WritableAtom<T, [T], void>,
  key: string
): (update: T | ((prev: T) => T)) => void {
  const setValue = useSetAtom(atom);
  const value = useAtomValue(atom);

  return useCallback(
    (update: T | ((prev: T) => T)) => {
      // Calculate new value first
      const newValue =
        typeof update === 'function'
          ? (update as (prev: T) => T)(value)
          : update;

      // Update local atom with the computed value
      setValue(newValue);

      // Write to storage for other tabs
      writeAtomToStorage(key, newValue);
    },
    [setValue, key, value]
  );
}

/**
 * Use effect to sync atom on changes
 *
 * Automatically syncs atom to storage whenever it changes.
 * Useful for atoms that should always be synced.
 *
 * @param atom - Jotai atom
 * @param key - Atom key for storage
 *
 * @doc.type hook
 * @doc.purpose Auto-sync atom on changes
 * @doc.layer platform
 *
 * @example
 * ```tsx
 * function App() {
 *   // Auto-sync theme atom whenever it changes
 *   useAutoSyncAtom(themeAtom, 'theme');
 *
 *   return <div>...</div>;
 * }
 * ```
 */
export function useAutoSyncAtom<T>(atom: Atom<T>, key: string): void {
  const value = useAtomValue(atom);

  useEffect(() => {
    // Write to storage whenever value changes
    writeAtomToStorage(key, value);
  }, [key, value]);
}
