import { useState, useEffect, useCallback } from 'react';

export type SetValue<T> = (value: T | ((val: T) => T)) => void;

/**
 * A custom hook that persists state to localStorage and keeps it in sync.
 * 
 * @param key - The key under which to store the value in localStorage.
 * @param initialValue - The initial value to use if no value exists in localStorage.
 * @returns A stateful value and a function to update it.
 * 
 * @example
 * const [name, setName] = useLocalStorage('username', 'Guest');
 * 
 * return (
 *   <div>
 *     <input
 *       type="text"
 *       value={name}
 *       onChange={(e) => setName(e.target.value)}
 *       placeholder="Enter your name"
 *     />
 *   </div>
 * );
 */
export function useLocalStorage<T>(
  key: string,
  initialValue: T
): [T, SetValue<T>] {
  // Get from local storage then parse stored json or return initialValue
  const readValue = useCallback((): T => {
    // Prevent build error "window is undefined" but keep working
    if (typeof window === 'undefined') {
      return initialValue;
    }

    try {
      const item = window.localStorage.getItem(key);
      return item ? JSON.parse(item) : initialValue;
    } catch (error) {
      console.warn(`Error reading localStorage key "${key}":`, error);
      return initialValue;
    }
  }, [initialValue, key]);

  // State to store our value
  const [storedValue, setStoredValue] = useState<T>(readValue);

  // Return a wrapped version of useState's setter function that persists the new value to localStorage
  const setValue: SetValue<T> = useCallback(
    (value) => {
      try {
        // Allow value to be a function so we have the same API as useState
        const valueToStore = value instanceof Function ? value(storedValue) : value;
        
        // Save to state
        setStoredValue(valueToStore);
        
        // Save to local storage
        if (typeof window !== 'undefined') {
          window.localStorage.setItem(key, JSON.stringify(valueToStore));
        }
      } catch (error) {
        console.warn(`Error setting localStorage key "${key}":`, error);
      }
    },
    [key, storedValue]
  );

  // Sync changes across tabs/windows
  useEffect(() => {
    const handleStorageChange = (event: StorageEvent) => {
      if (event.key === key && event.newValue !== JSON.stringify(storedValue)) {
        setStoredValue(readValue());
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => {
      window.removeEventListener('storage', handleStorageChange);
    };
  }, [key, readValue, storedValue]);

  // Read the latest value from localStorage on mount
  useEffect(() => {
    setStoredValue(readValue());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return [storedValue, setValue];
}

/**
 * A set of utility functions for working with localStorage.
 */
export const localStorageUtils = {
  /**
   * Retrieves an item from localStorage by key.
   * @param key - The key of the item to retrieve.
   * @param defaultValue - The default value to return if the item doesn't exist.
   * @returns The stored value or the default value.
   */
  getItem<T>(key: string, defaultValue: T): T {
    if (typeof window === 'undefined') {
      return defaultValue;
    }
    try {
      const item = window.localStorage.getItem(key);
      return item ? JSON.parse(item) : defaultValue;
    } catch (error) {
      console.warn(`Error reading localStorage key "${key}":`, error);
      return defaultValue;
    }
  },

  /**
   * Sets an item in localStorage.
   * @param key - The key under which to store the value.
   * @param value - The value to store.
   */
  setItem<T>(key: string, value: T): void {
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.setItem(key, JSON.stringify(value));
      } catch (error) {
        console.warn(`Error setting localStorage key "${key}":`, error);
      }
    }
  },

  /**
   * Removes an item from localStorage.
   * @param key - The key of the item to remove.
   */
  removeItem(key: string): void {
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.removeItem(key);
      } catch (error) {
        console.warn(`Error removing localStorage key "${key}":`, error);
      }
    }
  },

  /**
   * Clears all items from localStorage.
   */
  clear(): void {
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.clear();
      } catch (error) {
        console.warn('Error clearing localStorage:', error);
      }
    }
  },
};
