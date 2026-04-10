/**
 * @ghatana/state — React Hooks
 *
 * Thin typed wrappers around Jotai's useAtom family for common
 * @ghatana/state atom shapes.
 *
 * This file uses the "react" peer dependency — only import from here
 * in React contexts.
 */

import { useAtom, useAtomValue, useSetAtom } from "jotai";
import type { Atom, WritableAtom } from "jotai";

import type { AsyncState } from "./types";
import { AsyncState as AS } from "./types";

// ---------------------------------------------------------------------------
// Generic typed wrappers
// ---------------------------------------------------------------------------

/**
 * Returns `[value, setter]` for a writable atom.
 *
 * @example
 * ```tsx
 * const [count, setCount] = useStateAtom(counterAtom);
 * ```
 */
export function useStateAtom<T>(
  a: WritableAtom<T, [T | ((prev: T) => T)], void>
): [T, (next: T | ((prev: T) => T)) => void] {
  return useAtom(a);
}

/**
 * Returns only the value of an atom (read-only).
 *
 * @example
 * ```tsx
 * const theme = useStateValue(themeAtom);
 * ```
 */
export function useStateValue<T>(a: Atom<T>): T {
  return useAtomValue(a);
}

/**
 * Returns only the setter for a writable atom (no re-render on value change).
 *
 * @example
 * ```tsx
 * const setTheme = useStateSetter(themeAtom);
 * setTheme('dark');
 * ```
 */
export function useStateSetter<T>(
  a: WritableAtom<T, [T | ((prev: T) => T)], void>
): (next: T | ((prev: T) => T)) => void {
  return useSetAtom(a) as (next: T | ((prev: T) => T)) => void;
}

// ---------------------------------------------------------------------------
// AsyncState hook
// ---------------------------------------------------------------------------

/**
 * Returns `[state, helpers]` for an `AsyncState<T>` atom.
 *
 * Helpers:
 * - `setLoading()` — transitions to loading
 * - `setSuccess(data)` — transitions to success
 * - `setError(error)` — transitions to error
 * - `reset()` — resets back to idle
 *
 * @example
 * ```tsx
 * const [users, { setLoading, setSuccess, setError }] = useAsyncStateAtom(usersAtom);
 * ```
 */
export function useAsyncStateAtom<T>(
  a: WritableAtom<AsyncState<T>, [AsyncState<T>], void>
): [
  AsyncState<T>,
  {
    setLoading: () => void;
    setSuccess: (data: T) => void;
    setError: (error: Error) => void;
    reset: () => void;
  },
] {
  const [state, setState] = useAtom(a);

  return [
    state,
    {
      setLoading: () => setState(AS.loading()),
      setSuccess: (data) => setState(AS.success(data)),
      setError: (error) => setState(AS.error(error)),
      reset: () => setState(AS.idle()),
    },
  ];
}

// ---------------------------------------------------------------------------
// Boolean toggle hook
// ---------------------------------------------------------------------------

/**
 * Returns `[value, toggle, setFalse, setTrue]` for a boolean atom.
 *
 * @example
 * ```tsx
 * const [isOpen, toggleOpen] = useBooleanAtom(modalOpenAtom);
 * ```
 */
export function useBooleanAtom(
  a: WritableAtom<boolean, [boolean | ((prev: boolean) => boolean)], void>
): [boolean, () => void, () => void, () => void] {
  const [value, setValue] = useAtom(a);
  return [
    value,
    () => setValue((v) => !v),
    () => setValue(false),
    () => setValue(true),
  ];
}
