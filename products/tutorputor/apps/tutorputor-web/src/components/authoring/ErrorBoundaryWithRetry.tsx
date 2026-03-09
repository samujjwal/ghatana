/**
 * Error Boundary with Retry Logic
 * 
 * Enhanced error boundary component with intelligent retry strategies,
 * error reporting, and user-friendly recovery options.
 *
 * @doc.type component
 * @doc.purpose Error handling and recovery UI
 * @doc.layer product
 * @doc.pattern Error Handling
 */

import React, { Component, ErrorInfo, ReactNode } from 'react';

interface ErrorBoundaryWithRetryState {
    hasError: boolean;
    error: Error | null;
    errorInfo: ErrorInfo | null;
    retryCount: number;
    isRetrying: boolean;
}

interface ErrorBoundaryWithRetryProps {
    children: ReactNode;
    fallback?: ReactNode;
    onError?: (error: Error, errorInfo: ErrorInfo) => void;
    maxRetries?: number;
    retryDelay?: number;
    showErrorDetails?: boolean;
}

class ErrorBoundaryWithRetry extends Component<ErrorBoundaryWithRetryProps, ErrorBoundaryWithRetryState> {
    private retryTimeout: NodeJS.Timeout | null = null;

    constructor(props: ErrorBoundaryWithRetryProps) {
        super(props);

        this.state = {
            hasError: false,
            error: null,
            errorInfo: null,
            retryCount: 0,
            isRetrying: false
        };
    }

    static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryWithRetryState> {
        return {
            hasError: true,
            error
        };
    }

    componentDidCatch(error: Error, errorInfo: ErrorInfo) {
        this.setState({
            error,
            errorInfo
        });

        // Report error to monitoring service
        this.reportError(error, errorInfo);

        // Call custom error handler
        if (this.props.onError) {
            this.props.onError(error, errorInfo);
        }
    }

    componentWillUnmount() {
        if (this.retryTimeout) {
            clearTimeout(this.retryTimeout);
        }
    }

    private reportError = (error: Error, errorInfo: ErrorInfo) => {
        try {
            // Send error to monitoring service
            const errorReport = {
                message: error.message,
                stack: error.stack,
                componentStack: errorInfo.componentStack,
                timestamp: new Date().toISOString(),
                userAgent: navigator.userAgent,
                url: window.location.href
            };

            // In production, this would send to your error monitoring service
            console.error('[ErrorBoundary] Error reported:', errorReport);

            // Example: Send to error tracking service
            // errorTrackingService.captureException(error, { extra: errorInfo });
        } catch (reportingError) {
            console.error('[ErrorBoundary] Failed to report error:', reportingError);
        }
    };

    private handleRetry = () => {
        const { maxRetries = 3, retryDelay = 1000 } = this.props;
        const { retryCount } = this.state;

        if (retryCount >= maxRetries) {
            console.warn('[ErrorBoundary] Max retries reached');
            return;
        }

        this.setState({ isRetrying: true });

        this.retryTimeout = setTimeout(() => {
            this.setState({
                hasError: false,
                error: null,
                errorInfo: null,
                retryCount: retryCount + 1,
                isRetrying: false
            });
        }, retryDelay * Math.pow(2, retryCount)); // Exponential backoff
    };

    private handleReset = () => {
        this.setState({
            hasError: false,
            error: null,
            errorInfo: null,
            retryCount: 0,
            isRetrying: false
        });
    };

    private getErrorSeverity = (error: Error): 'low' | 'medium' | 'high' => {
        // Classify error severity based on error message or type
        const message = error.message.toLowerCase();

        if (message.includes('network') || message.includes('fetch')) {
            return 'medium';
        }

        if (message.includes('permission') || message.includes('unauthorized')) {
            return 'high';
        }

        return 'low';
    };

    private getRetrySuggestion = (error: Error): string => {
        const message = error.message.toLowerCase();

        if (message.includes('network') || message.includes('fetch')) {
            return 'Check your internet connection and try again.';
        }

        if (message.includes('permission') || message.includes('unauthorized')) {
            return 'Please refresh the page and log in again.';
        }

        if (message.includes('memory') || message.includes('out of memory')) {
            return 'Try refreshing the page to free up memory.';
        }

        return 'Try refreshing the page or contact support if the problem persists.';
    };

