import React, { ReactNode, ReactElement } from 'react';

/**
 * ErrorBoundary - Error catching and fallback UI component.
 *
 * <p><b>Purpose</b><br>
 * Catches JavaScript errors in child components and displays fallback UI.
 * Logs errors to console and provides recovery options.
 *
 * <p><b>Features</b><br>
 * - Error catching with fallback UI
 * - Error logging and reporting
 * - Reset functionality
 * - Dark mode support
 * - Development error details
 * - Recovery suggestions
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <ErrorBoundary onError={(error) => console.error(error)}>
 *   <MyComponent />
 * </ErrorBoundary>
 * ```
 *
 * @doc.type component
 * @doc.purpose Error boundary and fallback UI
 * @doc.layer product
 * @doc.pattern Error Handler
 */

interface ErrorBoundaryProps {
    children: ReactNode;
    onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
    fallback?: ReactElement;
    resetKeys?: Array<string | number>;
}

interface ErrorBoundaryState {
    hasError: boolean;
    error: Error | null;
    errorInfo: React.ErrorInfo | null;
}

/**
 * Class component for error boundary (React requires class component for boundary).
 * Tracks error state and provides fallback UI with recovery options.
 */
class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
    constructor(props: ErrorBoundaryProps) {
        super(props);
        this.state = {
            hasError: false,
            error: null,
            errorInfo: null,
        };
    }

    static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
        return { hasError: true, error };
    }

    componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
        this.setState({ errorInfo });
        this.props.onError?.(error, errorInfo);

        // Log to console in development
        // Note: Vite requires static access to import.meta.env properties for SSR compatibility
        const isDev = import.meta.env.MODE === 'development' ||
            (typeof process !== 'undefined' && (process as any)?.env?.NODE_ENV === 'development');

        if (isDev) {
            console.error('Error caught by boundary:', error, errorInfo);
        }
    }

    componentDidUpdate(prevProps: ErrorBoundaryProps) {
        const { resetKeys } = this.props;
        const prevResetKeys = prevProps.resetKeys;

        if (
            resetKeys &&
            prevResetKeys &&
            resetKeys.length === prevResetKeys.length &&
            resetKeys.some((key, index) => key !== prevResetKeys[index])
        ) {
            this.reset();
        }
    }

    reset = () => {
        this.setState({
            hasError: false,
            error: null,
            errorInfo: null,
        });
    };

    render() {
        const { hasError, error, errorInfo } = this.state;
        const { children, fallback } = this.props;

        if (hasError && error) {
            if (fallback) {
                return fallback;
            }

            return (
                <div className="min-h-screen bg-white dark:bg-slate-900 flex items-center justify-center p-4">
                    <div className="bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded-lg p-6 max-w-md w-full">
                        {/* Error Icon & Title */}
                        <div className="flex items-center gap-3 mb-4">
                            <span className="text-3xl">⚠️</span>
                            <h2 className="text-lg font-semibold text-red-900 dark:text-red-100">
                                Something went wrong
                            </h2>
                        </div>

                        {/* Error Message */}
                        <p className="text-red-800 dark:text-red-200 text-sm mb-4">
                            {error.message || 'An unexpected error occurred'}
                        </p>

                        {/* Development Error Details */}
                        {(import.meta.env.MODE === 'development' || (typeof process !== 'undefined' && (process as any)?.env?.NODE_ENV === 'development')) && errorInfo && (
                            <details className="mb-4 text-xs">
                                <summary className="cursor-pointer text-red-700 dark:text-red-300 font-medium mb-2">
                                    Error Details
                                </summary>
                                <pre className="bg-red-100 dark:bg-red-950 p-2 rounded text-red-900 dark:text-red-100 overflow-auto max-h-48">
                                    {errorInfo.componentStack}
                                </pre>
                            </details>
                        )}

                        {/* Recovery Suggestions */}
                        <div className="bg-red-100 dark:bg-red-900/50 rounded p-3 mb-4">
                            <p className="text-xs font-medium text-red-900 dark:text-red-100 mb-2">
                                Try these steps:
                            </p>
                            <ul className="text-xs text-red-800 dark:text-red-200 space-y-1 list-disc list-inside">
                                <li>Refresh the page</li>
                                <li>Clear your browser cache</li>
                                <li>Try again in a few moments</li>
                            </ul>
                        </div>

                        {/* Action Buttons */}
                        <div className="flex gap-3">
                            <button
                                onClick={this.reset}
                                className="flex-1 px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white font-medium rounded transition-colors"
                            >
                                Try Again
                            </button>
                            <button
                                onClick={() => window.location.href = '/'}
                                className="flex-1 px-4 py-2 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-900 dark:text-neutral-100 font-medium rounded transition-colors"
                            >
                                Go Home
                            </button>
                        </div>

                        {/* Error ID */}
                        <p className="text-xs text-red-600 dark:text-rose-400 mt-4 text-center">
                            Error ID: {Date.now()}
                        </p>
                    </div>
                </div>
            );
        }

        return children;
    }
}

export { ErrorBoundary };
export default ErrorBoundary;
