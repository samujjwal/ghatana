/**
 * Async State Components
 *
 * Unified loading, empty, error, and unavailable state components
 * for consistent presentation across all async pages.
 *
 * @doc.type component
 * @doc.purpose Standardized async state presentation
 * @doc.layer shared
 * @doc.pattern State Component
 */

import React from 'react';
import {
  Loader2,
  AlertTriangle,
  Database,
  Search,
  WifiOff,
  Shield,
} from 'lucide-react';
import { cn } from '../../lib/theme';

// ---------------------------------------------------------------------------
// Loading State
// ---------------------------------------------------------------------------

interface LoadingStateProps {
  message?: string;
  className?: string;
  'data-testid'?: string;
}

export const LoadingState = React.memo(function LoadingState({
  message = 'Loading...',
  className,
  'data-testid': testId,
}: LoadingStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-3 py-12',
        className
      )}
      role="status"
      aria-live="polite"
      data-testid={testId}
    >
      <Loader2 className="h-8 w-8 animate-spin text-primary-500" aria-hidden="true" />
      <p className="text-sm text-gray-500 dark:text-gray-400">{message}</p>
    </div>
  );
});

LoadingState.displayName = 'LoadingState';

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
  'data-testid'?: string;
}

export const EmptyState = React.memo(function EmptyState({
  title,
  description,
  icon,
  action,
  className,
  'data-testid': testId,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center py-12 text-center',
        className
      )}
      role="status"
      aria-live="polite"
      data-testid={testId}
    >
      {icon ?? <Database className="h-12 w-12 text-gray-400 mb-4" aria-hidden="true" />}
      <h3 className="text-lg font-medium text-gray-900 dark:text-white">{title}</h3>
      {description && (
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400 max-w-md">
          {description}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
});

EmptyState.displayName = 'EmptyState';

// ---------------------------------------------------------------------------
// Error State
// ---------------------------------------------------------------------------

interface ErrorStateProps {
  title?: string;
  message: string;
  onRetry?: () => void;
  className?: string;
  'data-testid'?: string;
}

export const ErrorState = React.memo(function ErrorState({
  title = 'Something went wrong',
  message,
  onRetry,
  className,
  'data-testid': testId,
}: ErrorStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center py-12 text-center',
        className
      )}
      role="alert"
      data-testid={testId}
    >
      <AlertTriangle className="h-12 w-12 text-red-500 mb-4" aria-hidden="true" />
      <h3 className="text-lg font-medium text-red-700 dark:text-red-400">{title}</h3>
      <p className="mt-1 text-sm text-gray-600 dark:text-gray-400 max-w-md">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className={cn(
            'mt-4 inline-flex items-center gap-2 px-4 py-2',
            'text-sm font-medium text-primary-600 dark:text-primary-400',
            'hover:text-primary-700 dark:hover:text-primary-300',
            'rounded-lg hover:bg-primary-50 dark:hover:bg-primary-900/20',
            'transition-colors'
          )}
        >
          <Loader2 className="h-4 w-4" aria-hidden="true" />
          Try again
        </button>
      )}
    </div>
  );
});

ErrorState.displayName = 'ErrorState';

// ---------------------------------------------------------------------------
// Unavailable State
// ---------------------------------------------------------------------------

interface UnavailableStateProps {
  title: string;
  message: string;
  detail?: string;
  className?: string;
  'data-testid'?: string;
}

export const UnavailableState = React.memo(function UnavailableState({
  title,
  message,
  detail,
  className,
  'data-testid': testId,
}: UnavailableStateProps) {
  return (
    <div
      className={cn(
        'rounded-lg border border-amber-200 bg-amber-50 p-6',
        'dark:border-amber-800 dark:bg-amber-950/20',
        className
      )}
      role="status"
      data-testid={testId}
    >
      <div className="flex items-start gap-3">
        <WifiOff className="mt-0.5 h-5 w-5 flex-shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
        <div>
          <h3 className="text-sm font-medium text-amber-900 dark:text-amber-100">
            {title}
          </h3>
          <p className="mt-1 text-sm text-amber-800/80 dark:text-amber-200/80">
            {message}
          </p>
          {detail && (
            <p className="mt-2 text-xs text-amber-700/70 dark:text-amber-300/70">
              {detail}
            </p>
          )}
        </div>
      </div>
    </div>
  );
});

UnavailableState.displayName = 'UnavailableState';

// ---------------------------------------------------------------------------
// Preview State
// ---------------------------------------------------------------------------

interface PreviewStateProps {
  title?: string;
  message: string;
  className?: string;
  'data-testid'?: string;
}

export const PreviewState = React.memo(function PreviewState({
  title = 'Preview',
  message,
  className,
  'data-testid': testId,
}: PreviewStateProps) {
  return (
    <div
      className={cn(
        'rounded-lg border border-blue-200 bg-blue-50 p-6',
        'dark:border-blue-800 dark:bg-blue-950/20',
        className
      )}
      role="status"
      data-testid={testId}
    >
      <div className="flex items-start gap-3">
        <Shield className="mt-0.5 h-5 w-5 flex-shrink-0 text-blue-600 dark:text-blue-400" aria-hidden="true" />
        <div>
          <h3 className="text-sm font-medium text-blue-900 dark:text-blue-100">
            {title}
          </h3>
          <p className="mt-1 text-sm text-blue-800/80 dark:text-blue-200/80">
            {message}
          </p>
        </div>
      </div>
    </div>
  );
});

PreviewState.displayName = 'PreviewState';

// ---------------------------------------------------------------------------
// Not Found State
// ---------------------------------------------------------------------------

interface NotFoundStateProps {
  query?: string;
  onClear?: () => void;
  className?: string;
  'data-testid'?: string;
}

export const NotFoundState = React.memo(function NotFoundState({
  query,
  onClear,
  className,
  'data-testid': testId,
}: NotFoundStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center py-12 text-center',
        className
      )}
      role="status"
      data-testid={testId}
    >
      <Search className="h-12 w-12 text-gray-400 mb-4" aria-hidden="true" />
      <h3 className="text-lg font-medium text-gray-900 dark:text-white">
        No results found
      </h3>
      {query && (
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          No matches for &quot;{query}&quot;. Try adjusting your search or filters.
        </p>
      )}
      {onClear && (
        <button
          type="button"
          onClick={onClear}
          className={cn(
            'mt-4 inline-flex items-center gap-2 px-4 py-2',
            'text-sm font-medium text-primary-600 dark:text-primary-400',
            'hover:text-primary-700 dark:hover:text-primary-300',
            'rounded-lg hover:bg-primary-50 dark:hover:bg-primary-900/20',
            'transition-colors'
          )}
        >
          Clear filters
        </button>
      )}
    </div>
  );
});

NotFoundState.displayName = 'NotFoundState';
