/**
 * Error Boundary Component
 *
 * <p><b>Purpose</b><br>
 * Catches JavaScript errors anywhere in the child component tree,
 * logs errors, and displays a fallback UI instead of crashing.
 *
 * <p><b>Features</b><br>
 * - Graceful error handling with fallback UI
 * - Error logging to console (integrates with Sentry in production)
 * - Reset functionality to recover from errors
 * - Customizable fallback UI
 *
 * @doc.type component
 * @doc.purpose Production-grade error boundary
 * @doc.layer platform
 * @doc.pattern ErrorBoundary
 *
 * @package @ghatana/software-org-web
 */

import React, { Component, ErrorInfo, ReactNode } from 'react';
import { Card, Button, Box } from '@ghatana/design-system';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';
import { captureError } from '@/lib/sentry';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

/**
 * Error Boundary Component
 *
 * Catches JavaScript errors anywhere in the child component tree.
 *
 * @example
 * ```tsx
 * <ErrorBoundary>
 *   <App />
 * </ErrorBoundary>
 * ```
 */
export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Log error to console
    console.error('Error caught by boundary:', error, errorInfo);

    this.setState({
      error,
      errorInfo,
    });

    // Send to Sentry
    captureError(error, {
      react: {
        componentStack: errorInfo.componentStack,
      },
    });
  }

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-slate-900 p-6">
          <Card className="max-w-2xl w-full">
            <Box className="p-8">
              <div className="text-center mb-6">
                <div className="text-6xl mb-4">⚠️</div>
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                  Oops! Something went wrong
                </h1>
                <p className="text-slate-600 dark:text-neutral-400">
                  We're sorry for the inconvenience. The error has been logged
                  and we'll look into it.
                </p>
              </div>

              {this.state.error && (
                <div className="mb-6">
                  <details className="bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded-lg p-4">
                    <summary className="cursor-pointer font-semibold text-red-900 dark:text-red-300 mb-2">
                      Error Details
                    </summary>
                    <div className="text-sm font-mono text-red-800 dark:text-red-300 whitespace-pre-wrap">
                      {this.state.error.toString()}
                    </div>
                    {this.state.errorInfo && (
                      <div className="mt-2 text-xs font-mono text-red-700 dark:text-rose-400 whitespace-pre-wrap overflow-auto max-h-48">
                        {this.state.errorInfo.componentStack}
                      </div>
                    )}
                  </details>
                </div>
              )}

              <div className="flex gap-4 justify-center">
                <Button
                  variant="primary"
                  size="md"
                  onClick={this.handleReset}
                >
                  Try Again
                </Button>
                <Button
                  variant="outline"
                  size="md"
                  onClick={() => window.location.href = '/'}
                >
                  Go to Dashboard
                </Button>
              </div>
            </Box>
          </Card>
        </div>
      );
    }

    return this.props.children;
  }
}

/**
 * Hook for handling async errors in functional components
 */
export function useErrorHandler() {
  const [error, setError] = React.useState<Error | null>(null);

  if (error) {
    throw error;
  }

  return setError;
}
