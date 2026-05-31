/**
 * useRouteEntryState Hook
 *
 * Standardized pattern for handling route entry state (query params, hash, etc.)
 * with type-safe parsing and validation via Zod schemas.
 *
 * @doc.type hook
 * @doc.purpose Type-safe route entry state parsing and validation
 * @doc.layer shared
 * @doc.pattern State Management
 * @example
 * ```tsx
 * const schema = z.object({ tab: z.enum(['overview', 'details']) });
 * const { state, setState, isValid, errors } = useRouteEntryState(schema, { tab: 'overview' });
 * ```
 */

import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router";
import type { z } from "zod";

interface RouteEntryStateResult<T> {
  /** Parsed and validated state */
  state: T;
  /** Update state and URL search params */
  setState: (newState: Partial<T>) => void;
  /** Whether current URL state passes schema validation */
  isValid: boolean;
  /** Validation errors if any */
  errors: z.ZodError<T> | null;
  /** Reset to defaults */
  reset: () => void;
}

export function useRouteEntryState<T extends z.ZodObject<z.ZodRawShape>>(
  schema: T,
  defaults: z.infer<T>,
  options?: {
    /** URL param key prefix to avoid collisions */
    prefix?: string;
  },
): RouteEntryStateResult<z.infer<T>> {
  const [searchParams, setSearchParams] = useSearchParams();
  const prefix = options?.prefix ?? "";

  const prefixedKey = useCallback(
    (key: string) => (prefix ? `${prefix}.${key}` : key),
    [prefix],
  );

  const rawState = useMemo(() => {
    const entries: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      if (!prefix || key.startsWith(`${prefix}.`)) {
        const cleanKey = prefix ? key.replace(`${prefix}.`, "") : key;
        entries[cleanKey] = value;
      }
    });
    return entries;
  }, [searchParams, prefix]);

  const parsed = useMemo(() => {
    try {
      const merged = { ...defaults, ...rawState };
      const result = schema.safeParse(merged);
      if (result.success) {
        return {
          state: result.data as z.infer<T>,
          isValid: true,
          errors: null,
        };
      }
      return { state: defaults, isValid: false, errors: result.error };
    } catch {
      return { state: defaults, isValid: false, errors: null };
    }
  }, [rawState, schema, defaults]);

  const setState = useCallback(
    (newState: Partial<z.infer<T>>) => {
      const next = new URLSearchParams(searchParams);
      Object.entries(newState).forEach(([key, value]) => {
        const paramKey = prefixedKey(key);
        if (value === undefined || value === null || value === "") {
          next.delete(paramKey);
        } else {
          next.set(paramKey, String(value));
        }
      });
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams, prefixedKey],
  );

  const reset = useCallback(() => {
    const next = new URLSearchParams(searchParams);
    Object.keys(defaults).forEach((key) => {
      next.delete(prefixedKey(key));
    });
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams, defaults, prefixedKey]);

  return {
    state: parsed.state,
    setState,
    isValid: parsed.isValid,
    errors: parsed.errors,
    reset,
  };
}

export default useRouteEntryState;