    render() {
        const { hasError, error, errorInfo, retryCount, isRetrying } = this.state;
        const { children, fallback, maxRetries = 3, showErrorDetails = false } = this.props;

        if (hasError && error) {
            // Custom fallback provided
            if (fallback) {
                return fallback;
            }

            const severity = this.getErrorSeverity(error);
            const retrySuggestion = this.getRetrySuggestion(error);
            const canRetry = retryCount < maxRetries;

            return (
                <div className="error-boundary-fallback p-6 max-w-2xl mx-auto">
                    <div className={`rounded-lg p-6 ${severity === 'high' ? 'bg-red-50 border-red-200' :
                            severity === 'medium' ? 'bg-yellow-50 border-yellow-200' :
                                'bg-gray-50 border-gray-200'
                        } border`}>
                        {/* Error Header */}
                        <div className="flex items-center mb-4">
                            <div className={`rounded-full p-2 mr-3 ${severity === 'high' ? 'bg-red-100' :
                                    severity === 'medium' ? 'bg-yellow-100' :
                                        'bg-gray-100'
                                }`}>
                                {severity === 'high' ? '⚠️' : severity === 'medium' ? '⚡' : '❌'}
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-gray-900">
                                    Something went wrong
                                </h2>
                                <p className="text-sm text-gray-600">
                                    {severity === 'high' ? 'Critical error occurred' :
                                        severity === 'medium' ? 'Temporary issue' :
                                            'Unexpected error'}
                                </p>
                            </div>
                        </div>

                        {/* Error Message */}
                        <div className="mb-4">
                            <p className="text-gray-700 mb-2">
                                {error.message || 'An unexpected error occurred while loading this component.'}
                            </p>
                            <p className="text-sm text-gray-600">
                                {retrySuggestion}
                            </p>
                        </div>

                        {/* Retry Actions */}
                        <div className="flex gap-3 mb-4">
                            {canRetry && (
                                <button
                                    onClick={this.handleRetry}
                                    disabled={isRetrying}
                                    className={`px-4 py-2 rounded-md text-sm font-medium ${isRetrying
                                            ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                            : 'bg-blue-600 text-white hover:bg-blue-700'
                                        }`}
                                >
                                    {isRetrying ? (
                                        <span className="flex items-center">
                                            <svg className="animate-spin -ml-1 mr-2 h-4 w-4" fill="none" viewBox="0 0 24 24">
                                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                            </svg>
                                            Retrying...
                                        </span>
                                    ) : (
                                        `Retry (${maxRetries - retryCount} attempts left)`
                                    )}
                                </button>
                            )}

                            <button
                                onClick={this.handleReset}
                                className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-300"
                            >
                                Reset
                            </button>

                            <button
                                onClick={() => window.location.reload()}
                                className="px-4 py-2 border border-gray-300 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-50"
                            >
                                Refresh Page
                            </button>
                        </div>

                        {/* Retry Progress */}
                        {retryCount > 0 && (
                            <div className="mb-4">
                                <div className="flex items-center justify-between text-sm text-gray-600 mb-1">
                                    <span>Retry attempts</span>
                                    <span>{retryCount}/{maxRetries}</span>
                                </div>
                                <div className="w-full bg-gray-200 rounded-full h-2">
                                    <div
                                        className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                                        style={{ width: `${(retryCount / maxRetries) * 100}%` }}
                                    />
                                </div>
                            </div>
                        )}

                        {/* Error Details (Development) */}
                        {showErrorDetails && errorInfo && (
                            <details className="mt-4">
                                <summary className="cursor-pointer text-sm font-medium text-gray-700 hover:text-gray-900">
                                    Error Details
                                </summary>
                                <div className="mt-2 p-3 bg-gray-100 rounded text-xs font-mono">
                                    <div className="mb-2">
                                        <strong>Error:</strong>
                                        <pre className="whitespace-pre-wrap">{error.stack}</pre>
                                    </div>
                                    <div>
                                        <strong>Component Stack:</strong>
                                        <pre className="whitespace-pre-wrap">{errorInfo.componentStack}</pre>
                                    </div>
                                </div>
                            </details>
                        )}

                        {/* Support Information */}
                        <div className="mt-4 pt-4 border-t border-gray-200">
                            <p className="text-xs text-gray-500">
                                Error ID: {Date.now().toString(36)} |
                                Retry count: {retryCount} |
                                Severity: {severity}
                            </p>
                            <p className="text-xs text-gray-500 mt-1">
                                If this problem persists, please contact support with the error ID above.
                            </p>
                        </div>
                    </div>
                </div>
            );
        }

        return children;
    }
}

// Hook for using error boundary in functional components
export const useErrorBoundary = () => {
    const [error, setError] = React.useState<Error | null>(null);

    const resetError = React.useCallback(() => {
        setError(null);
    }, []);

    const captureError = React.useCallback((error: Error) => {
        setError(error);
    }, []);

    return {
        error,
        resetError,
        captureError
    };
};

// Higher-order component for error boundary
export const withErrorBoundary = <P extends object>(
    Component: React.ComponentType<P>,
    errorBoundaryProps?: Omit<ErrorBoundaryWithRetryProps, 'children'>
) => {
    const WrappedComponent = (props: P) => (
        <ErrorBoundaryWithRetry {...errorBoundaryProps}>
            <Component {...props} />
        </ErrorBoundaryWithRetry>
    );

    WrappedComponent.displayName = `withErrorBoundary(${Component.displayName || Component.name})`;
    return WrappedComponent;
};

export default ErrorBoundaryWithRetry;
