/**
 * Error Boundary Component for Flashit Web
 * Catches and handles React errors gracefully
 *
 * @doc.type component
 * @doc.purpose Error boundary with recovery and reporting
 * @doc.layer product
 * @doc.pattern ErrorBoundary
 */

import React, { Component, ReactNode, ErrorInfo } from 'react';
import { flashitWebColors, flashitWebAlpha, flashitWebGradients } from '@/styles/designTokens';
import { emitFlashItDiagnostic } from '@/diagnostics';

// ============================================================================
// Types & Interfaces
// ============================================================================

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: (error: Error, reset: () => void) => ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  resetKeys?: unknown[];
  level?: 'page' | 'section' | 'component';
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  errorCount: number;
}

// ============================================================================
// Constants
// ============================================================================

const MAX_ERROR_RESET_ATTEMPTS = 5;
const NO_ERROR_LOGS: readonly unknown[] = Object.freeze([]);

// ============================================================================
// Error Boundary Component
// ============================================================================

/**
 * ErrorBoundary catches errors in child components
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  private resetTimer: NodeJS.Timeout | null = null;

  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorCount: 0,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return {
      hasError: true,
      error,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    const { onError, level = 'page' } = this.props;
    const { errorCount } = this.state;

    // Update state with error info
    this.setState({
      errorInfo,
      errorCount: errorCount + 1,
    });

    // Log error to console in development
    if (process.env.NODE_ENV === 'development') {
      emitFlashItDiagnostic({ level: 'error', component: 'ErrorBoundary', message: 'Error boundary caught error', error, context: { componentStack: errorInfo.componentStack } });
    }

    // Log error to monitoring service
    this.logError(error, errorInfo, level);

    // Call custom error handler
    if (onError) {
      onError(error, errorInfo);
    }

    // Auto-reset after a delay for component-level errors
    if (level === 'component') {
      this.scheduleAutoReset();
    }
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps): void {
    const { resetKeys } = this.props;
    const { hasError } = this.state;

    // Reset error state if resetKeys changed
    if (
      hasError &&
      resetKeys &&
      prevProps.resetKeys &&
      !this.areKeysEqual(resetKeys, prevProps.resetKeys)
    ) {
      this.reset();
    }
  }

  componentWillUnmount(): void {
    if (this.resetTimer) {
      clearTimeout(this.resetTimer);
    }
  }

  private areKeysEqual(keys1: unknown[], keys2: unknown[]): boolean {
    return JSON.stringify(keys1) === JSON.stringify(keys2);
  }

  private logError(error: Error, errorInfo: ErrorInfo, level: string): void {
    try {
      // Send to error logging service
      const errorData = {
        message: error.message,
        stack: error.stack,
        componentStack: errorInfo.componentStack,
        level,
        timestamp: new Date().toISOString(),
        userAgent: navigator.userAgent,
        url: window.location.href,
        errorCount: this.state.errorCount,
      };

      // Store in localStorage for later sync
      const errorLogs = this.getStoredErrors();
      errorLogs.unshift(errorData);
      
      // Keep only last 50 errors
      if (errorLogs.length > 50) {
        errorLogs.splice(50);
      }

      localStorage.setItem('flashit_error_logs', JSON.stringify(errorLogs));

      // Implementation note: Send to error logging service (e.g., Sentry)
      // errorLogger.log(errorData);
    } catch (e) {
      emitFlashItDiagnostic({ level: 'error', component: 'ErrorBoundary', message: 'Failed to persist error log', error: e });
    }
  }

  private getStoredErrors(): unknown[] {
    try {
      const stored = localStorage.getItem('flashit_error_logs');
      return stored ? JSON.parse(stored) : Array.from(NO_ERROR_LOGS);
    } catch {
      return Array.from(NO_ERROR_LOGS);
    }
  }

  private scheduleAutoReset(): void {
    if (this.resetTimer) {
      clearTimeout(this.resetTimer);
    }

    this.resetTimer = setTimeout(() => {
      this.reset();
    }, 5000); // Auto-reset after 5 seconds
  }

  private reset = (): void => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });

    if (this.resetTimer) {
      clearTimeout(this.resetTimer);
      this.resetTimer = null;
    }
  };

  private handleReload = (): void => {
    window.location.reload();
  };

  render(): ReactNode {
    const { children, fallback, level = 'page' } = this.props;
    const { hasError, error, errorInfo, errorCount } = this.state;

    if (hasError && error) {
      // Use custom fallback if provided
      if (fallback) {
        return fallback(error, this.reset);
      }

      // Different UI based on level
      if (level === 'component') {
        return (
          <div className="error-component">
            <div className="error-icon">⚠️</div>
            <div className="error-message">
              <p>This component failed to load</p>
              <button onClick={this.reset} className="error-retry-btn">
                Retry
              </button>
            </div>
          </div>
        );
      }

      if (level === 'section') {
        return (
          <div className="error-section">
            <div className="error-content">
              <h3>Unable to load this section</h3>
              <p>{error.message}</p>
              <div className="error-actions">
                <button onClick={this.reset} className="btn-primary">
                  Try Again
                </button>
              </div>
            </div>
          </div>
        );
      }

      // Page-level error (default)
      return (
        <div className="error-page">
          <div className="error-container">
            <div className="error-header">
              <h1>Oops! Something went wrong</h1>
              <p className="error-subtitle">
                We're sorry for the inconvenience. An unexpected error occurred.
              </p>
            </div>

            <div className="error-details">
              <div className="error-card">
                <h3>Error Details</h3>
                <p className="error-text">{error.message}</p>
                
                {process.env.NODE_ENV === 'development' && error.stack && (
                  <details className="error-stack">
                    <summary>Stack Trace</summary>
                    <pre>{error.stack}</pre>
                  </details>
                )}

                {process.env.NODE_ENV === 'development' && errorInfo?.componentStack && (
                  <details className="error-stack">
                    <summary>Component Stack</summary>
                    <pre>{errorInfo.componentStack}</pre>
                  </details>
                )}
              </div>
            </div>

            <div className="error-actions">
              <button onClick={this.reset} className="btn-primary">
                Try Again
              </button>
              
              {errorCount >= MAX_ERROR_RESET_ATTEMPTS && (
                <button onClick={this.handleReload} className="btn-secondary">
                  Reload Page
                </button>
              )}
            </div>
          </div>

          <style jsx>{`
            .error-page {
              min-height: 100vh;
              display: flex;
              align-items: center;
              justify-content: center;
              background-color: ${flashitWebColors.neutral100};
              padding: 2rem;
            }
            .error-container {
              max-width: 800px;
              width: 100%;
              background: white;
              border-radius: 12px;
              box-shadow: 0 4px 6px ${flashitWebAlpha.shadow};
              overflow: hidden;
            }
            .error-header {
              background: ${flashitWebGradients.errorHeader};
              color: white;
              padding: 3rem 2rem;
              text-align: center;
            }
            .error-header h1 {
              margin: 0 0 1rem 0;
              font-size: 2rem;
              font-weight: 600;
            }
            .error-subtitle {
              margin: 0;
              opacity: 0.9;
              font-size: 1.1rem;
            }
            .error-details {
              padding: 2rem;
            }
            .error-card {
              background: ${flashitWebColors.slate50};
              border-radius: 8px;
              padding: 1.5rem;
              border: 1px solid ${flashitWebColors.slate200};
            }
            .error-card h3 {
              margin: 0 0 1rem 0;
              color: ${flashitWebColors.slate800};
              font-size: 1.25rem;
            }
            .error-text {
              color: ${flashitWebColors.slate500};
              margin: 0 0 1rem 0;
              font-family: 'Courier New', monospace;
              font-size: 0.95rem;
            }
            .error-stack {
              margin-top: 1rem;
            }
            .error-stack summary {
              cursor: pointer;
              color: ${flashitWebColors.indigo500};
              font-weight: 500;
              margin-bottom: 0.5rem;
            }
            .error-stack pre {
              background: ${flashitWebColors.slate800};
              color: ${flashitWebColors.slate200};
              padding: 1rem;
              border-radius: 6px;
              overflow-x: auto;
              font-size: 0.85rem;
              line-height: 1.5;
            }
            .error-actions {
              padding: 2rem;
              border-top: 1px solid ${flashitWebColors.slate200};
              display: flex;
              gap: 1rem;
              justify-content: center;
            }
            .btn-primary, .btn-secondary {
              padding: 0.75rem 2rem;
              border-radius: 8px;
              font-size: 1rem;
              font-weight: 500;
              cursor: pointer;
              transition: all 0.2s;
              border: none;
            }
            .btn-primary {
              background: ${flashitWebColors.indigo500};
              color: white;
            }
            .btn-primary:hover {
              background: ${flashitWebColors.indigo600};
              transform: translateY(-1px);
              box-shadow: 0 4px 6px ${flashitWebAlpha.indigoShadow};
            }
            .btn-secondary {
              background: white;
              color: ${flashitWebColors.indigo500};
              border: 2px solid ${flashitWebColors.indigo500};
            }
            .btn-secondary:hover {
              background: ${flashitWebColors.indigo50};
            }

            .error-section {
              padding: 2rem;
              background: ${flashitWebColors.red50};
              border-radius: 8px;
              border: 1px solid ${flashitWebColors.red200};
              margin: 1rem 0;
            }
            .error-content h3 {
              color: ${flashitWebColors.red600};
              margin: 0 0 0.5rem 0;
            }
            .error-content p {
              color: ${flashitWebColors.red700};
              margin: 0 0 1rem 0;
            }

            .error-component {
              display: flex;
              align-items: center;
              gap: 1rem;
              padding: 1rem;
              background: ${flashitWebColors.red50};
              border-radius: 6px;
              border: 1px solid ${flashitWebColors.red200};
            }
            .error-icon {
              font-size: 2rem;
            }
            .error-message {
              flex: 1;
            }
            .error-message p {
              margin: 0 0 0.5rem 0;
              color: ${flashitWebColors.red700};
              font-size: 0.95rem;
            }
            .error-retry-btn {
              background: ${flashitWebColors.red600};
              color: white;
              border: none;
              padding: 0.5rem 1rem;
              border-radius: 4px;
              cursor: pointer;
              font-size: 0.875rem;
            }
            .error-retry-btn:hover {
              background: ${flashitWebColors.red700};
            }
          `}</style>
        </div>
      );
    }

    return children;
  }
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get stored error logs
 */
export function getErrorLogs(): unknown[] {
  try {
    const stored = localStorage.getItem('flashit_error_logs');
    return stored ? JSON.parse(stored) : Array.from(NO_ERROR_LOGS);
  } catch {
    return Array.from(NO_ERROR_LOGS);
  }
}

/**
 * Clear error logs
 */
export function clearErrorLogs(): void {
  localStorage.removeItem('flashit_error_logs');
}

/**
 * withErrorBoundary HOC
 */
export function withErrorBoundary<P extends object>(
  Component: React.ComponentType<P>,
  errorBoundaryProps?: Omit<ErrorBoundaryProps, 'children'>
) {
  return function WithErrorBoundaryComponent(props: P) {
    return (
      <ErrorBoundary {...errorBoundaryProps}>
        <Component {...props} />
      </ErrorBoundary>
    );
  };
}

export default ErrorBoundary;
