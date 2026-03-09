import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * Alert severity variants
 */
export type AlertSeverity = 'info' | 'success' | 'warning' | 'error';

/**
 * Alert visual variant styles
 */
export type AlertVariant = 'filled' | 'outlined' | 'standard';

/**
 * Alert component props
 */
export interface AlertProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'title'> {
  /**
   * Severity of the alert (determines color and icon)
   * @default 'info'
   */
  severity?: AlertSeverity;
  /**
   * Visual variant of the alert
   * @default 'standard'
   */
  variant?: AlertVariant;
  /**
   * Alert title (optional)
   */
  title?: React.ReactNode;
  /**
   * Alert content
   */
  children: React.ReactNode;
  /**
   * Callback when close button is clicked
   */
  onClose?: () => void;
  /**
   * Custom icon to replace default severity icon
   */
  icon?: React.ReactNode;
  /**
   * Hide the icon entirely
   * @default false
   */
  hideIcon?: boolean;
  /**
   * Action component (e.g., button)
   */
  action?: React.ReactNode;
}

/**
 * Severity color and icon configurations
 */
const severityConfig: Record<AlertSeverity, {
  filled: string;
  outlined: string;
  standard: string;
  icon: React.ReactNode;
}> = {
  info: {
    filled: 'bg-blue-500 text-white border-blue-500',
    outlined: 'bg-transparent text-blue-700 dark:text-blue-400 border-blue-500',
    standard: 'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-400 border-l-4 border-blue-500',
    icon: (
      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  success: {
    filled: 'bg-green-500 text-white border-green-500',
    outlined: 'bg-transparent text-green-700 dark:text-green-400 border-green-500',
    standard: 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400 border-l-4 border-green-500',
    icon: (
      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  warning: {
    filled: 'bg-orange-500 text-white border-orange-500',
    outlined: 'bg-transparent text-orange-700 dark:text-orange-400 border-orange-500',
    standard: 'bg-orange-50 dark:bg-orange-900/20 text-orange-700 dark:text-orange-400 border-l-4 border-orange-500',
    icon: (
      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
      </svg>
    ),
  },
  error: {
    filled: 'bg-red-500 text-white border-red-500',
    outlined: 'bg-transparent text-red-700 dark:text-red-400 border-red-500',
    standard: 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 border-l-4 border-red-500',
    icon: (
      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
};

/**
 * Alert component for displaying important messages
 *
 * Provides visual feedback with different severity levels and variants.
 * Supports titles, custom icons, actions, and dismissal.
 *
 * @example Basic usage
 * ```tsx
 * <Alert severity="info">
 *   This is an informational message
 * </Alert>
 * ```
 *
 * @example With title and close button
 * ```tsx
 * <Alert
 *   severity="warning"
 *   title="Warning"
 *   onClose={() => console.log('closed')}
 * >
 *   This is a warning message
 * </Alert>
 * ```
 *
 * @example With custom action
 * ```tsx
 * <Alert
 *   severity="error"
 *   action={<Button size="sm">Retry</Button>}
 * >
 *   Operation failed
 * </Alert>
 * ```
 *
 * @example Filled variant
 * ```tsx
 * <Alert severity="success" variant="filled">
 *   Success! Your changes were saved.
 * </Alert>
 * ```
 */
export const Alert = React.forwardRef<HTMLDivElement, AlertProps>(
  (
    {
      severity = 'info',
      variant = 'standard',
      title,
      children,
      onClose,
      icon,
      hideIcon = false,
      action,
      className,
      ...props
    },
    ref
  ) => {
    const config = severityConfig[severity];
    const variantClasses = config[variant];
    const displayIcon = icon || config.icon;

    return (
      <div
        ref={ref}
        role="alert"
        className={cn(
          'flex items-start gap-3 p-4 rounded-lg border',
          variantClasses,
          className
        )}
        {...props}
      >
        {/* Icon */}
        {!hideIcon && (
          <div className="flex-shrink-0 mt-0.5">
            {displayIcon}
          </div>
        )}

        {/* Content */}
        <div className="flex-1 min-w-0">
          {title && (
            <div className="font-semibold text-sm mb-1">
              {title}
            </div>
          )}
          <div className="text-sm leading-relaxed">
            {children}
          </div>
        </div>

        {/* Action */}
        {action && (
          <div className="flex-shrink-0">
            {action}
          </div>
        )}

        {/* Close button */}
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            className={cn(
              'flex-shrink-0 p-1 rounded hover:bg-black/10 dark:hover:bg-white/10 transition-colors',
              variant === 'filled' && 'hover:bg-white/20'
            )}
            aria-label="Close alert"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
      </div>
    );
  }
);

Alert.displayName = 'Alert';
