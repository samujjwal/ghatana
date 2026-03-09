/**
 * Error Boundary Component
 * 
 * React error boundary for graceful error handling.
 * Catches JavaScript errors anywhere in the child component tree.
 * 
 * @module ui/components
 */

import React, { Component, type ReactNode, type ErrorInfo } from 'react';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';

export interface ErrorBoundaryProps {
  /** Child components */
  children: ReactNode;
  
  /** Fallback UI */
  fallback?: (error: Error, errorInfo: ErrorInfo, reset: () => void) => ReactNode;
  
  /** Error callback */
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  
  /** Show reset button */
  showReset?: boolean;
  
  /** Show home button */
  showHome?: boolean;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

/**
 * Error Boundary Component
 * 
 * Catches and displays errors in React component tree.
 * 
 * @example
 * ```tsx
 * <ErrorBoundary
 *   onError={(error, errorInfo) => {
 *     console.error('Error caught:', error, errorInfo);
 *     // Send to error tracking service
 *   }}
 * >
 *   <YourComponent />
 * </ErrorBoundary>
 * ```
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

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return {
      hasError: true,
      error,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    this.setState({
      error,
      errorInfo,
    });

    // Call error callback
    this.props.onError?.(error, errorInfo);

    // Log to console in development
    if (process.env.NODE_ENV === 'development') {
      console.error('Error caught by boundary:', error);
      console.error('Component stack:', errorInfo.componentStack);
    }
  }

  reset = (): void => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  render(): ReactNode {
    if (this.state.hasError && this.state.error) {
      // Use custom fallback if provided
      if (this.props.fallback) {
        return this.props.fallback(
          this.state.error,
          this.state.errorInfo!,
          this.reset
        );
      }

      // Default error UI
      return (
        <div className="min-h-screen flex items-center justify-center bg-zinc-950 p-4">
          <div className="max-w-2xl w-full bg-zinc-900 rounded-lg border border-zinc-800 p-8">
            {/* Icon */}
            <div className="flex justify-center mb-6">
              <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center">
                <AlertTriangle className="w-8 h-8 text-red-500" />
              </div>
            </div>

            {/* Title */}
            <h1 className="text-2xl font-bold text-white text-center mb-2">
              Something went wrong
            </h1>

            {/* Message */}
            <p className="text-zinc-400 text-center mb-6">
              We're sorry, but something unexpected happened. The error has been logged
              and we'll look into it.
            </p>

            {/* Error details (development only) */}
            {process.env.NODE_ENV === 'development' && (
              <div className="mb-6 p-4 bg-zinc-950 rounded border border-zinc-800">
                <h3 className="text-sm font-medium text-red-400 mb-2">Error Details:</h3>
                <pre className="text-xs text-zinc-400 overflow-auto max-h-40">
                  {this.state.error.toString()}
                </pre>
                {this.state.errorInfo && (
                  <>
                    <h3 className="text-sm font-medium text-red-400 mt-4 mb-2">
                      Component Stack:
                    </h3>
                    <pre className="text-xs text-zinc-400 overflow-auto max-h-40">
                      {this.state.errorInfo.componentStack}
                    </pre>
                  </>
                )}
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-3 justify-center">
              {this.props.showReset !== false && (
                <button
                  onClick={this.reset}
                  className="flex items-center gap-2 px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors"
                >
                  <RefreshCw className="w-4 h-4" />
                  Try Again
                </button>
              )}
              {this.props.showHome !== false && (
                <button
                  onClick={() => (window.location.href = '/')}
                  className="flex items-center gap-2 px-4 py-2 bg-zinc-800 text-white rounded-lg hover:bg-zinc-700 transition-colors"
                >
                  <Home className="w-4 h-4" />
                  Go Home
                </button>
              )}
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
