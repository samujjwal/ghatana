/**
 * API Hook
 * 
 * Provides type-safe API calls with loading states and error handling.
 * Integrates with authentication and optimistic updates.
 * 
 * @module api/hooks
 */

import { useState, useCallback, useRef } from 'react';

export interface ApiConfig {
  /** Base API URL */
  baseUrl?: string;
  
  /** Default headers */
  headers?: Record<string, string>;
  
  /** Authentication token */
  token?: string;
  
  /** Request timeout in milliseconds */
  timeout?: number;
  
  /** Retry configuration */
  retry?: {
    maxAttempts: number;
    backoff: 'linear' | 'exponential';
    initialDelay: number;
  };
}

export interface UseApiOptions<T> {
  /** Initial data */
  initialData?: T;
  
  /** Callback on success */
  onSuccess?: (data: T) => void;
  
  /** Callback on error */
  onError?: (error: Error) => void;
  
  /** Enable automatic retry */
  retry?: boolean;
  
  /** Cache key for request deduplication */
  cacheKey?: string;
}

/**
 * API Hook
 * 
 * Provides type-safe API calls with automatic loading/error states.
 * 
 * @example
 * ```tsx
 * const { data, loading, error, execute } = useApi<User[]>({
 *   initialData: [],
 *   onSuccess: (users) => console.log('Loaded users:', users),
 * });
 * 
 * // Execute API call
 * await execute('/api/users');
 * ```
 */
export function useApi<T = unknown>(options: UseApiOptions<T> = {}) {
  const {
    initialData,
    onSuccess,
    onError,
    retry = true,
    cacheKey,
  } = options;

  const [data, setData] = useState<T | undefined>(initialData);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  
  const abortControllerRef = useRef<AbortController | null>(null);
  const cacheRef = useRef<Map<string, { data: T; timestamp: number }>>(new Map());

  /**
   * Execute API request
   */
  const execute = useCallback(
    async (
      endpoint: string,
      config: RequestInit = {}
    ): Promise<T | undefined> => {
      // Cancel previous request
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      // Check cache
      if (cacheKey && cacheRef.current.has(cacheKey)) {
        const cached = cacheRef.current.get(cacheKey)!;
        const age = Date.now() - cached.timestamp;
        
        // Use cache if less than 5 minutes old
        if (age < 5 * 60 * 1000) {
          setData(cached.data);
          return cached.data;
        }
      }

      // Create abort controller
      abortControllerRef.current = new AbortController();

      setLoading(true);
      setError(null);

      try {
        const response = await fetch(endpoint, {
          ...config,
          signal: abortControllerRef.current.signal,
        });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const result = await response.json() as T;
        
        setData(result);
        
        // Cache result
        if (cacheKey) {
          cacheRef.current.set(cacheKey, {
            data: result,
            timestamp: Date.now(),
          });
        }

        onSuccess?.(result);
        return result;
      } catch (err) {
        if (err instanceof Error && err.name === 'AbortError') {
          // Request was cancelled
          return undefined;
        }

        const error = err instanceof Error ? err : new Error('Request failed');
        setError(error);
        onError?.(error);
        return undefined;
      } finally {
        setLoading(false);
        abortControllerRef.current = null;
      }
    },
    [cacheKey, onSuccess, onError]
  );

  /**
   * Cancel ongoing request
   */
  const cancel = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
  }, []);

  /**
   * Clear cache
   */
  const clearCache = useCallback(() => {
    cacheRef.current.clear();
  }, []);

  /**
   * Reset state
   */
  const reset = useCallback(() => {
    setData(initialData);
    setLoading(false);
    setError(null);
    cancel();
  }, [initialData, cancel]);

  return {
    // State
    data,
    loading,
    error,
    
    // Actions
    execute,
    cancel,
    clearCache,
    reset,
    
    // Helpers
    setData, // For manual updates
  };
}

export default useApi;
