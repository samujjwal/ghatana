/**
 * P1-030: Mutation error handling hook for DMOS UI.
 *
 * <p>Provides consistent error handling for all mutations with:
 * <ul>
 *   <li>Actionable error messages</li>
 *   <li>Correlation ID display for support</li>
 *   <li>Retry capability where safe</li>
 *   <li>Error categorization (network, auth, validation, server)</li>
 * </ul>
 *
 * @doc.type hook
 * @doc.purpose Mutation error handling with correlation ID support (P1-030)
 * @doc.layer frontend
 */

import { useState, useCallback } from 'react';

export interface ApiError {
  status: number;
  code: string;
  message: string;
  correlationId: string;
  details?: Record<string, unknown>;
  retryable: boolean;
}

export interface MutationState<T> {
  data: T | null;
  isLoading: boolean;
  error: ApiError | null;
  isSuccess: boolean;
}

export interface UseMutationErrorResult<T, Args extends unknown[]> {
  state: MutationState<T>;
  execute: (...args: Args) => Promise<T | null>;
  reset: () => void;
  retry: () => Promise<T | null>;
}

const RETRYABLE_STATUS_CODES = [408, 429, 502, 503, 504];

function categorizeError(status: number): { type: string; defaultMessage: string; retryable: boolean } {
  if (status === 401) {
    return { type: 'AUTH', defaultMessage: 'Your session has expired. Please sign in again.', retryable: false };
  }
  if (status === 403) {
    return { type: 'FORBIDDEN', defaultMessage: 'You do not have permission to perform this action.', retryable: false };
  }
  if (status === 404) {
    return { type: 'NOT_FOUND', defaultMessage: 'The requested resource was not found.', retryable: false };
  }
  if (status === 409) {
    return { type: 'CONFLICT', defaultMessage: 'This action conflicts with a concurrent change. Please refresh and try again.', retryable: true };
  }
  if (status === 422) {
    return { type: 'VALIDATION', defaultMessage: 'The request could not be processed. Please check your input.', retryable: false };
  }
  if (status === 423) {
    return { type: 'LOCKED', defaultMessage: 'The resource is currently locked. Please try again later.', retryable: true };
  }
  if (RETRYABLE_STATUS_CODES.includes(status)) {
    return { type: 'RETRYABLE', defaultMessage: 'A temporary error occurred. Please try again.', retryable: true };
  }
  if (status >= 500) {
    return { type: 'SERVER', defaultMessage: 'A server error occurred. Please contact support if the problem persists.', retryable: false };
  }
  if (status >= 400) {
    return { type: 'CLIENT', defaultMessage: 'An error occurred. Please check your input and try again.', retryable: false };
  }
  return { type: 'UNKNOWN', defaultMessage: 'An unexpected error occurred.', retryable: false };
}

function parseApiError(error: unknown): ApiError {
  if (error && typeof error === 'object') {
    const err = error as Record<string, unknown>;

    // If it's already an ApiError structure
    if (typeof err.status === 'number') {
      const category = categorizeError(err.status);
      const explicitCode = typeof err.code === 'string' ? err.code.toUpperCase() : undefined;
      const normalizedCode =
        err.status === 400 && explicitCode === 'VALIDATION'
          ? 'VALIDATION'
          : category.type;
      return {
        status: err.status,
        code: normalizedCode,
        message: category.defaultMessage,
        correlationId: String(err.correlationId || 'unknown'),
        details: err.details as Record<string, unknown> | undefined,
        retryable: typeof err.retryable === 'boolean' ? err.retryable : category.retryable,
      };
    }

    // Network errors
    if (err.name === 'TypeError' && String(err.message).includes('fetch')) {
      return {
        status: 0,
        code: 'NETWORK_ERROR',
        message: 'Unable to connect to the server. Please check your internet connection.',
        correlationId: 'unknown',
        retryable: true,
      };
    }
  }

  // Default error
  return {
    status: 500,
    code: 'UNKNOWN_ERROR',
    message: error instanceof Error ? error.message : 'An unexpected error occurred.',
    correlationId: 'unknown',
    retryable: false,
  };
}

export function useMutationError<T, Args extends unknown[]>(
  mutationFn: (...args: Args) => Promise<T>,
  options?: {
    onSuccess?: (data: T) => void;
    onError?: (error: ApiError) => void;
  }
): UseMutationErrorResult<T, Args> {
  const [state, setState] = useState<MutationState<T>>({
    data: null,
    isLoading: false,
    error: null,
    isSuccess: false,
  });

  const [lastArgs, setLastArgs] = useState<Args | null>(null);

  const reset = useCallback(() => {
    setState({
      data: null,
      isLoading: false,
      error: null,
      isSuccess: false,
    });
    setLastArgs(null);
  }, []);

  const execute = useCallback(
    async (...args: Args): Promise<T | null> => {
      setLastArgs(args);
      setState(prev => ({ ...prev, isLoading: true, error: null, isSuccess: false }));

      try {
        const data = await mutationFn(...args);
        setState({
          data,
          isLoading: false,
          error: null,
          isSuccess: true,
        });
        options?.onSuccess?.(data);
        return data;
      } catch (error) {
        const apiError = parseApiError(error);
        setState(prev => ({
          ...prev,
          isLoading: false,
          error: apiError,
          isSuccess: false,
        }));
        options?.onError?.(apiError);
        return null;
      }
    },
    [mutationFn, options]
  );

  const retry = useCallback(async (): Promise<T | null> => {
    if (!lastArgs) {
      return null;
    }
    return execute(...lastArgs);
  }, [execute, lastArgs]);

  return {
    state,
    execute,
    reset,
    retry,
  };
}
