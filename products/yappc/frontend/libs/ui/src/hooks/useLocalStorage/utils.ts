/**
 * Utility class for localStorage operations.
 *
 * Provides helper methods for safely reading, writing, and removing values
 * from localStorage with error handling.
 */
export class LocalStorageUtils {
  /**
   * Check if localStorage is available in the current environment.
   *
   * @returns true if localStorage is available, false otherwise
   */
  static isAvailable(): boolean {
    if (typeof window === 'undefined') return false;
    try {
      const test = '__test__';
      window.localStorage.setItem(test, test);
      window.localStorage.removeItem(test);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Get a value from localStorage.
   *
   * @template T - Type of the stored value
   * @param key - localStorage key
   * @param fallback - Fallback value if key doesn't exist or error occurs
   * @returns Parsed value from localStorage, or fallback value
   */
  static getItem<T>(key: string, fallback: T): T {
    if (typeof window === 'undefined') {
      return fallback;
    }

    try {
      const item = window.localStorage.getItem(key);
      return item ? (JSON.parse(item) as T) : fallback;
    } catch (error) {
      console.warn(`Error reading localStorage key "${key}":`, error);
      return fallback;
    }
  }

  /**
   * Set a value in localStorage.
   *
   * @template T - Type of value to store
   * @param key - localStorage key
   * @param value - Value to store (will be JSON stringified)
   * @returns true if successful, false otherwise
   */
  static setItem<T>(key: string, value: T): boolean {
    if (typeof window === 'undefined') {
      return false;
    }

    try {
      window.localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (error) {
      console.warn(`Error setting localStorage key "${key}":`, error);
      return false;
    }
  }

  /**
   * Remove a value from localStorage.
   *
   * @param key - localStorage key to remove
   * @returns true if successful, false otherwise
   */
  static removeItem(key: string): boolean {
    if (typeof window === 'undefined') {
      return false;
    }

    try {
      window.localStorage.removeItem(key);
      return true;
    } catch (error) {
      console.warn(`Error removing localStorage key "${key}":`, error);
      return false;
    }
  }
}
