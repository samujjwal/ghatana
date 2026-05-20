/**
 * Application-level Error Boundary
 *
 * Wraps the entire application to catch unhandled runtime errors and display
 * a graceful fallback UI instead of a white-screen-of-death.
 *
 * Features:
 * - Logs error details for observability (extend `reportError` with Sentry etc.)
 * - Retry/reload actions for the user
 * - Separate behaviour for development (full stack) vs production (friendly msg)
 *
 * @doc.type component
 * @doc.purpose Global application error boundary
 * @doc.layer frontend
 * @doc.pattern Error Boundary
 */

import React from 'react';
import { emitDataCloudDiagnostic } from '../../diagnostics';

interface Props {
  children: React.ReactNode;
  /** Optional custom fallback renderer; receives the error and a reset fn. */
  fallback?: (error: Error, reset: () => void) => React.ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorId: string | null;
}

/** Simple unique ID used for correlating error reports. */
function generateErrorId(): string {
  return `err-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
}

/**
 * Send error details to an observability backend.
 * Extend this function to integrate with Sentry, Datadog, or similar.
 */
function reportError(error: Error, errorId: string, info: React.ErrorInfo): void {
  // In production, replace this console call with your error-reporting service.
  if (import.meta.env.DEV) {
    emitDataCloudDiagnostic("AppErrorBoundary", "error", "Unhandled error", {
      errorId,
      error,
      componentStack: info.componentStack,
    });
  }
  // Example Sentry integration (uncomment when configured):
  // Sentry.captureException(error, { extra: { errorId, componentStack: info.componentStack } });
}

/**
 * Application-level error boundary component.
 *
 * Mount once at the root in App.tsx:
 * ```tsx
 * <AppErrorBoundary>
 *   <RouterProvider router={router} />
 * </AppErrorBoundary>
 * ```
 */
export class AppErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null, errorId: null };
    this.handleReset = this.handleReset.bind(this);
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error, errorId: generateErrorId() };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    if (this.state.errorId) {
      reportError(error, this.state.errorId, info);
    }
  }

  handleReset(): void {
    this.setState({ hasError: false, error: null, errorId: null });
  }

  render(): React.ReactNode {
    if (!this.state.hasError || !this.state.error) {
      return this.props.children;
    }

    if (this.props.fallback) {
      return this.props.fallback(this.state.error, this.handleReset);
    }

    return (
      <DefaultErrorFallback
        error={this.state.error}
        errorId={this.state.errorId}
        onReset={this.handleReset}
      />
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Default fallback UI
// ─────────────────────────────────────────────────────────────────────────────

interface FallbackProps {
  error: Error;
  errorId: string | null;
  onReset: () => void;
}

function DefaultErrorFallback({ error, errorId, onReset }: FallbackProps): React.ReactElement {
  const isDev = import.meta.env.DEV;

  return (
    <div
      role="alert"
      aria-live="assertive"
      className="flex items-center justify-center min-h-screen bg-gray-50 dark:bg-gray-900 p-6"
    >
      <div className="w-full max-w-md text-center">
        <div className="mb-4 flex justify-center">
          {/* Warning icon — inline SVG to avoid bundle chunk on error path */}
          <svg
            aria-hidden="true"
            className="w-16 h-16 text-red-500"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={1.5}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z"
            />
          </svg>
        </div>

        <h1 className="text-2xl font-semibold text-gray-900 dark:text-white mb-2">
          Something went wrong
        </h1>

        <p className="text-gray-600 dark:text-gray-400 mb-1 text-sm">
          An unexpected error occurred. You can try to recover or reload the page.
        </p>

        {errorId && (
          <p className="text-xs text-gray-400 dark:text-gray-500 mb-4 font-mono">
            Reference: {errorId}
          </p>
        )}

        {isDev && (
          <details className="mb-4 text-left bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 rounded-lg p-3">
            <summary className="text-sm font-medium text-red-700 dark:text-red-300 cursor-pointer">
              Developer details
            </summary>
            <pre className="mt-2 text-xs text-red-600 dark:text-red-400 overflow-auto max-h-48 whitespace-pre-wrap">
              {error.message}
              {'\n\n'}
              {error.stack}
            </pre>
          </details>
        )}

        <div className="flex gap-3 justify-center">
          <button
            type="button"
            onClick={onReset}
            className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          >
            Try to recover
          </button>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="px-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-gray-400 focus:ring-offset-2"
          >
            Reload page
          </button>
        </div>
      </div>
    </div>
  );
}

export default AppErrorBoundary;
