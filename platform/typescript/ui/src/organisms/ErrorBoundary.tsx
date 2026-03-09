import React, { Component } from 'react';
import type { ReactNode } from 'react';

/**
 * Error context passed to error handler callbacks.
 * 
 * @doc.type interface
 * @doc.purpose Error information for logging and reporting
 * @doc.layer ui
 * @doc.pattern Value Object
 */
export interface ErrorContext {
  /** The error that was thrown */
  error: Error;
  /** React component stack trace */
  errorInfo: React.ErrorInfo;
  /** Additional context data */
  context?: Record<string, unknown>;
}

/**
 * Props for ErrorBoundary component.
 * 
 * @doc.type interface
 * @doc.purpose Component props for error boundary
 * @doc.layer ui
 * @doc.pattern Component Props
 */
export interface ErrorBoundaryProps {
  /** Child components to wrap with error boundary */
  children: ReactNode;
  /** Custom fallback UI to display when error occurs */
  fallback?: ReactNode | ((errorContext: ErrorContext) => ReactNode);
  /** Callback fired when error is caught */
  onError?: (errorContext: ErrorContext) => void;
  /** Whether to log errors to console in development */
  logErrors?: boolean;
  /** Whether to show error details in development */
  showErrorDetails?: boolean;
  /** Custom reset button text */
  resetButtonText?: string;
  /** Custom reload button text */
  reloadButtonText?: string;
  /** Whether to show the reset button */
  showResetButton?: boolean;
  /** Whether to show the reload button */
  showReloadButton?: boolean;
}

/**
 * Internal state for ErrorBoundary component.
 */
interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
}

/**
 * ErrorBoundary component for catching React errors.
 * 
 * <p><b>Purpose</b><br>
 * Catches JavaScript errors anywhere in the child component tree, logs those errors,
 * and displays a fallback UI instead of crashing the whole application. Essential for
 * production applications to handle unexpected errors gracefully.
 * 
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Basic usage
 * <ErrorBoundary>
 *   <App />
 * </ErrorBoundary>
 * 
 * // With custom fallback
 * <ErrorBoundary fallback={<CustomErrorUI />}>
 *   <App />
 * </ErrorBoundary>
 * 
 * // With error handler
 * <ErrorBoundary
 *   onError={(errorContext) => {
 *     // Send to error tracking service
 *     logErrorToService(errorContext);
 *   }}
 * >
 *   <App />
 * </ErrorBoundary>
 * 
 * // With function fallback for access to error details
 * <ErrorBoundary
 *   fallback={({ error, errorInfo }) => (
 *     <div>
 *       <h1>Error: {error.message}</h1>
 *       <pre>{errorInfo.componentStack}</pre>
 *     </div>
 *   )}
 * >
 *   <App />
 * </ErrorBoundary>
 * }</pre>
 * 
 * <p><b>Features</b><br>
 * - Catches errors in child component tree
 * - Provides default fallback UI with reset/reload options
 * - Supports custom fallback UI (static or function)
 * - Error callback for logging/reporting
 * - Development mode error details
 * - Reset functionality to retry rendering
 * - Reload functionality for full page refresh
 * - Customizable button text and visibility
 * 
 * <p><b>Error Handling Best Practices</b><br>
 * 1. Use multiple error boundaries for different sections of your app
 * 2. Always provide onError callback for production error reporting
 * 3. Test error boundaries with intentional errors in development
 * 4. Consider custom fallbacks for critical vs. non-critical sections
 * 5. Log errors with sufficient context for debugging
 * 
 * <p><b>What Error Boundaries Catch</b><br>
 * - Errors during rendering
 * - Errors in lifecycle methods
 * - Errors in constructors of the whole tree below them
 * 
 * <p><b>What Error Boundaries Don't Catch</b><br>
 * - Event handlers (use try-catch)
 * - Asynchronous code (use try-catch)
 * - Server-side rendering
 * - Errors thrown in the error boundary itself
 * 
 * <p><b>Architecture Role</b><br>
 * This is a platform-wide error handling component. Each product should wrap
 * its root component with this boundary and provide product-specific error
 * reporting via the onError callback.
 * 
 * @see withErrorBoundary
 * @doc.type component
 * @doc.purpose React error boundary for graceful error handling
 * @doc.layer ui
 * @doc.pattern Organism
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  /**
   * Update state when error is caught.
   * 
   * @param error The error that was thrown
   * @returns Partial state update
   */
  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return {
      hasError: true,
      error,
    };
  }

  /**
   * Called when an error is caught.
   * Logs error and calls optional error handler.
   * 
   * @param error The error that was thrown
   * @param errorInfo React error info with component stack
   */
  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    const { onError, logErrors = true } = this.props;

    // Log error to console in development
    if (logErrors && process.env.NODE_ENV === 'development') {
      console.error('ErrorBoundary caught an error:', error, errorInfo);
    }

    // Update state with error info
    this.setState({
      error,
      errorInfo,
    });

    // Call optional error callback
    if (onError) {
      const errorContext: ErrorContext = {
        error,
        errorInfo,
        context: {
          timestamp: new Date().toISOString(),
          userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'unknown',
          url: typeof window !== 'undefined' ? window.location.href : 'unknown',
        },
      };
      onError(errorContext);
    }
  }

  /**
   * Reset error state and retry rendering.
   */
  handleReset = (): void => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  /**
   * Reload the entire page.
   */
  handleReload = (): void => {
    if (typeof window !== 'undefined') {
      window.location.reload();
    }
  };

  /**
   * Render fallback UI when error occurs, otherwise render children.
   */
  render(): ReactNode {
    const {
      children,
      fallback,
      showErrorDetails = process.env.NODE_ENV === 'development',
      resetButtonText = 'Try Again',
      reloadButtonText = 'Reload Page',
      showResetButton = true,
      showReloadButton = true,
    } = this.props;

    const { hasError, error, errorInfo } = this.state;

    if (hasError && error && errorInfo) {
      const errorContext: ErrorContext = {
        error,
        errorInfo,
      };

      // Custom fallback UI (function)
      if (typeof fallback === 'function') {
        return fallback(errorContext);
      }

      // Custom fallback UI (component)
      if (fallback) {
        return fallback;
      }

      // Default fallback UI
      return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
          <div className="max-w-md w-full bg-white rounded-lg shadow-lg p-6">
            <div className="flex items-center justify-center w-12 h-12 mx-auto bg-red-100 rounded-full">
              <svg
                className="w-6 h-6 text-red-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                />
              </svg>
            </div>

            <h1 className="mt-4 text-xl font-semibold text-gray-900 text-center">
              Something went wrong
            </h1>

            <p className="mt-2 text-sm text-gray-600 text-center">
              We're sorry, but something unexpected happened. Please try again or reload the page.
            </p>

            {showErrorDetails && (
              <details className="mt-4 p-4 bg-gray-100 rounded-md">
                <summary className="text-sm font-medium text-gray-700 cursor-pointer">
                  Error Details
                </summary>
                <div className="mt-2 text-xs text-gray-600 font-mono">
                  <p className="font-semibold break-words">{error.toString()}</p>
                  {errorInfo.componentStack && (
                    <pre className="mt-2 overflow-auto max-h-60 whitespace-pre-wrap break-words">
                      {errorInfo.componentStack}
                    </pre>
                  )}
                </div>
              </details>
            )}

            {(showResetButton || showReloadButton) && (
              <div className="mt-6 flex gap-3">
                {showResetButton && (
                  <button
                    onClick={this.handleReset}
                    className="flex-1 px-4 py-2 bg-white border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
                    aria-label="Try to recover from error"
                  >
                    {resetButtonText}
                  </button>
                )}
                {showReloadButton && (
                  <button
                    onClick={this.handleReload}
                    className="flex-1 px-4 py-2 bg-blue-600 border border-transparent rounded-md text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
                    aria-label="Reload the page"
                  >
                    {reloadButtonText}
                  </button>
                )}
              </div>
            )}

            <p className="mt-4 text-xs text-gray-500 text-center">
              If this problem persists, please contact support.
            </p>
          </div>
        </div>
      );
    }

    return children;
  }
}

