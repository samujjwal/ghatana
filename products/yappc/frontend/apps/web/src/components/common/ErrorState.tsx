/**
 * Standardized Error State Component
 * 
 * Provides consistent error UI across the application.
 * Supports different error types, retry actions, and custom messages.
 * 
 * @doc.type component
 * @doc.purpose Error state UI
 * @doc.layer presentation
 */

import { AlertCircle, RefreshCw, XCircle } from 'lucide-react';
import { Button } from '@ghatana/ui';

export interface ErrorStateProps {
  /** Error title */
  title?: string;
  /** Error message */
  message: string;
  /** Error code (optional) */
  code?: string;
  /** Error type */
  type?: 'error' | 'warning' | 'info';
  /** Retry callback */
  onRetry?: () => void;
  /** Dismiss callback */
  onDismiss?: () => void;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Display variant */
  variant?: 'inline' | 'card' | 'banner';
  /** Custom className */
  className?: string;
}

const iconMap = {
  error: XCircle,
  warning: AlertCircle,
  info: AlertCircle,
};

const colorMap = {
  error: 'text-red-500',
  warning: 'text-yellow-500',
  info: 'text-blue-500',
};

const bgColorMap = {
  error: 'bg-red-50 dark:bg-red-950/20 border-red-200 dark:border-red-800',
  warning: 'bg-yellow-50 dark:bg-yellow-950/20 border-yellow-200 dark:border-yellow-800',
  info: 'bg-blue-50 dark:bg-blue-950/20 border-blue-200 dark:border-blue-800',
};

const sizeClasses = {
  sm: { icon: 'w-4 h-4', text: 'text-sm', padding: 'p-3' },
  md: { icon: 'w-5 h-5', text: 'text-base', padding: 'p-4' },
  lg: { icon: 'w-6 h-6', text: 'text-lg', padding: 'p-6' },
};

export function ErrorState({
  title,
  message,
  code,
  type = 'error',
  onRetry,
  onDismiss,
  size = 'md',
  variant = 'card',
  className = '',
}: ErrorStateProps) {
  const Icon = iconMap[type];
  const sizeClass = sizeClasses[size];

  if (variant === 'inline') {
    return (
      <div className={`flex items-start gap-2 ${className}`} role="alert">
        <Icon className={`${sizeClass.icon} ${colorMap[type]} flex-shrink-0`} />
        <div className="flex-1">
          {title && <p className={`${sizeClass.text} font-semibold text-foreground`}>{title}</p>}
          <p className={`${sizeClass.text} text-muted-foreground`}>{message}</p>
          {code && <p className="mt-1 text-xs text-muted-foreground">Error code: {code}</p>}
        </div>
        {onRetry && (
          <Button onClick={onRetry} variant="ghost" size="sm">
            <RefreshCw className="w-4 h-4" />
          </Button>
        )}
      </div>
    );
  }

  if (variant === 'banner') {
    return (
      <div
        className={`${bgColorMap[type]} border ${sizeClass.padding} rounded-lg ${className}`}
        role="alert"
      >
        <div className="flex items-start gap-3">
          <Icon className={`${sizeClass.icon} ${colorMap[type]} flex-shrink-0`} />
          <div className="flex-1">
            {title && <p className={`${sizeClass.text} font-semibold text-foreground`}>{title}</p>}
            <p className={`${sizeClass.text} text-muted-foreground`}>{message}</p>
            {code && <p className="mt-1 text-xs text-muted-foreground">Error code: {code}</p>}
            {onRetry && (
              <Button onClick={onRetry} variant="outline" size="sm" className="mt-3">
                <RefreshCw className="w-4 h-4 mr-2" />
                Try Again
              </Button>
            )}
          </div>
          {onDismiss && (
            <button
              onClick={onDismiss}
              className="text-muted-foreground hover:text-foreground"
              aria-label="Dismiss"
            >
              <XCircle className="w-5 h-5" />
            </button>
          )}
        </div>
      </div>
    );
  }

  // Card variant (default)
  return (
    <div
      className={`rounded-lg border border-border bg-surface ${sizeClass.padding} shadow-sm ${className}`}
      role="alert"
    >
      <div className="flex flex-col items-center text-center">
        <Icon className={`${sizeClass.icon} ${colorMap[type]} mb-3`} />
        {title && <h3 className={`${sizeClass.text} font-semibold text-foreground mb-2`}>{title}</h3>}
        <p className={`${sizeClass.text} text-muted-foreground mb-1`}>{message}</p>
        {code && <p className="text-xs text-muted-foreground mb-4">Error code: {code}</p>}
        {onRetry && (
          <Button onClick={onRetry} variant="outline" size={size}>
            <RefreshCw className="w-4 h-4 mr-2" />
            Try Again
          </Button>
        )}
      </div>
    </div>
  );
}

/**
 * Page-level error state
 */
export function PageError({
  title = 'Something went wrong',
  message,
  onRetry,
}: Pick<ErrorStateProps, 'title' | 'message' | 'onRetry'>) {
  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <ErrorState title={title} message={message} onRetry={onRetry} size="lg" variant="card" />
    </div>
  );
}

/**
 * Section-level error state
 */
export function SectionError({
  message,
  onRetry,
}: Pick<ErrorStateProps, 'message' | 'onRetry'>) {
  return (
    <div className="flex min-h-[200px] items-center justify-center p-4">
      <ErrorState message={message} onRetry={onRetry} size="md" variant="card" />
    </div>
  );
}

/**
 * Inline error message (for forms, etc.)
 */
export function InlineError({ message }: { message: string }) {
  return <ErrorState message={message} type="error" size="sm" variant="inline" />;
}
