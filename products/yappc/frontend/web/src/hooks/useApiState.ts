/**
 * API State Hook
 *
 * React hook for comprehensive API state management with circuit breaker patterns,
 * exponential backoff retry logic, and UI state transitions.
 *
 * @doc.type hook
 * @doc.purpose Comprehensive API state management with circuit breaker and retry logic
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { UiState, CircuitBreakerConfig, RetryConfig } from '../lib/stateMachine';
import { CircuitBreaker, RetryHandler, UiStateMachine, isLoadingState, isErrorState } from '../lib/stateMachine';

// ============================================================================
// Configuration Types
// ============================================================================

export interface ApiStateConfig<T> {
  queryKey: string[];
  queryFn: () => Promise<T>;
  retryConfig?: RetryConfig;
  circuitBreaker?: CircuitBreakerConfig;
  optimisticUpdate?: (data: T) => void;
  enabled?: boolean;
  staleTime?: number;
  gcTime?: number;
}

export interface ApiStateResult<T> {
  // Data
  data: T | undefined;
  error: Error | null;
  
  // UI State
  state: UiState;
  stateMessage: string;
  
  // Loading states
  isLoading: boolean;
  isFetching: boolean;
  isSuccess: boolean;
  isError: boolean;
  isIdle: boolean;
  
  // Circuit breaker
  circuitBreakerState: 'closed' | 'open' | 'half-open';
  
  // Actions
  refetch: () => void;
  reset: () => void;
  retry: () => void;
  
  // State machine
  transition: (state: UiState, reason?: string) => void;
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Hook for comprehensive API state management
 *
 * Provides:
 * - Circuit breaker pattern for preventing cascading failures
 * - Exponential backoff retry logic
 * - UI state machine with 16 states
 * - Optimistic updates
 * - Automatic state transitions based on API responses
 */
export function useApiState<T>(config: ApiStateConfig<T>): ApiStateResult<T> {
  const {
    queryKey,
    queryFn,
    retryConfig = {
      attempts: 3,
      backoff: 'exponential',
      baseDelay: 1000,
      maxDelay: 30000,
    },
    circuitBreaker: circuitBreakerConfig = {
      failureThreshold: 5,
      recoveryTimeout: 60000,
    },
    enabled = true,
    staleTime = 30000,
    gcTime = 300000,
  } = config;

  const queryClient = useQueryClient();
  const stateMachineRef = useRef(new UiStateMachine());
  const circuitBreakerRef = useRef(new CircuitBreaker(circuitBreakerConfig));
  const retryHandlerRef = useRef(new RetryHandler(retryConfig));

  // UI state
  const [uiState, setUiState] = useState<UiState>('idle');
  const [circuitBreakerState, setCircuitBreakerState] = useState<'closed' | 'open' | 'half-open'>('closed');

  // Subscribe to state machine changes
  useEffect(() => {
    const unsubscribe = stateMachineRef.current.subscribe((state) => {
      setUiState(state);
    });

    return unsubscribe;
  }, []);

  // Subscribe to circuit breaker state changes
  useEffect(() => {
    const unsubscribe = circuitBreakerRef.current.subscribe?.((state: 'closed' | 'open' | 'half-open') => {
      setCircuitBreakerState(state);
    }) || (() => {});

    return unsubscribe;
  }, [circuitBreakerConfig]);

  // Query with circuit breaker and retry logic
  const query = useQuery({
    queryKey,
    queryFn: async () => {
      stateMachineRef.current.transition('loading', 'Fetching data');

      return await retryHandlerRef.current.execute(async () => {
        return await circuitBreakerRef.current.execute(queryFn);
      });
    },
    enabled,
    staleTime,
    gcTime,
    retry: false, // We handle retry manually with our retry handler
  });

  // Update UI state based on query state
  useEffect(() => {
    if (!enabled) {
      stateMachineRef.current.transition('idle', 'Query disabled');
      return;
    }

    if (query.isLoading) {
      stateMachineRef.current.transition('loading', 'Query in progress');
      return;
    }

    if (query.isError) {
      const error = query.error as Error;
      
      // Determine appropriate error state
      if (error.message.includes('timeout') || error.message.includes('TIMEOUT')) {
        stateMachineRef.current.transition('timeout', error.message);
      } else if (error.message.includes('rate limit') || error.message.includes('429')) {
        stateMachineRef.current.transition('rate_limit', error.message);
      } else if (error.message.includes('permission') || error.message.includes('403')) {
        stateMachineRef.current.transition('permission_denied', error.message);
      } else if (error.message.includes('auth') || error.message.includes('401')) {
        stateMachineRef.current.transition('auth_failure', error.message);
      } else if (error.message.includes('conflict') || error.message.includes('409')) {
        stateMachineRef.current.transition('conflict', error.message);
      } else if (error.message.includes('Circuit breaker is OPEN')) {
        stateMachineRef.current.transition('offline', 'Service unavailable');
      } else if (error.message.includes('server error') || error.message.includes('5')) {
        stateMachineRef.current.transition('server_error', error.message);
      } else {
        stateMachineRef.current.transition('server_error', error.message);
      }
      return;
    }

    if (query.isSuccess) {
      if (query.data === undefined || query.data === null) {
        stateMachineRef.current.transition('empty', 'No data returned');
      } else if (Array.isArray(query.data) && query.data.length === 0) {
        stateMachineRef.current.transition('empty', 'Empty array');
      } else {
        stateMachineRef.current.transition('success', 'Data loaded successfully');
      }
    }
  }, [query.isLoading, query.isError, query.isSuccess, query.data, query.error, enabled]);

  // Handle stale data
  useEffect(() => {
    if (query.isStale && query.isSuccess) {
      stateMachineRef.current.transition('stale', 'Data is stale');
    }
  }, [query.isStale, query.isSuccess]);

  // Refetch with state transition
  const refetch = useCallback(() => {
    stateMachineRef.current.transition('loading', 'Manual refetch');
    query.refetch();
  }, [query]);

  // Reset query and state machine
  const reset = useCallback(() => {
    stateMachineRef.current.reset();
    circuitBreakerRef.current.reset();
    queryClient.removeQueries({ queryKey });
  }, [queryClient, queryKey]);

  // Retry with circuit breaker reset
  const retry = useCallback(() => {
    stateMachineRef.current.transition('retry', 'Retrying failed request');
    circuitBreakerRef.current.reset();
    query.refetch();
  }, [query]);

  // Manual state transition
  const transition = useCallback((state: UiState, reason?: string) => {
    stateMachineRef.current.transition(state, reason);
  }, []);

  // Get state message
  const getStateMessage = useCallback((state: UiState): string => {
    const messages: Record<UiState, string> = {
      idle: 'Ready',
      loading: 'Loading...',
      success: 'Success',
      empty: 'No data available',
      partial: 'Partial data loaded',
      stale: 'Data may be outdated',
      validation_error: 'Invalid data',
      permission_denied: 'Permission denied',
      auth_failure: 'Authentication failed',
      conflict: 'Data conflict detected',
      timeout: 'Request timed out',
      rate_limit: 'Too many requests',
      server_error: 'Server error',
      offline: 'You are offline',
      retry: 'Retrying...',
      background_refresh: 'Updating...',
    };
    return messages[state] || 'Unknown state';
  }, []);

  return {
    data: query.data,
    error: query.error as Error | null,
    state: uiState,
    stateMessage: getStateMessage(uiState),
    isLoading: isLoadingState(uiState),
    isFetching: query.isFetching,
    isSuccess: uiState === 'success',
    isError: isErrorState(uiState),
    isIdle: uiState === 'idle',
    circuitBreakerState,
    refetch,
    reset,
    retry,
    transition,
  };
}

