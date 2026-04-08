/**
 * Error Recovery Hook
 *
 * Provides advanced error recovery patterns including:
 * - Automatic retry with exponential backoff
 * - Offline detection and operation queueing
 * - Error classification and recovery suggestions
 *
 * @doc.type hook
 * @doc.purpose Advanced error recovery patterns
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect, useRef } from 'react';

// ============================================================================
// Types
// ============================================================================

export type ErrorSeverity = 'low' | 'medium' | 'high' | 'critical';

export type ErrorCategory = 
  | 'network'
  | 'validation'
  | 'authentication'
  | 'authorization'
  | 'server'
  | 'timeout'
  | 'offline'
  | 'unknown';

export interface ErrorInfo {
  message: string;
  category: ErrorCategory;
  severity: ErrorSeverity;
  timestamp: number;
  retryable: boolean;
  suggestedAction?: string;
}

export interface RetryConfig {
  maxAttempts?: number;
  initialDelay?: number;
  maxDelay?: number;
  backoffMultiplier?: number;
  retryableErrors?: ErrorCategory[];
}

export interface UseErrorRecoveryOptions {
  enableAutoRetry?: boolean;
  retryConfig?: RetryConfig;
  enableOfflineQueue?: boolean;
}

export interface UseErrorRecoveryResult {
  error: Error | null;
  errorInfo: ErrorInfo | null;
  isOnline: boolean;
  isRetrying: boolean;
  retryCount: number;
  hasPendingOperations: boolean;
  retry: () => Promise<void>;
  clearError: () => void;
  executeWithRetry: <T>(fn: () => Promise<T>) => Promise<T>;
  queueOperation: <T>(fn: () => Promise<T>) => Promise<T>;
  processOfflineQueue: () => Promise<void>;
}

// ============================================================================
// Error Classification
// ============================================================================

function classifyError(error: Error): ErrorInfo {
  const message = error.message.toLowerCase();
  const timestamp = Date.now();

  // Network errors
  if (message.includes('network') || message.includes('fetch')) {
    return {
      message: error.message,
      category: 'network',
      severity: 'medium',
      timestamp,
      retryable: true,
      suggestedAction: 'Check your internet connection and try again',
    };
  }

  // Offline errors
  if (message.includes('offline') || message.includes('no internet')) {
    return {
      message: error.message,
      category: 'offline',
      severity: 'high',
      timestamp,
      retryable: true,
      suggestedAction: 'Your device appears to be offline. Operations will be queued.',
    };
  }

  // Authentication errors
  if (message.includes('unauthorized') || message.includes('401')) {
    return {
      message: error.message,
      category: 'authentication',
      severity: 'high',
      timestamp,
      retryable: false,
      suggestedAction: 'Please log in again',
    };
  }

  // Authorization errors
  if (message.includes('forbidden') || message.includes('403')) {
    return {
      message: error.message,
      category: 'authorization',
      severity: 'high',
      timestamp,
      retryable: false,
      suggestedAction: 'You do not have permission to perform this action',
    };
  }

  // Server errors
  if (message.includes('500') || message.includes('server error')) {
    return {
      message: error.message,
      category: 'server',
      severity: 'critical',
      timestamp,
      retryable: true,
      suggestedAction: 'The server is experiencing issues. Please try again later',
    };
  }

  // Timeout errors
  if (message.includes('timeout') || message.includes('timed out')) {
    return {
      message: error.message,
      category: 'timeout',
      severity: 'medium',
      timestamp,
      retryable: true,
      suggestedAction: 'The request timed out. Please try again',
    };
  }

  // Validation errors
  if (message.includes('validation') || message.includes('invalid')) {
    return {
      message: error.message,
      category: 'validation',
      severity: 'low',
      timestamp,
      retryable: false,
      suggestedAction: 'Please check your input and try again',
    };
  }

  // Unknown errors
  return {
    message: error.message,
    category: 'unknown',
    severity: 'medium',
    timestamp,
    retryable: true,
    suggestedAction: 'An unexpected error occurred. Please try again',
  };
}

// ============================================================================
// Retry Logic with Exponential Backoff
// ============================================================================

async function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function executeWithRetryLogic<T>(
  fn: () => Promise<T>,
  config: Required<RetryConfig>,
  onRetry?: (attempt: number, error: Error) => void
): Promise<T> {
  let lastError: Error;
  let delay = config.initialDelay;

  for (let attempt = 1; attempt <= config.maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error as Error;
      const errorInfo = classifyError(lastError);

      // Check if error is retryable
      if (!errorInfo.retryable || !config.retryableErrors.includes(errorInfo.category)) {
        throw lastError;
      }

      // If this was the last attempt, throw
      if (attempt === config.maxAttempts) {
        throw lastError;
      }

      // Call retry callback
      onRetry?.(attempt, lastError);

      // Wait with exponential backoff
      await sleep(delay);
      delay = Math.min(delay * config.backoffMultiplier, config.maxDelay);
    }
  }

  throw lastError!;
}

// ============================================================================
// Offline Operation Queue
// ============================================================================

interface QueuedOperation<T> {
  fn: () => Promise<T>;
  resolve: (value: T) => void;
  reject: (error: Error) => void;
  timestamp: number;
}

class OfflineQueue {
  private queue: QueuedOperation<unknown>[] = [];
  private isProcessing = false;

  enqueue<T>(fn: () => Promise<T>): Promise<T> {
    return new Promise((resolve, reject) => {
      this.queue.push({
        fn,
        resolve: resolve as (value: unknown) => void,
        reject,
        timestamp: Date.now(),
      });
    });
  }

  async process(): Promise<void> {
    if (this.isProcessing || this.queue.length === 0) {
      return;
    }

    this.isProcessing = true;

    while (this.queue.length > 0) {
      const operation = this.queue.shift();
      if (!operation) break;

      try {
        const result = await operation.fn();
        operation.resolve(result);
      } catch (error) {
        operation.reject(error as Error);
      }
    }

    this.isProcessing = false;
  }

  size(): number {
    return this.queue.length;
  }

  clear(): void {
    this.queue = [];
  }
}

// ============================================================================
// Hook Implementation
// ============================================================================

const defaultRetryConfig: Required<RetryConfig> = {
  maxAttempts: 3,
  initialDelay: 1000,
  maxDelay: 30000,
  backoffMultiplier: 2,
  retryableErrors: ['network', 'server', 'timeout', 'offline'],
};

const offlineQueue = new OfflineQueue();

export function useErrorRecovery({
  enableAutoRetry = true,
  retryConfig = {},
  enableOfflineQueue = true,
}: UseErrorRecoveryOptions = {}): UseErrorRecoveryResult {
  const [error, setError] = useState<Error | null>(null);
  const [errorInfo, setErrorInfo] = useState<ErrorInfo | null>(null);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [isRetrying, setIsRetrying] = useState(false);
  const [retryCount, setRetryCount] = useState(0);

  const config = { ...defaultRetryConfig, ...retryConfig };
  const retryCountRef = useRef(0);

  // Monitor online/offline status
  useEffect(() => {
    const handleOnline = () => {
      setIsOnline(true);
      // Process offline queue when back online
      if (enableOfflineQueue) {
        offlineQueue.process().catch(console.error);
      }
    };

    const handleOffline = () => {
      setIsOnline(false);
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, [enableOfflineQueue]);

  // Execute function with automatic retry
  const executeWithRetry = useCallback(
    async <T>(fn: () => Promise<T>): Promise<T> => {
      if (!enableAutoRetry) {
        return fn();
      }

      setIsRetrying(true);
      retryCountRef.current = 0;

      try {
        const result = await executeWithRetryLogic(
          fn,
          config,
          (attempt, err) => {
            retryCountRef.current = attempt;
            setRetryCount(attempt);
          }
        );
        setRetryCount(0);
        setIsRetrying(false);
        return result;
      } catch (err) {
        setIsRetrying(false);
        const error = err as Error;
        const classifiedError = classifyError(error);
        setError(error);
        setErrorInfo(classifiedError);
        throw error;
      }
    },
    [enableAutoRetry, config]
  );

  // Manual retry
  const retry = useCallback(async () => {
    setError(null);
    setErrorInfo(null);
    setRetryCount(0);
    // Note: This is a placeholder - actual retry logic depends on context
    // Components using this hook should implement their own retry logic
  }, []);

  // Clear error
  const clearError = useCallback(() => {
    setError(null);
    setErrorInfo(null);
    setRetryCount(0);
  }, []);

  // Queue operation for offline execution
  const queueOperation = useCallback(
    async <T>(fn: () => Promise<T>): Promise<T> => {
      if (isOnline || !enableOfflineQueue) {
        return executeWithRetry(fn);
      }

      return offlineQueue.enqueue(fn);
    },
    [isOnline, enableOfflineQueue, executeWithRetry]
  );

  // Process offline queue
  const processOfflineQueue = useCallback(async () => {
    await offlineQueue.process();
  }, []);

  return {
    error,
    errorInfo,
    isOnline,
    isRetrying,
    retryCount,
    hasPendingOperations: offlineQueue.size() > 0,
    retry,
    clearError,
    executeWithRetry,
    queueOperation,
    processOfflineQueue,
  };
}
