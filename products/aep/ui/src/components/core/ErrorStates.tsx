/**
 * Error state components for consistent error display.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/error-handling-ui/**
 * ErrorStates — collection of error state variants.
 *
 * @doc.type component
 * @doc.purpose Error state display variants
 * @doc.layer frontend
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
import React from 'react';
import { AlertCircle, RefreshCw, XCircle, Lock, Clock, FileText } from 'lucide-react';
import {
  ApiError,
  ValidationError,
  RateLimitError,
  PermissionError,
  NotFoundError,
  NetworkError,
} from '@/lib/errors';

/**
 * ErrorBanner component props
 */
interface ErrorBannerProps {
  error: Error | null;
  onDismiss?: () => void;
  onRetry?: () => void;
  /**
   * Optional: Custom error message
   */
  message?: string;
  /**
   * Optional: Show detailed error information
   */
  showDetails?: boolean;
  className?: string;
}

/**
 * ErrorBanner component
 *
 * Displays an error in a banner format with appropriate icon and styling
 * based on the error type. Includes retry and dismiss actions.
 */
export const ErrorBanner: React.FC<ErrorBannerProps> = ({
  error,
  onDismiss,
  onRetry,
  message,
  showDetails = false,
  className = '',
}) => {
  if (!error) return null;

  const getErrorIcon = () => {
    if (error instanceof ValidationError) return FileText;
    if (error instanceof RateLimitError) return Clock;
    if (error instanceof PermissionError) return Lock;
    if (error instanceof NotFoundError) return XCircle;
    if (error instanceof NetworkError) return AlertCircle;
    return AlertCircle;
  };

  const getErrorColor = () => {
    if (error instanceof ValidationError) return 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-900 dark:text-amber-300 dark:border-amber-800';
    if (error instanceof RateLimitError) return 'bg-orange-50 text-orange-700 border-orange-200 dark:bg-orange-900 dark:text-orange-300 dark:border-orange-800';
    if (error instanceof PermissionError) return 'bg-red-50 text-red-700 border-red-200 dark:bg-red-900 dark:text-red-300 dark:border-red-800';
    if (error instanceof NotFoundError) return 'bg-gray-50 text-gray-700 border-gray-200 dark:bg-gray-900 dark:text-gray-300 dark:border-gray-800';
    if (error instanceof NetworkError) return 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-900 dark:text-blue-300 dark:border-blue-800';
    return 'bg-red-50 text-red-700 border-red-200 dark:bg-red-900 dark:text-red-300 dark:border-red-800';
  };

  const Icon = getErrorIcon();

  return (
    <div className={['flex items-start gap-3 p-4 rounded-lg border', getErrorColor(), className].join(' ')}>
      <Icon className="h-5 w-5 flex-shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium">{message || error.message || 'An error occurred'}</p>
      </div>
      <div className="flex gap-2 flex-shrink-0">
        {onRetry && (
          <button
            onClick={onRetry}
            className="p-1 hover:opacity-75 transition-opacity"
            aria-label="Retry"
          >
            <RefreshCw className="h-4 w-4" />
          </button>
        )}
        {onDismiss && (
          <button
            onClick={onDismiss}
            className="p-1 hover:opacity-75 transition-opacity"
            aria-label="Dismiss"
          >
            <XCircle className="h-4 w-4" />
          </button>
        )}
      </div>
    </div>
  );
};

/**
 * ErrorPage component props
 */
interface ErrorPageProps {
  error: Error | null;
  onRetry?: () => void;
  /**
   * Optional: Custom title
   */
  title?: string;
  /**
   * Optional: Custom subtitle
   */
  subtitle?: string;
  /**
   * Optional: Show back to home button
   */
  showHomeButton?: boolean;
  /**
   * Optional: Show detailed error information
   */
  showDetails?: boolean;
}

/**
 * ErrorPage component
 *
 * Full-page error display for critical errors that prevent normal operation.
 * Provides clear messaging and recovery actions.
 */
export const ErrorPage: React.FC<ErrorPageProps> = ({
  error,
  onRetry,
  title,
  subtitle,
  showHomeButton = true,
  showDetails = false,
}) => {
  if (!error) return null;

  const getErrorTitle = () => {
    if (error instanceof ValidationError) return 'Validation Error';
    if (error instanceof RateLimitError) return 'Rate Limit Exceeded';
    if (error instanceof PermissionError) return 'Access Denied';
    if (error instanceof NotFoundError) return 'Not Found';
    if (error instanceof NetworkError) return 'Network Error';
    return 'Error';
  };

  const getErrorSubtitle = () => {
    if (error instanceof RateLimitError && error.retryAfter) {
      return `Please try again in ${error.retryAfter} seconds.`;
    }
    if (error instanceof NetworkError) {
      return 'Please check your internet connection and try again.';
    }
    return 'Something went wrong. Please try again or contact support if the problem persists.';
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-6 bg-gray-50 dark:bg-gray-950">
      <div className="max-w-md w-full text-center">
        <div className="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-800 p-8 shadow-sm">
          <div className="flex justify-center mb-4">
            <div className="bg-red-100 dark:bg-red-900 rounded-full p-4">
              <AlertCircle className="h-8 w-8 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <h1 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
            {title || getErrorTitle()}
          </h1>
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">
            {subtitle || getErrorSubtitle()}
          </p>
          <div className="flex flex-col gap-3">
            {onRetry && (
              <button
                onClick={onRetry}
                className="w-full px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors font-medium"
              >
                Try Again
              </button>
            )}
            {showHomeButton && (
              <a
                href="/"
                className="w-full px-4 py-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transition-colors font-medium"
              >
                Go to Home
              </a>
            )}
          </div>
          {showDetails && error instanceof ApiError && error.details != null && (
            <details className="mt-6 text-left">
              <summary className="text-xs text-gray-500 cursor-pointer hover:text-gray-700">
                Error Details
              </summary>
              <pre className="mt-2 text-xs bg-gray-100 dark:bg-gray-800 p-3 rounded overflow-auto">
                {JSON.stringify(error.details, null, 2)}
              </pre>
            </details>
          )}
        </div>
      </div>
    </div>
  );
};

/**
 * EmptyState component props
 */
interface EmptyStateProps {
  /**
   * Icon to display
   */
  icon?: React.ReactNode;
  /**
   * Title of the empty state
   */
  title: string;
  /**
   * Optional: Description
   */
  description?: string;
  /**
   * Optional: Action button
   */
  action?: React.ReactNode;
  className?: string;
}

/**
 * EmptyState component
 *
 * Displays a consistent empty state when no data is available.
 */
export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  action,
  className = '',
}) => {
  const defaultIcon = (
    <div className="bg-gray-100 dark:bg-gray-800 rounded-full p-3">
      <AlertCircle className="h-6 w-6 text-gray-400" />
    </div>
  );

  return (
    <div className={['flex flex-col items-center justify-center p-8 text-center', className].join(' ')}>
      {icon || defaultIcon}
      <h3 className="text-sm font-semibold text-gray-900 dark:text-white mt-4 mb-2">
        {title}
      </h3>
      {description && (
        <p className="text-xs text-gray-600 dark:text-gray-400 max-w-xs mb-4">
          {description}
        </p>
      )}
      {action}
    </div>
  );
};
