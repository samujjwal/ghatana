/**
 * Progress Component (Tailwind CSS + Base UI)
 * 
 * A progress indicator component using Base UI Progress primitives styled with Tailwind CSS.
 * Supports linear and circular variants, determinate and indeterminate states.
 * 
 * @example
 * ```tsx
 * <ProgressTailwind
 *   value={75}
 *   max={100}
 *   label="Upload progress"
 * />
 * ```
 */

import { Progress as BaseProgress } from '@base-ui/react/progress';
import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Props for Progress component
 */
export interface ProgressProps {
  /** Current progress value */
  value?: number | null;
  /** Maximum value */
  max?: number;
  /** Label for the progress */
  label?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Color scheme */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';
  /** Variant style */
  variant?: 'linear' | 'circular';
  /** Show value label */
  showValue?: boolean;
  /** Format function for value display */
  formatValue?: (value: number, max: number) => string;
  /** Additional CSS classes */
  className?: string;
  /** Additional CSS classes for label */
  labelClassName?: string;
}

/**
 * Progress component for showing completion status
 * 
 * Built with Base UI Progress primitives and styled with Tailwind CSS.
 * Supports both determinate (with value) and indeterminate (without value) states.
 */
export const Progress = React.forwardRef<HTMLDivElement, ProgressProps>(
  (
    {
      value,
      max = 100,
      label,
      size = 'md',
      colorScheme = 'primary',
      variant = 'linear',
      showValue = false,
      formatValue = (v, m) => `${Math.round((v / m) * 100)}%`,
      className,
      labelClassName,
    },
    ref
  ) => {
    const percentage = value != null ? Math.min(Math.max((value / max) * 100, 0), 100) : null;
    const isIndeterminate = value == null;

    // Size classes for linear progress
    const linearSizeClasses = {
      sm: 'h-1',
      md: 'h-2',
      lg: 'h-3',
    };

    // Size classes for circular progress
    const circularSizeClasses = {
      sm: 'h-8 w-8',
      md: 'h-12 w-12',
      lg: 'h-16 w-16',
    };

    // Color scheme classes
    const colorClasses = {
      primary: 'bg-primary-500',
      secondary: 'bg-secondary-500',
      success: 'bg-success-500',
      error: 'bg-error-500',
      warning: 'bg-warning-500',
      grey: 'bg-grey-500',
    };

    if (variant === 'circular') {
      const strokeWidth = size === 'sm' ? 3 : size === 'md' ? 4 : 5;
      const radius = 50 - strokeWidth / 2;
      const circumference = 2 * Math.PI * radius;
      const strokeDashoffset = percentage != null ? circumference - (percentage / 100) * circumference : 0;

      return (
        <div className={cn('inline-flex flex-col items-center gap-2', className)}>
          {label && (
            <span
              className={cn(
                'text-sm font-medium text-grey-700',
                labelClassName
              )}
            >
              {label}
            </span>
          )}

          <BaseProgress.Root ref={ref} value={value ?? null} max={max}>
            <div className={cn('relative', circularSizeClasses[size])}>
              <svg className="h-full w-full -rotate-90 transform">
                {/* Background circle */}
                <circle
                  cx="50"
                  cy="50"
                  r={radius}
                  stroke="currentColor"
                  strokeWidth={strokeWidth}
                  fill="none"
                  className="text-grey-200"
                />

                {/* Progress circle */}
                {isIndeterminate ? (
                  <circle
                    cx="50"
                    cy="50"
                    r={radius}
                    stroke="currentColor"
                    strokeWidth={strokeWidth}
                    fill="none"
                    strokeDasharray={circumference}
                    className={cn(
                      'animate-spin-slow transition-all',
                      colorClasses[colorScheme]
                    )}
                    style={{
                      strokeDashoffset: circumference * 0.75,
                    }}
                  />
                ) : (
                  <circle
                    cx="50"
                    cy="50"
                    r={radius}
                    stroke="currentColor"
                    strokeWidth={strokeWidth}
                    fill="none"
                    strokeDasharray={circumference}
                    strokeDashoffset={strokeDashoffset}
                    className={cn(
                      'transition-all duration-300',
                      colorClasses[colorScheme]
                    )}
                  />
                )}
              </svg>

              {/* Value text in center */}
              {showValue && !isIndeterminate && (
                <div className="absolute inset-0 flex items-center justify-center">
                  <span className="text-xs font-semibold text-grey-700">
                    {formatValue(value!, max)}
                  </span>
                </div>
              )}
            </div>
          </BaseProgress.Root>
        </div>
      );
    }

    // Linear variant
    return (
      <div className={cn('w-full', className)}>
        <BaseProgress.Root ref={ref} value={value ?? null} max={max}>
          {(label || showValue) && (
            <div className="mb-2 flex items-center justify-between">
              {label && (
                <BaseProgress.Label
                  className={cn(
                    'text-sm font-medium text-grey-700',
                    labelClassName
                  )}
                >
                  {label}
                </BaseProgress.Label>
              )}
              {showValue && !isIndeterminate && (
                <span className="text-sm font-medium text-grey-700">
                  {formatValue(value!, max)}
                </span>
              )}
            </div>
          )}

          <BaseProgress.Track
            className={cn(
              'relative w-full overflow-hidden rounded-full bg-grey-200',
              linearSizeClasses[size]
            )}
          >
            <BaseProgress.Indicator
              className={cn(
                'h-full transition-all duration-300',
                colorClasses[colorScheme],
                isIndeterminate && 'animate-progress-indeterminate'
              )}
              style={{
                width: isIndeterminate ? '40%' : `${percentage}%`,
              }}
            />
          </BaseProgress.Track>
        </BaseProgress.Root>
      </div>
    );
  }
);
Progress.displayName = 'Progress';

// Export with Tailwind suffix for consistency
export { Progress as ProgressTailwind };
