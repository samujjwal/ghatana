/**
 * Loading Components
 * 
 * Consistent loading states and skeleton screens.
 * Provides visual feedback during data loading operations.
 * 
 * @doc.type component
 * @doc.purpose Loading states and skeleton screens
 * @doc.layer ui
 * @doc.pattern Feedback Components
 */

import React from 'react';
import type { ReactNode } from 'react';

// ============================================================================
// Spinner Component
// ============================================================================

interface SpinnerProps {
    /** Size */
    size?: 'sm' | 'md' | 'lg';
    /** Color */
    color?: 'primary' | 'secondary' | 'current';
    /** Additional className */
    className?: string;
}

/**
 * Loading spinner component
 */
export const Spinner: React.FC<SpinnerProps> = ({
    size = 'md',
    color = 'primary',
    className = '',
}) => {
    const sizeClasses = {
        sm: 'w-4 h-4',
        md: 'w-6 h-6',
        lg: 'w-8 h-8',
    };

    const colorClasses = {
        primary: 'text-primary',
        secondary: 'text-secondary',
        current: 'text-current',
    };

    return (
        <svg
            className={`animate-spin ${sizeClasses[size]} ${colorClasses[color]} ${className}`}
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            aria-hidden="true"
        >
            <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
            />
            <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            />
        </svg>
    );
};

// ============================================================================
// Skeleton Component
// ============================================================================

interface SkeletonProps {
    /** Width */
    width?: string | number;
    /** Height */
    height?: string | number;
    /** Shape */
    variant?: 'text' | 'rectangular' | 'circular';
    /** Animation */
    animation?: 'pulse' | 'wave' | 'none';
    /** Additional className */
    className?: string;
}

/**
 * Skeleton placeholder component
 */
export const Skeleton: React.FC<SkeletonProps> = ({
    width = '100%',
    height = '1rem',
    variant = 'text',
    animation = 'pulse',
    className = '',
}) => {
    const variantClasses = {
        text: 'rounded',
        rectangular: 'rounded-md',
        circular: 'rounded-full',
    };

    const animationClasses = {
        pulse: 'animate-pulse',
        wave: 'animate-shimmer',
        none: '',
    };

    const style = {
        width: typeof width === 'number' ? `${width}px` : width,
        height: typeof height === 'number' ? `${height}px` : height,
    };

    return (
        <div
            className={`bg-muted ${variantClasses[variant]} ${animationClasses[animation]} ${className}`}
            style={style}
            aria-hidden="true"
        />
    );
};

// ============================================================================
// Loading Card Skeleton
// ============================================================================

interface LoadingCardProps {
    /** Show avatar */
    showAvatar?: boolean;
    /** Number of text lines */
    textLines?: number;
    /** Show button */
    showButton?: boolean;
    /** Additional className */
    className?: string;
}

/**
 * Loading card skeleton placeholder
 */
export const LoadingCard: React.FC<LoadingCardProps> = ({
    showAvatar = false,
    textLines = 3,
    showButton = false,
    className = '',
}) => {
    return (
        <div className={`p-6 border border-surface rounded-lg ${className}`}>
            <div className="space-y-3">
                {showAvatar && (
                    <div className="flex items-center space-x-3">
                        <Skeleton variant="circular" width={40} height={40} />
                        <div className="flex-1 space-y-2">
                            <Skeleton width="40%" height={20} />
                            <Skeleton width="60%" height={16} />
                        </div>
                    </div>
                )}

                <div className="space-y-2">
                    <Skeleton width="100%" height={20} />
                    {textLines > 1 && <Skeleton width="100%" height={16} />}
                    {textLines > 2 && <Skeleton width="80%" height={16} />}
                </div>

                {showButton && (
                    <div className="flex justify-end">
                        <Skeleton width={100} height={36} variant="rectangular" />
                    </div>
                )}
            </div>
        </div>
    );
};

// ============================================================================
// Loading Table Skeleton
// ============================================================================

