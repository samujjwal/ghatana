/**
 * Error Reporting Utilities
 * 
 * Error logging and reporting for production monitoring
 * 
 * @module ui/error
 * @doc.type utility
 * @doc.purpose Error logging and reporting
 * @doc.layer ui
 */

import type { ErrorInfo } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Error severity levels
 */
export enum ErrorSeverity {
  /** Low severity - minor issues */
  LOW = 'low',
  
  /** Medium severity - notable but not critical */
  MEDIUM = 'medium',
  
  /** High severity - significant issues */
  HIGH = 'high',
  
  /** Critical severity - app-breaking issues */
  CRITICAL = 'critical',
}

/**
 * Error context metadata
 */
export interface ErrorContext {
  /** User ID if available */
  userId?: string;
  
  /** Current route/page */
  route?: string;
  
  /** Component that threw error */
  component?: string;
  
  /** Error boundary name */
  boundaryName?: string;
  
  /** Additional custom context */
  [key: string]: unknown;
}

/**
 * Error report data
 */
export interface ErrorReport {
  /** Error message */
  message: string;
  
  /** Error stack trace */
  stack?: string;
  
  /** Component stack from React */
  componentStack?: string;
  
  /** Error severity */
  severity: ErrorSeverity;
  
  /** Error context/metadata */
  context: ErrorContext;
  
  /** Timestamp */
  timestamp: number;
  
  /** Environment */
  environment: string;
  
  /** User agent */
  userAgent: string;
  
  /** URL where error occurred */
  url: string;
}

/**
 * Error reporter interface
 */
export interface ErrorReporter {
  /** Report error */
  report: (error: Error, errorInfo?: ErrorInfo, context?: ErrorContext) => void;
  
  /** Set global context */
  setContext: (context: Partial<ErrorContext>) => void;
  
  /** Set user */
  setUser: (userId: string) => void;
}

// ============================================================================
// Error Severity Classification
// ============================================================================

/**
 * Classify error severity based on error characteristics
 */
export function classifyErrorSeverity(error: Error): ErrorSeverity {
  const message = error.message.toLowerCase();
  
  // Critical errors
  if (
    message.includes('network') ||
    message.includes('connection') ||
    message.includes('timeout') ||
    message.includes('auth') ||
    message.includes('unauthorized')
  ) {
    return ErrorSeverity.CRITICAL;
  }
  
  // High severity errors
  if (
    message.includes('failed to fetch') ||
    message.includes('not found') ||
    message.includes('invalid') ||
    error.name === 'TypeError' ||
    error.name === 'ReferenceError'
  ) {
    return ErrorSeverity.HIGH;
  }
  
  // Medium severity errors
  if (
    message.includes('warning') ||
    message.includes('deprecated') ||
    error.name === 'SyntaxError'
  ) {
    return ErrorSeverity.MEDIUM;
  }
  
  // Default to medium
  return ErrorSeverity.MEDIUM;
}

// ============================================================================
// Error Report Builder
// ============================================================================

/**
 * Build error report from error and context
 */
export function buildErrorReport(
  error: Error,
  errorInfo?: ErrorInfo,
  context: ErrorContext = {},
  severity?: ErrorSeverity
): ErrorReport {
  return {
    message: error.message,
    stack: error.stack,
    componentStack: errorInfo?.componentStack ?? undefined,
    severity: severity || classifyErrorSeverity(error),
    context: {
      ...context,
      route: context.route || window.location.pathname,
    },
    timestamp: Date.now(),
    environment: process.env.NODE_ENV || 'development',
    userAgent: navigator.userAgent,
    url: window.location.href,
  };
}

// ============================================================================
// Console Error Reporter
// ============================================================================

/**
 * Console error reporter
 * Logs errors to console (development)
 */
export class ConsoleErrorReporter implements ErrorReporter {
  private globalContext: ErrorContext = {};
  
  setContext(context: Partial<ErrorContext>): void {
    this.globalContext = { ...this.globalContext, ...context };
  }
  
  setUser(userId: string): void {
    this.globalContext.userId = userId;
  }
  
