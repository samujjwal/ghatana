import * as React from 'react';

/**
 * Options for useControllableState hook
 */
export interface UseControllableStateOptions<T> {
  /**
   * Controlled value. When provided, component becomes controlled.
   */
  value?: T;

  /**
   * Default value used for uncontrolled usage.
   */
  defaultValue?: T | (() => T);

  /**
   * Callback fired whenever the value changes.
   */
  onChange?: (next: T) => void;
}

/**
 * useControllableState
 *
 * Provides a convenient way to support both controlled and uncontrolled usage
 * in components (mirrors the pattern used by Radix/Base UI).
 *
 * @example
 * ```tsx
 * const [isOpen, setOpen] = useControllableState({
 *   value: controlledOpen,
 *   defaultValue: false,
 *   onChange: handleOpenChange,
 * });
 * ```
 */
export function useControllableState<T>({
  value,
  defaultValue,
  onChange,
}: UseControllableStateOptions<T>): [T, (next: T | ((prev: T) => T)) => void] {
  const isControlled = value !== undefined;
  const initialValueRef = React.useRef(value ?? (typeof defaultValue === 'function'
      ? (defaultValue as () => T)()
      : defaultValue));

  const [internalValue, setInternalValue] = React.useState<T | undefined>(
    initialValueRef.current
  );

  const lastValueRef = React.useRef<T | undefined>(value ?? internalValue);

  const setValue = React.useCallback(
    (next: T | ((prev: T) => T)) => {
      const current = isControlled ? value : lastValueRef.current;
      const resolved =
        typeof next === 'function' ? (next as (prev: T) => T)(current as T) : next;

      if (!isControlled) {
        setInternalValue(resolved);
      }

      if (resolved !== lastValueRef.current) {
        lastValueRef.current = resolved;
        onChange?.(resolved);
      }
    },
    [isControlled, onChange, value]
  );

  return [isControlled ? (value as T) : (internalValue as T), setValue];
}
