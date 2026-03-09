/**
 * Production-Ready Error Handling and Logging System
 * 
 * Provides comprehensive error handling, logging, and monitoring
 * 
 * @doc.type service
 * @doc.purpose Error handling and logging infrastructure
 * @doc.layer infrastructure
 * @doc.pattern Service
 */

import { systemLogger } from './logger';

export interface ErrorContext {
  userId?: string;
  requestId?: string;
  operation?: string;
  component?: string;
  additionalData?: Record<string, any>;
}

export interface ErrorReport {
  error: Error;
  context: ErrorContext;
  timestamp: Date;
  severity: 'low' | 'medium' | 'high' | 'critical';
  stackTrace?: string;
  userAgent?: string;
  ipAddress?: string;
}

/**
 * Enhanced Error class with context
 */
export class ContextualError extends Error {
  constructor(
    message: string,
    public readonly context: ErrorContext,
    public readonly severity: 'low' | 'medium' | 'high' | 'critical' = 'medium',
    public readonly originalError?: Error
  ) {
    super(message);
    this.name = 'ContextualError';
    
    // Maintain stack trace
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, ContextualError);
    }
  }
}

/**
 * Error Handler Service
 */
export class ErrorHandlerService {
  private static instance: ErrorHandlerService;
  private errorCallbacks: Array<(error: ErrorReport) => void> = [];
  private errorCounts: Map<string, number> = new Map();
  private lastErrors: Map<string, Date> = new Map();

  private constructor() {}

  static getInstance(): ErrorHandlerService {
    if (!ErrorHandlerService.instance) {
      ErrorHandlerService.instance = new ErrorHandlerService();
    }
    return ErrorHandlerService.instance;
  }

  /**
   * Handle an error with context
   */
  handleError(error: Error, context: ErrorContext = {}): void {
    const contextualError = error instanceof ContextualError 
      ? error 
      : new ContextualError(error.message, context, 'medium', error);

    const errorReport: ErrorReport = {
      error: contextualError,
      context: contextualError.context,
      timestamp: new Date(),
      severity: contextualError.severity,
      stackTrace: contextualError.stack,
      userAgent: context.additionalData?.userAgent,
      ipAddress: context.additionalData?.ipAddress,
    };

    // Log the error
    this.logError(errorReport);

    // Track error frequency
    this.trackErrorFrequency(errorReport);

    // Notify callbacks
    this.notifyCallbacks(errorReport);

    // Take action based on severity
    this.takeSeverityBasedAction(errorReport);
  }

  /**
   * Log error with structured format
   */
  private logError(errorReport: ErrorReport): void {
    const logData = {
      error: {
        name: errorReport.error.name,
        message: errorReport.error.message,
        stack: errorReport.stackTrace,
      },
      context: errorReport.context,
      severity: errorReport.severity,
      timestamp: errorReport.timestamp.toISOString(),
      userAgent: errorReport.userAgent,
      ipAddress: errorReport.ipAddress,
    };

    switch (errorReport.severity) {
      case 'critical':
        systemLogger.error('[CRITICAL]', errorReport.error.message, logData);
        break;
      case 'high':
        systemLogger.error('[HIGH]', errorReport.error.message, logData);
        break;
      case 'medium':
        systemLogger.warn('[MEDIUM]', errorReport.error.message, logData);
        break;
      case 'low':
        systemLogger.info('[LOW]', errorReport.error.message, logData);
        break;
    }
  }

  /**
   * Track error frequency for rate limiting
   */
  private trackErrorFrequency(errorReport: ErrorReport): void {
    const errorKey = `${errorReport.context.operation || 'unknown'}-${errorReport.error.name}`;
    const currentCount = this.errorCounts.get(errorKey) || 0;
    this.errorCounts.set(errorKey, currentCount + 1);
    this.lastErrors.set(errorKey, new Date());
  }

  /**
   * Notify all registered error callbacks
   */
  private notifyCallbacks(errorReport: ErrorReport): void {
    this.errorCallbacks.forEach(callback => {
      try {
        callback(errorReport);
      } catch (callbackError) {
        systemLogger.error('Error in error callback', callbackError);
      }
    });
  }

  /**
   * Take action based on error severity
   */
  private takeSeverityBasedAction(errorReport: ErrorReport): void {
    switch (errorReport.severity) {
      case 'critical':
        // Critical errors might require immediate action
        this.handleCriticalError(errorReport);
        break;
      case 'high':
        // High errors might need alerts
        this.handleHighError(errorReport);
        break;
      case 'medium':
        // Medium errors are logged and tracked
        break;
      case 'low':
        // Low errors are just logged
        break;
    }
  }

