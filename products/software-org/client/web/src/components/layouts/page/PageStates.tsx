/**
 * Page State Components
 *
 * Consistent loading, error, and empty state components.
 *
 * @doc.type component
 * @doc.purpose Page state displays
 * @doc.layer product
 */

import React from 'react';
import { Loader2, AlertCircle, RefreshCw, LucideIcon, Inbox } from 'lucide-react';
import clsx from 'clsx';
import { Button } from '@/components/ui';
import { COLORS } from './theme';

// =============================================================================
// Loading State
// =============================================================================

export interface LoadingStateProps {
    /** Loading message */
    message?: string;
    /** Size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Additional class names */
    className?: string;
}

/**
 * LoadingState - Consistent loading indicator
 *
 * @example
 * ```tsx
 * if (isLoading) {
 *   return <LoadingState message="Loading agents..." />;
 * }
 * ```
 */
export function LoadingState({
    message = 'Loading...',
    size = 'md',
    className,
}: LoadingStateProps) {
    const iconSizes = {
        sm: 'w-6 h-6',
        md: 'w-8 h-8',
        lg: 'w-12 h-12',
    };

    const textSizes = {
        sm: 'text-sm',
        md: 'text-base',
        lg: 'text-lg',
    };

    const paddings = {
        sm: 'py-8',
        md: 'py-16',
        lg: 'py-24',
    };

    return (
        <div className={clsx(
            'flex flex-col items-center justify-center',
            paddings[size],
            className
        )}>
            <Loader2 className={clsx(
                iconSizes[size],
                'animate-spin text-blue-500'
            )} />
            <span className={clsx(
                'mt-3',
                COLORS.neutral.text,
                textSizes[size]
            )}>
                {message}
            </span>
        </div>
    );
}

// =============================================================================
// Error State
// =============================================================================

export interface ErrorStateProps {
    /** Error title */
    title?: string;
    /** Error message/description */
    message?: string;
    /** Error object (for message extraction) */
    error?: Error | unknown;
    /** Retry button handler */
    onRetry?: () => void;
    /** Retry button label */
    retryLabel?: string;
    /** Size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Additional class names */
    className?: string;
}

/**
 * ErrorState - Consistent error display
 *
 * @example
 * ```tsx
 * if (error) {
 *   return (
 *     <ErrorState
 *       title="Failed to load agents"
 *       error={error}
 *       onRetry={refetch}
 *     />
 *   );
 * }
 * ```
 */
export function ErrorState({
    title = 'Something went wrong',
    message,
    error,
    onRetry,
    retryLabel = 'Try Again',
    size = 'md',
    className,
}: ErrorStateProps) {
    const iconSizes = {
        sm: 'w-8 h-8',
        md: 'w-12 h-12',
        lg: 'w-16 h-16',
    };

    const paddings = {
        sm: 'py-8',
        md: 'py-16',
        lg: 'py-24',
    };

    const errorMessage = message || (error instanceof Error ? error.message : 'An unexpected error occurred');

    return (
        <div className={clsx(
            'flex flex-col items-center justify-center text-center',
            paddings[size],
            className
        )}>
            <AlertCircle className={clsx(
                iconSizes[size],
                'text-red-500 mb-4'
            )} />
            <h3 className={clsx(
                'font-semibold mb-2',
                COLORS.neutral.textStrong,
                size === 'sm' ? 'text-base' : 'text-lg'
            )}>
                {title}
            </h3>
            <p className={clsx(
                'mb-4 max-w-md',
                COLORS.neutral.text,
                size === 'sm' ? 'text-sm' : 'text-base'
            )}>
                {errorMessage}
            </p>
            {onRetry && (
                <Button onClick={onRetry} className="flex items-center gap-2">
                    <RefreshCw className="w-4 h-4" />
                    {retryLabel}
                </Button>
            )}
        </div>
    );
}

// =============================================================================
// Empty State
// =============================================================================

export interface EmptyStateProps {
    /** Icon to display */
    icon?: LucideIcon;
    /** Empty state title */
    title?: string;
    /** Empty state description */
    description?: string;
    /** Primary action button */
    action?: React.ReactNode;
    /** Size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Additional class names */
    className?: string;
}

/**
 * EmptyState - Consistent empty/no results display
 *
 * @example
 * ```tsx
 * if (items.length === 0) {
 *   return (
 *     <EmptyState
 *       icon={Bot}
 *       title="No agents found"
 *       description="Try adjusting your search or filters"
 *       action={<Button>Add Agent</Button>}
 *     />
 *   );
 * }
 * ```
 */
export function EmptyState({
    icon: Icon = Inbox,
    title = 'No items found',
    description,
    action,
    size = 'md',
    className,
}: EmptyStateProps) {
    const iconSizes = {
        sm: 'w-10 h-10',
        md: 'w-16 h-16',
        lg: 'w-20 h-20',
    };

    const paddings = {
        sm: 'py-8',
        md: 'py-16',
        lg: 'py-24',
    };

    return (
        <div className={clsx(
            'flex flex-col items-center justify-center text-center',
            paddings[size],
            className
        )}>
            <Icon className={clsx(
                iconSizes[size],
                'text-slate-400 dark:text-slate-600 mb-4'
            )} />
            <h3 className={clsx(
                'font-semibold mb-2',
                COLORS.neutral.text,
                size === 'sm' ? 'text-base' : 'text-lg'
            )}>
                {title}
            </h3>
            {description && (
                <p className={clsx(
                    'mb-4 max-w-md',
                    COLORS.neutral.textMuted,
                    size === 'sm' ? 'text-sm' : 'text-base'
                )}>
                    {description}
                </p>
            )}
            {action && (
                <div className="mt-2">
                    {action}
                </div>
            )}
        </div>
    );
}