  report(error: Error, errorInfo?: ErrorInfo, context: ErrorContext = {}): void {
    const report = buildErrorReport(
      error,
      errorInfo,
      { ...this.globalContext, ...context }
    );
    
    // Use different console methods based on severity
    const logMethod = report.severity === ErrorSeverity.CRITICAL 
      ? console.error 
      : console.warn;
    
    logMethod('[Error Report]', {
      severity: report.severity,
      message: report.message,
      context: report.context,
      stack: report.stack,
      componentStack: report.componentStack,
      timestamp: new Date(report.timestamp).toISOString(),
    });
  }
}

// ============================================================================
// Remote Error Reporter
// ============================================================================

/**
 * Remote error reporter
 * Sends errors to remote logging service
 */
export class RemoteErrorReporter implements ErrorReporter {
  private globalContext: ErrorContext = {};
  private endpoint: string;
  private apiKey?: string;
  
  constructor(endpoint: string, apiKey?: string) {
    this.endpoint = endpoint;
    this.apiKey = apiKey;
  }
  
  setContext(context: Partial<ErrorContext>): void {
    this.globalContext = { ...this.globalContext, ...context };
  }
  
  setUser(userId: string): void {
    this.globalContext.userId = userId;
  }
  
  async report(error: Error, errorInfo?: ErrorInfo, context: ErrorContext = {}): Promise<void> {
    const report = buildErrorReport(
      error,
      errorInfo,
      { ...this.globalContext, ...context }
    );
    
    try {
      const headers: HeadersInit = {
        'Content-Type': 'application/json',
      };
      
      if (this.apiKey) {
        headers['Authorization'] = `Bearer ${this.apiKey}`;
      }
      
      await fetch(this.endpoint, {
        method: 'POST',
        headers,
        body: JSON.stringify(report),
        // Don't await response to avoid blocking
        keepalive: true,
      });
    } catch (reportError) {
      // Fallback to console if reporting fails
      console.error('Failed to report error:', reportError);
      console.error('Original error:', report);
    }
  }
}

// ============================================================================
// Composite Error Reporter
// ============================================================================

/**
 * Composite error reporter
 * Combines multiple reporters
 */
export class CompositeErrorReporter implements ErrorReporter {
  private reporters: ErrorReporter[];
  
  constructor(reporters: ErrorReporter[]) {
    this.reporters = reporters;
  }
  
  setContext(context: Partial<ErrorContext>): void {
    this.reporters.forEach(reporter => reporter.setContext(context));
  }
  
  setUser(userId: string): void {
    this.reporters.forEach(reporter => reporter.setUser(userId));
  }
  
  report(error: Error, errorInfo?: ErrorInfo, context: ErrorContext = {}): void {
    this.reporters.forEach(reporter => {
      try {
        reporter.report(error, errorInfo, context);
      } catch (reportError) {
        console.error('Reporter failed:', reportError);
      }
    });
  }
}

// ============================================================================
// Default Error Reporter
// ============================================================================

/**
 * Create default error reporter based on environment
 */
export function createDefaultErrorReporter(): ErrorReporter {
  const reporters: ErrorReporter[] = [new ConsoleErrorReporter()];
  
  // Add remote reporter in production
  if (process.env.NODE_ENV === 'production') {
    const endpoint = process.env.VITE_ERROR_REPORTING_ENDPOINT;
    const apiKey = process.env.VITE_ERROR_REPORTING_API_KEY;
    
    if (endpoint) {
      reporters.push(new RemoteErrorReporter(endpoint, apiKey));
    }
  }
  
  return new CompositeErrorReporter(reporters);
}

// ============================================================================
// Global Error Reporter Instance
// ============================================================================

/**
 * Global error reporter instance
 * Can be configured at app initialization
 */
let globalErrorReporter: ErrorReporter = createDefaultErrorReporter();

/**
 * Set global error reporter
 */
export function setErrorReporter(reporter: ErrorReporter): void {
  globalErrorReporter = reporter;
}

/**
 * Get global error reporter
 */
export function getErrorReporter(): ErrorReporter {
  return globalErrorReporter;
}

/**
 * Report error using global reporter
 */
export function reportError(
  error: Error,
  errorInfo?: ErrorInfo,
  context?: ErrorContext
): void {
  globalErrorReporter.report(error, errorInfo, context);
}
