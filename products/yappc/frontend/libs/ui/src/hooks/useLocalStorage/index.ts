import { useState, useEffect, useCallback } from 'react';

import { LocalStorageUtils } from './utils';

import type { UseLocalStorageReturn, UseLocalStorageOptions } from './types';

/**
 * React hook for managing state synchronized with localStorage.
 *
 * Automatically persists state to localStorage and synchronizes across
 * browser tabs. Handles SSR safety and error cases gracefully.
 *
 * Features:
 * - Automatic localStorage persistence
 * - Server-side rendering (SSR) safe
 * - Supports updater functions like useState
 * - Error handling with console warnings
 * - Remove value capability
 *
 * @template T - Type of the stored value
 * @param key - The localStorage key
 * @param initialValue - Initial value if key doesn't exist in localStorage
 * @param options - Optional configuration
 * @returns Tuple of [value, setValue, removeValue]
 *
 * @example
 * ```tsx
 * // Basic usage
 * const [user, setUser, removeUser] = useLocalStorage('user', null);
 *
 * // With updater function
 * const [count, setCount] = useLocalStorage('counter', 0);
 * setCount((prev) => prev + 1);
 *
 * // Clean up
 * removeUser();
 * ```
 */
export function useLocalStorage<T>(
  key: string,
  initialValue: T,
  options: UseLocalStorageOptions = {}
): UseLocalStorageReturn<T> {
  const { syncData = true } = options;

  /**
   * Read value from localStorage with error handling.
   */
  const readValue = useCallback((): T => {
    return LocalStorageUtils.getItem<T>(key, initialValue);
  }, [initialValue, key]);

  const [storedValue, setStoredValue] = useState<T>(readValue);

  /**
   * Update value in state and localStorage.
   *
   * Supports updater functions like React's useState for computing
   * the new value based on the previous value.
   */
  const setValue = useCallback(
    (value: T | ((val: T) => T)) => {
      try {
        // Handle updater function
        const valueToStore =
          value instanceof Function ? value(storedValue) : value;

        // Update state
        setStoredValue(valueToStore);

        // Persist to localStorage
        LocalStorageUtils.setItem(key, valueToStore);
      } catch (error) {
        console.warn(`Error setting localStorage key "${key}":`, error);
      }
    },
    [key, storedValue]
  );

  /**
   * Remove value from both state and localStorage.
   */
  const removeValue = useCallback(() => {
    try {
      LocalStorageUtils.removeItem(key);
      setStoredValue(initialValue);
    } catch (error) {
      console.warn(`Error removing localStorage key "${key}":`, error);
    }
  }, [key, initialValue]);

  // Sync state with localStorage on mount and when key changes
  useEffect(() => {
    setStoredValue(readValue());
  }, [readValue]);

  // Listen for changes from other tabs (if enabled)
  useEffect(() => {
    if (!syncData) return;

    const handleStorageChange = (event: StorageEvent) => {
      if (event.key !== key) return;
      if (event.newValue === null) {
        setStoredValue(initialValue);
      } else {
        try {
          setStoredValue(JSON.parse(event.newValue) as T);
        } catch (error) {
          console.warn(
            `Error parsing localStorage change for key "${key}":`,
            error
          );
        }
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, [key, initialValue, syncData]);

  return [storedValue, setValue, removeValue];
}

export default useLocalStorage;
