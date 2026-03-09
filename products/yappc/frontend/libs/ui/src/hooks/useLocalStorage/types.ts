/**
 * Type definitions for useLocalStorage hook
 */

/**
 * Return type for useLocalStorage hook.
 *
 * @template T - Type of the stored value
 */
export type UseLocalStorageReturn<T> = [
  /** Current stored value */
  value: T,
  /** Function to update stored value (supports updater function like useState) */
  setValue: (value: T | ((val: T) => T)) => void,
  /** Function to remove the value from localStorage */
  removeValue: () => void,
];

/**
 * Options for configuring useLocalStorage hook
 */
export interface UseLocalStorageOptions {
  /** Whether to sync with other browser tabs (localStorage changes). Default: true */
  syncData?: boolean;
}
