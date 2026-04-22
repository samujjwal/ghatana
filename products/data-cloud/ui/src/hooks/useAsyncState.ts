/**
 * useAsyncState Hook
 *
 * Standardized async state and error handling patterns for data fetching.
 * Provides discriminated union types for loading, success, error, and empty states.
 *
 * @doc.type hook
 * @doc.purpose Standardized async state management with error classification
 * @doc.layer shared
 * @doc.pattern State Management
 * @example
 * ```tsx
 * const state = useAsyncState({
 *   data: collections,
 *   isLoading,
 *   error,
 *   isEmpty: collections?.length === 0
 * });
 * ```
 */

import { useMemo } from 'react';

export type AsyncState<T> =
  | { status: 'loading'; data: null; error: null }
  | { status: 'empty'; data: null; error: null }
  | { status: 'error'; data: null; error: AsyncError }
  | { status: 'success'; data: T; error: null };

export type AsyncErrorCategory = 'network' | 'validation' | 'auth' | 'server' | 'unknown';

export interface AsyncError {
  message: string;
  category: AsyncErrorCategory;
  detail?: string;
  retryable: boolean;
}

function classifyError(error: unknown): AsyncError {
  if (error instanceof Error) {
    const message = error.message.toLowerCase();

    if (message.includes('network') || message.includes('fetch') || message.includes('connection')) {
      return {
        message: error.message,
        category: 'network',
        detail: 'Check your network connection and try again.',
        retryable: true,
      };
    }

    if (message.includes('unauthorized') || message.includes('forbidden') || message.includes('auth')) {
      return {
        message: error.message,
        category: 'auth',
        detail: 'You may not have permission to access this resource.',
        retryable: false,
      };
    }

    if (message.includes('validation') || message.includes('invalid') || message.includes('required')) {
      return {
        message: error.message,
        category: 'validation',
        detail: 'Please check your input and try again.',
        retryable: true,
      };
    }

    if (message.includes('internal') || message.includes('server')) {
      return {
        message: error.message,
        category: 'server',
        detail: 'A server error occurred. Please try again later.',
        retryable: true,
      };
    }

    return {
      message: error.message,
      category: 'unknown',
      retryable: true,
    };
  }

  return {
    message: 'An unexpected error occurred',
    category: 'unknown',
    retryable: true,
  };
}

interface UseAsyncStateOptions<T> {
  data: T | null | undefined;
  isLoading: boolean;
  error: unknown;
  isEmpty?: boolean;
}

export function useAsyncState<T>(options: UseAsyncStateOptions<T>): AsyncState<T> {
  const { data, isLoading, error, isEmpty } = options;

  return useMemo(() => {
    if (isLoading) {
      return { status: 'loading', data: null, error: null };
    }

    if (error) {
      return { status: 'error', data: null, error: classifyError(error) };
    }

    if (isEmpty || (Array.isArray(data) && data.length === 0)) {
      return { status: 'empty', data: null, error: null };
    }

    if (data !== null && data !== undefined) {
      return { status: 'success', data, error: null };
    }

    return { status: 'empty', data: null, error: null };
  }, [data, isLoading, error, isEmpty]);
}

export default useAsyncState;
