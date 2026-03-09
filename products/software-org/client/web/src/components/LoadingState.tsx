import { ReactNode } from 'react';
import { Loader2 } from 'lucide-react';

/**
 * Loading State Components
 *
 * <p><b>Purpose</b><br>
 * Reusable loading indicators for various UI states.
 * Provides consistent loading UX across the application.
 *
 * <p><b>Features</b><br>
 * - Spinner loader for inline loading
 * - Skeleton loader for content placeholders
 * - Full-page loader for route transitions
 * - Customizable sizes and messages
 *
 * @doc.type component
 * @doc.purpose Loading state UI components
 * @doc.layer platform
 * @doc.pattern LoadingState
 */

interface SpinnerProps {
    size?: 'sm' | 'md' | 'lg';
    message?: string;
    className?: string;
}

/**
 * Spinner Loading Indicator
 */
export function Spinner({ size = 'md', message, className = '' }: SpinnerProps) {
    const sizeClasses = {
        sm: 'h-4 w-4',
        md: 'h-8 w-8',
        lg: 'h-12 w-12',
    };

    return (
        <div className={`flex flex-col items-center justify-center gap-3 ${className}`}>
            <Loader2 className={`${sizeClasses[size]} animate-spin text-blue-600 dark:text-blue-400`} />
            {message && (
                <p className="text-sm text-slate-600 dark:text-neutral-400">{message}</p>
            )}
        </div>
    );
}

/**
 * Full Page Loading Screen
 */
export function PageLoader({ message = 'Loading...' }: { message?: string }) {
    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 flex items-center justify-center">
            <Spinner size="lg" message={message} />
        </div>
    );
}

/**
 * Skeleton Loader for Content Placeholders
 */
interface SkeletonProps {
    count?: number;
    height?: string;
    className?: string;
}

export function Skeleton({ count = 1, height = 'h-4', className = '' }: SkeletonProps) {
    return (
        <>
            {Array.from({ length: count }).map((_, i) => (
                <div
                    key={i}
                    className={`${height} bg-slate-200 dark:bg-slate-700 rounded animate-pulse ${className}`}
                />
            ))}
        </>
    );
}

/**
 * Alert List Skeleton Loader
 */
export function AlertListSkeleton({ count = 5 }: { count?: number }) {
    return (
        <div className="space-y-3">
            {Array.from({ length: count }).map((_, i) => (
                <div
                    key={i}
                    className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg p-4 space-y-3"
                >
                    <div className="flex items-start justify-between">
                        <div className="flex-1 space-y-2">
                            <Skeleton height="h-5" className="w-3/4" />
                            <Skeleton height="h-4" className="w-full" />
                        </div>
                        <Skeleton height="h-6" className="w-20" />
                    </div>
                    <div className="flex items-center gap-4 text-xs">
                        <Skeleton height="h-3" className="w-24" />
                        <Skeleton height="h-3" className="w-32" />
                    </div>
                </div>
            ))}
        </div>
    );
}

/**
 * Table Skeleton Loader
 */
export function TableSkeleton({ rows = 10, columns = 5 }: { rows?: number; columns?: number }) {
    return (
        <div className="space-y-2">
            {/* Header */}
            <div className="grid gap-4" style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}>
                {Array.from({ length: columns }).map((_, i) => (
                    <Skeleton key={i} height="h-4" />
                ))}
            </div>
            {/* Rows */}
            {Array.from({ length: rows }).map((_, rowIndex) => (
                <div
                    key={rowIndex}
                    className="grid gap-4"
                    style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}
                >
                    {Array.from({ length: columns }).map((_, colIndex) => (
                        <Skeleton key={colIndex} height="h-3" />
                    ))}
                </div>
            ))}
        </div>
    );
}

/**
 * Loading Overlay for Async Actions
 */
interface LoadingOverlayProps {
    isLoading: boolean;
    message?: string;
    children: ReactNode;
}

export function LoadingOverlay({ isLoading, message, children }: LoadingOverlayProps) {
    return (
        <div className="relative">
            {children}
            {isLoading && (
                <div className="absolute inset-0 bg-white/80 dark:bg-slate-900/80 backdrop-blur-sm flex items-center justify-center z-50 rounded-lg">
                    <Spinner size="md" message={message} />
                </div>
            )}
        </div>
    );
}