/**
 * Type for the withErrorBoundary HOC.
 */
export type ErrorBoundaryHOCType = <P extends object>(
  Component: React.ComponentType<P>,
  options?: {
    fallback?: ReactNode | ((errorContext: ErrorContext) => ReactNode);
    onError?: (errorContext: ErrorContext) => void;
  }
) => React.ComponentType<P>;

/**
 * Higher-Order Component that wraps a component with ErrorBoundary.
 * 
 * <p><b>Purpose</b><br>
 * Provides a convenient way to add error boundary protection to any component
 * without modifying its implementation. Useful for wrapping route components,
 * lazy-loaded components, or any component that might throw errors.
 * 
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Basic usage
 * const SafeComponent = withErrorBoundary(MyComponent);
 * 
 * // With custom fallback
 * const SafeComponent = withErrorBoundary(MyComponent, {
 *   fallback: <div>Something went wrong in MyComponent</div>
 * });
 * 
 * // With error handler
 * const SafeComponent = withErrorBoundary(MyComponent, {
 *   onError: (errorContext) => {
 *     logErrorToService(errorContext);
 *   }
 * });
 * 
 * // With function fallback
 * const SafeComponent = withErrorBoundary(MyComponent, {
 *   fallback: ({ error }) => <div>Error: {error.message}</div>
 * });
 * }</pre>
 * 
 * <p><b>Use Cases</b><br>
 * - Wrapping route components for page-level error handling
 * - Protecting lazy-loaded components during code splitting
 * - Adding error boundaries to third-party components
 * - Creating component variants with different error handling
 * 
 * @param Component The component to wrap with error boundary
 * @param options Configuration options for the error boundary
 * @returns Wrapped component with error boundary protection
 * 
 * @see ErrorBoundary
 * @doc.type function
 * @doc.purpose HOC for wrapping components with error boundary
 * @doc.layer ui
 * @doc.pattern Higher-Order Component
 */
export const withErrorBoundary: ErrorBoundaryHOCType = (Component, options = {}) => {
  const { fallback, onError } = options;

  const WrappedComponent = (props: React.ComponentProps<typeof Component>) => (
    <ErrorBoundary fallback={fallback} onError={onError}>
      <Component {...props} />
    </ErrorBoundary>
  );

  WrappedComponent.displayName = `withErrorBoundary(${Component.displayName || Component.name || 'Component'})`;

  return WrappedComponent;
};

export default ErrorBoundary;
