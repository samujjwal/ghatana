/**
 * Error Boundary Hooks
 * 
 * React hooks for error boundary functionality
 * 
 * @module ui/error
 * @doc.type hook
 * @doc.purpose Programmatic error handling
 * @doc.layer ui
 */

import { useCallback, useState } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Error state
 */
interface ErrorState {
  /** Has error */
  hasError: boolean;
  
  /** Error object */
  error: Error | null;
}

/**
 * Error handler return type
 */
interface ErrorHandler {
  /** Current error state */
  error: Error | null;
  
  /** Whether there is an error */
  hasError: boolean;
  
  /** Set error */
  setError: (error: Error | null) => void;
  
  /** Clear error */
  clearError: () => void;
  
  /** Try/catch wrapper */
  tryCatch: <T>(fn: () => T) => T | null;
  
  /** Async try/catch wrapper */
  tryCatchAsync: <T>(fn: () => Promise<T>) => Promise<T | null>;
}

// ============================================================================
// useErrorHandler Hook
// ============================================================================

/**
 * Use error handler
 * Provides programmatic error handling within components
 * 
 * @example
 * function MyComponent() {
 *   const { error, setError, clearError, tryCatch } = useErrorHandler();
 *   
 *   const handleClick = () => {
 *     tryCatch(() => {
 *       riskyOperation();
 *     });
 *   };
 *   
 *   if (error) {
 *     return <ErrorDisplay error={error} onDismiss={clearError} />;
 *   }
 *   
 *   return <button onClick={handleClick}>Do Something</button>;
 * }
 */
export function useErrorHandler(): ErrorHandler {
  const [errorState, setErrorState] = useState<ErrorState>({
    hasError: false,
    error: null,
  });
  
  /**
   * Set error
   */
  const setError = useCallback((error: Error | null) => {
    setErrorState({
      hasError: error !== null,
      error,
    });
  }, []);
  
  /**
   * Clear error
   */
  const clearError = useCallback(() => {
    setErrorState({
      hasError: false,
      error: null,
    });
  }, []);
  
  /**
   * Try/catch wrapper
   * Executes function and catches errors
   */
  const tryCatch = useCallback(<T,>(fn: () => T): T | null => {
    try {
      return fn();
    } catch (error) {
      setError(error instanceof Error ? error : new Error(String(error)));
      return null;
    }
  }, [setError]);
  
  /**
   * Async try/catch wrapper
   * Executes async function and catches errors
   */
  const tryCatchAsync = useCallback(async <T,>(fn: () => Promise<T>): Promise<T | null> => {
    try {
      return await fn();
    } catch (error) {
      setError(error instanceof Error ? error : new Error(String(error)));
      return null;
    }
  }, [setError]);
  
  return {
    error: errorState.error,
    hasError: errorState.hasError,
    setError,
    clearError,
    tryCatch,
    tryCatchAsync,
  };
}

// ============================================================================
// useErrorReset Hook
// ============================================================================

/**
 * Error reset return type
 */
interface ErrorReset {
  /** Reset key */
  resetKey: number;
  
  /** Reset error boundary */
  reset: () => void;
}

/**
 * Use error reset
 * Provides error boundary reset mechanism
 * Use with ErrorBoundary's resetKeys prop
 * 
 * @example
 * function ParentComponent() {
 *   const { resetKey, reset } = useErrorReset();
 *   
 *   return (
 *     <ErrorBoundary resetKeys={[resetKey]}>
 *       <ChildComponent onError={reset} />
 *     </ErrorBoundary>
 *   );
 * }
 */
export function useErrorReset(): ErrorReset {
  const [resetKey, setResetKey] = useState(0);
  
  const reset = useCallback(() => {
    setResetKey(prev => prev + 1);
  }, []);
  
  return {
    resetKey,
    reset,
  };
}

// ============================================================================
// useAsyncError Hook
// ============================================================================

/**
 * Use async error
 * Throws async errors to nearest error boundary
 * Useful for error boundaries to catch async errors
 * 
 * @example
 * function MyComponent() {
 *   const throwError = useAsyncError();
 *   
 *   const loadData = async () => {
 *     try {
 *       await fetchData();
 *     } catch (error) {
 *       throwError(error); // This will be caught by error boundary
 *     }
 *   };
 * }
 */
export function useAsyncError(): (error: Error) => void {
  const [, setError] = useState<Error | null>(null);
  
  return useCallback((error: Error) => {
    setError(() => {
      throw error;
    });
  }, []);
}