  /**
   * Handle critical errors
   */
  private handleCriticalError(errorReport: ErrorReport): void {
    // In production, this might trigger alerts, shutdown procedures, etc.
    systemLogger.error('CRITICAL ERROR DETECTED', {
      error: errorReport.error.message,
      context: errorReport.context,
      action: 'immediate_attention_required',
    });
  }

  /**
   * Handle high severity errors
   */
  private handleHighError(errorReport: ErrorReport): void {
    // In production, this might trigger monitoring alerts
    systemLogger.warn('HIGH SEVERITY ERROR', {
      error: errorReport.error.message,
      context: errorReport.context,
      action: 'monitoring_alert_triggered',
    });
  }

  /**
   * Register error callback
   */
  onError(callback: (error: ErrorReport) => void): void {
    this.errorCallbacks.push(callback);
  }

  /**
   * Remove error callback
   */
  removeErrorCallback(callback: (error: ErrorReport) => void): void {
    const index = this.errorCallbacks.indexOf(callback);
    if (index > -1) {
      this.errorCallbacks.splice(index, 1);
    }
  }

  /**
   * Get error statistics
   */
  getErrorStats(): {
    totalErrors: number;
    errorsByType: Record<string, number>;
    recentErrors: Array<{ type: string; count: number; lastOccurrence: Date }>;
  } {
    const totalErrors = Array.from(this.errorCounts.values()).reduce((sum, count) => sum + count, 0);
    const errorsByType = Object.fromEntries(this.errorCounts);
    const recentErrors = Array.from(this.errorCounts.entries()).map(([type, count]) => ({
      type,
      count,
      lastOccurrence: this.lastErrors.get(type) || new Date(),
    }));

    return {
      totalErrors,
      errorsByType,
      recentErrors,
    };
  }

  /**
   * Reset error statistics
   */
  resetStats(): void {
    this.errorCounts.clear();
    this.lastErrors.clear();
  }
}

/**
 * Global error handler instance
 */
export const errorHandler = ErrorHandlerService.getInstance();

/**
 * Decorator for error handling on async functions
 */
export function withErrorHandling<T extends (...args: any[]) => Promise<any>>(
  fn: T,
  context: Partial<ErrorContext> = {}
): T {
  return (async (...args: Parameters<T>) => {
    try {
      return await fn(...args);
    } catch (error) {
      errorHandler.handleError(error as Error, {
        operation: fn.name,
        component: context.component,
        userId: context.userId,
        requestId: context.requestId,
        additionalData: {
          ...context.additionalData,
          args: args.length > 0 ? args : undefined,
        },
      });
      throw error;
    }
  }) as T;
}

/**
 * Safe async function wrapper that returns null on error
 */
export async function safeAsync<T>(
  fn: () => Promise<T>,
  context: Partial<ErrorContext> = {}
): Promise<T | null> {
  try {
    return await fn();
  } catch (error) {
    errorHandler.handleError(error as Error, context);
    return null;
  }
}

/**
 * Circuit breaker pattern implementation
 */
export class CircuitBreaker {
  private failures = 0;
  private lastFailureTime: Date | null = null;
  private state: 'CLOSED' | 'OPEN' | 'HALF_OPEN' = 'CLOSED';

  constructor(
    private readonly threshold: number = 5,
    private readonly timeout: number = 60000 // 1 minute
  ) {}

  async execute<T>(fn: () => Promise<T>, fallback?: () => T): Promise<T> {
    if (this.state === 'OPEN') {
      if (this.shouldAttemptReset()) {
        this.state = 'HALF_OPEN';
      } else {
        if (fallback) {
          return fallback();
        }
        throw new Error('Circuit breaker is OPEN');
      }
    }

    try {
      const result = await fn();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      if (fallback) {
        return fallback();
      }
      throw error;
    }
  }

  private onSuccess(): void {
    this.failures = 0;
    this.state = 'CLOSED';
  }

  private onFailure(): void {
    this.failures++;
    this.lastFailureTime = new Date();
    
    if (this.failures >= this.threshold) {
      this.state = 'OPEN';
    }
  }

  private shouldAttemptReset(): boolean {
    return this.lastFailureTime !== null && 
           Date.now() - this.lastFailureTime.getTime() >= this.timeout;
  }

  getState(): string {
    return this.state;
  }

  reset(): void {
    this.failures = 0;
    this.lastFailureTime = null;
    this.state = 'CLOSED';
  }
}

export default {
  errorHandler,
  withErrorHandling,
  safeAsync,
  CircuitBreaker,
  ContextualError,
};