// ============================================================================
// Mutation Hook
// ============================================================================

export interface UseApiMutationOptions<TData, TVariables, TError = Error> {
  mutationFn: (variables: TVariables) => Promise<TData>;
  onMutate?: (variables: TVariables) => void;
  onSuccess?: (data: TData, variables: TVariables) => void;
  onError?: (error: TError, variables: TVariables) => void;
  onSettled?: (data: TData | undefined, error: TError | null, variables: TVariables) => void;
  retryConfig?: RetryConfig;
  circuitBreaker?: CircuitBreakerConfig;
}

export interface UseApiMutationResult<TData, TVariables, TError> {
  mutate: (variables: TVariables) => void;
  mutateAsync: (variables: TVariables) => Promise<TData>;
  data: TData | undefined;
  error: TError | null;
  isLoading: boolean;
  isSuccess: boolean;
  isError: boolean;
  isIdle: boolean;
  reset: () => void;
}

/**
 * Hook for API mutations with circuit breaker and retry logic
 */
export function useApiMutation<TData, TVariables, TError = Error>(
  options: UseApiMutationOptions<TData, TVariables, TError>
): UseApiMutationResult<TData, TVariables, TError> {
  const {
    mutationFn,
    onMutate,
    onSuccess,
    onError,
    onSettled,
    retryConfig = {
      attempts: 3,
      backoff: 'exponential',
      baseDelay: 1000,
      maxDelay: 30000,
    },
    circuitBreaker: circuitBreakerConfig = {
      failureThreshold: 5,
      recoveryTimeout: 60000,
    },
  } = options;

  const circuitBreakerRef = useRef(new CircuitBreaker(circuitBreakerConfig));
  const retryHandlerRef = useRef(new RetryHandler(retryConfig));

  const mutation = useMutation({
    mutationFn: async (variables: TVariables) => {
      return await retryHandlerRef.current.execute(async () => {
        return await circuitBreakerRef.current.execute(() => mutationFn(variables));
      });
    },
    onMutate,
    onSuccess,
    onError,
    onSettled,
  });

  return {
    mutate: mutation.mutate,
    mutateAsync: mutation.mutateAsync,
    data: mutation.data,
    error: mutation.error as TError | null,
    isLoading: mutation.isPending,
    isSuccess: mutation.isSuccess,
    isError: mutation.isError,
    isIdle: mutation.isIdle,
    reset: mutation.reset,
  };
}