interface LoadingTableProps {
    /** Number of rows */
    rows?: number;
    /** Number of columns */
    columns?: number;
    /** Show header */
    showHeader?: boolean;
    /** Additional className */
    className?: string;
}

/**
 * Loading table skeleton placeholder
 */
export const LoadingTable: React.FC<LoadingTableProps> = ({
    rows = 5,
    columns = 4,
    showHeader = true,
    className = '',
}) => {
    return (
        <div className={`w-full ${className}`}>
            {showHeader && (
                <div className="border-b border-surface pb-3 mb-3">
                    <div className="grid gap-4" style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}>
                        {Array.from({ length: columns }).map((_, i) => (
                            <Skeleton key={`header-${i}`} height={20} variant="text" />
                        ))}
                    </div>
                </div>
            )}

            <div className="space-y-2">
                {Array.from({ length: rows }).map((_, rowIndex) => (
                    <div key={`row-${rowIndex}`} className="grid gap-4" style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}>
                        {Array.from({ length: columns }).map((_, colIndex) => (
                            <Skeleton key={`cell-${rowIndex}-${colIndex}`} height={16} variant="text" />
                        ))}
                    </div>
                ))}
            </div>
        </div>
    );
};

// ============================================================================
// Loading State Component
// ============================================================================

interface LoadingStateProps {
    /** Loading message */
    message?: string;
    /** Show spinner */
    showSpinner?: boolean;
    /** Size */
    size?: 'sm' | 'md' | 'lg';
    /** Center content */
    centered?: boolean;
    /** Additional content */
    children?: ReactNode;
    /** Additional className */
    className?: string;
}

/**
 * Loading state component with spinner and message
 */
export const LoadingState: React.FC<LoadingStateProps> = ({
    message = 'Loading...',
    showSpinner = true,
    size = 'md',
    centered = true,
    children,
    className = '',
}) => {
    return (
        <div className={`${centered ? 'flex flex-col items-center justify-center' : ''} ${className}`}>
            {showSpinner && <Spinner size={size} className={message ? 'mb-3' : ''} />}
            {message && (
                <p className="text-muted text-sm">
                    {message}
                </p>
            )}
            {children}
        </div>
    );
};

// ============================================================================
// Progress Indicator
// ============================================================================

interface ProgressProps {
    /** Progress value (0-100) */
    value: number;
    /** Show percentage */
    showPercentage?: boolean;
    /** Size */
    size?: 'sm' | 'md' | 'lg';
    /** Color */
    color?: 'primary' | 'success' | 'warning' | 'error';
    /** Additional className */
    className?: string;
}

/**
 * Progress bar component
 */
export const Progress: React.FC<ProgressProps> = ({
    value,
    showPercentage = false,
    size = 'md',
    color = 'primary',
    className = '',
}) => {
    const sizeClasses = {
        sm: 'h-1',
        md: 'h-2',
        lg: 'h-3',
    };

    const colorClasses = {
        primary: 'bg-primary',
        success: 'bg-success',
        warning: 'bg-warning',
        error: 'bg-error',
    };

    return (
        <div className={`w-full ${className}`}>
            <div className="flex items-center justify-between mb-1">
                {showPercentage && (
                    <span className="text-sm text-muted">
                        {Math.round(value)}%
                    </span>
                )}
            </div>
            <div className={`w-full bg-muted rounded-full ${sizeClasses[size]}`}>
                <div
                    className={`${colorClasses[color]} ${sizeClasses[size]} rounded-full transition-all duration-300 ease-out`}
                    style={{ width: `${Math.min(100, Math.max(0, value))}%` }}
                    role="progressbar"
                    aria-valuenow={value}
                    aria-valuemin={0}
                    aria-valuemax={100}
                />
            </div>
        </div>
    );
};

export default {
    Spinner,
    Skeleton,
    LoadingCard,
    LoadingTable,
    LoadingState,
    Progress,
};
