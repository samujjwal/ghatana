/**
 * Unified Badge Component
 * 
 * Status and label badges with consistent styling.
 * Used for status indicators, counts, and categorical labels.
 * 
 * @doc.type component
 * @doc.purpose Design system badge
 * @doc.layer design-system
 */

import React from 'react';
import { cn } from '../../lib/utils';

export type BadgeVariant =
    | 'success'   // Green - successful states
    | 'warning'   // Yellow - warning states
    | 'error'     // Red - error states
    | 'info'      // Blue - informational
    | 'neutral'   // Grey - neutral/default
    | 'primary';  // Brand color

export type BadgeSize = 'sm' | 'md';

export interface BadgeProps {
    /** Visual style variant */
    variant?: BadgeVariant;
    /** Size of the badge */
    size?: BadgeSize;
    /** Label text */
    label: string;
    /** Optional icon */
    icon?: React.ReactNode;
    /** Dot indicator instead of text */
    dot?: boolean;
    /** Additional CSS classes */
    className?: string;
}

const variantStyles: Record<BadgeVariant, string> = {
    success: cn(
        'bg-success-bg text-success-color border-success-border',
        'dark:bg-success-bg/30 dark:text-success-color dark:border-success-border'
    ),
    warning: cn(
        'bg-warning-bg text-warning-color border-warning-border',
        'dark:bg-warning-bg/30 dark:text-warning-color dark:border-warning-border'
    ),
    error: cn(
        'bg-destructive-bg text-destructive border-destructive-border',
        'dark:bg-destructive-bg/30 dark:text-destructive dark:border-destructive-border'
    ),
    info: cn(
        'bg-info-bg text-info-color border-info-border',
        'dark:bg-info-bg/30 dark:text-info-color dark:border-info-border'
    ),
    neutral: cn(
        'bg-grey-100 text-grey-700 border-grey-200',
        'dark:bg-grey-800 dark:text-grey-300 dark:border-grey-700'
    ),
    primary: cn(
        'bg-primary-50 text-primary-700 border-primary-200',
        'dark:bg-primary-900/30 dark:text-primary-400 dark:border-primary-800'
    ),
};

const sizeStyles: Record<BadgeSize, string> = {
    sm: 'px-1.5 py-0.5 text-xs gap-1',
    md: 'px-2 py-1 text-sm gap-1.5',
};

const dotSizeStyles: Record<BadgeSize, string> = {
    sm: 'w-1.5 h-1.5',
    md: 'w-2 h-2',
};

/**
 * Badge Component
 * 
 * @example
 * ```tsx
 * import { Badge } from '@/components/design-system';
 * import { CheckCircle } from 'lucide-react';
 * 
 * <Badge variant="success" label="Active" icon={<CheckCircle />} />
 * <Badge variant="error" label="Failed" />
 * <Badge variant="neutral" dot label="Draft" />
 * ```
 */
export function Badge({
    variant = 'neutral',
    size = 'md',
    label,
    icon,
    dot = false,
    className,
}: BadgeProps) {
    return (
        <span
            className={cn(
                // Base styles
                'inline-flex items-center',
                'font-semibold',
                'rounded-full',
                'border',
                'whitespace-nowrap',

                // Variant styles
                variantStyles[variant],

                // Size styles
                sizeStyles[size],

                // Custom classes
                className
            )}
        >
            {/* Dot indicator */}
            {dot && (
                <span
                    className={cn(
                        'rounded-full',
                        dotSizeStyles[size],
                        // Match background to text color
                        variant === 'success' && 'bg-success-bg',
                        variant === 'warning' && 'bg-warning-bg',
                        variant === 'error' && 'bg-destructive-bg',
                        variant === 'info' && 'bg-info-bg',
                        variant === 'neutral' && 'bg-grey-500',
                        variant === 'primary' && 'bg-primary-500'
                    )}
                />
            )}

            {/* Icon */}
            {icon && !dot && (
                <span className="flex-shrink-0 -ml-0.5">
                    {icon}
                </span>
            )}

            {/* Label */}
            <span className="truncate">{label}</span>
        </span>
    );
}

/**
 * Notification Badge (small dot badge for notifications)
 */
export interface NotificationBadgeProps {
    /** Count to display */
    count?: number;
    /** Show dot even if count is 0 */
    showZero?: boolean;
    /** Maximum count to display before showing "99+" */
    max?: number;
    /** Additional CSS classes */
    className?: string;
}

export function NotificationBadge({
    count = 0,
    showZero = false,
    max = 99,
    className,
}: NotificationBadgeProps) {
    if (count === 0 && !showZero) {
        return null;
    }

    const displayCount = count > max ? `${max}+` : count.toString();

    return (
        <span
            className={cn(
                'absolute -top-1 -right-1',
                'min-w-[18px] h-[18px] px-1',
                'flex items-center justify-center',
                'text-xs font-semibold',
                'bg-error-color text-white',
                'rounded-full',
                'shadow-sm',
                'border-2 border-white dark:border-grey-900',
                className
            )}
        >
            {count === 0 ? '' : displayCount}
        </span>
    );
}

export default Badge;
