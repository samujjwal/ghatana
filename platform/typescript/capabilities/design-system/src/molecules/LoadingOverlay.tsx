import React from 'react';
import { clsx } from 'clsx';

export interface LoadingOverlayProps {
    /**
     * Whether the overlay is visible
     */
    isLoading: boolean;
    /**
     * Loading text to display
     * @default 'Loading...'
     */
    text?: string;
    /**
     * Whether to blur the background
     * @default true
     */
    blur?: boolean;
    /**
     * Overlay opacity
     * @default 'medium'
     */
    opacity?: 'light' | 'medium' | 'dark';
    /**
     * Spinner size
     * @default 'md'
     */
    spinnerSize?: 'sm' | 'md' | 'lg';
    /**
     * Additional CSS classes
     */
    className?: string;
}

/**
 * LoadingOverlay component for blocking UI during async operations.
 *
 * @example
 * ```tsx
 * <div className="relative">
 *   <LoadingOverlay isLoading={isSubmitting} text="Saving changes..." />
 *   <form>
 *     {/* Form content *\/}
 *   </form>
 * </div>
 * ```
 */
export const LoadingOverlay: React.FC<LoadingOverlayProps> = ({
    isLoading,
    text = 'Loading...',
    blur = true,
    opacity = 'medium',
    spinnerSize = 'md',
    className,
}) => {
    if (!isLoading) return null;

    const opacityClasses = {
        light: 'bg-white/60',
        medium: 'bg-white/80',
        dark: 'bg-white/95',
    };

    const spinnerSizeClasses = {
        sm: 'h-8 w-8 border-2',
        md: 'h-12 w-12 border-3',
        lg: 'h-16 w-16 border-4',
    };

    return (
        <div
            className={clsx(
                'absolute inset-0 z-50 flex flex-col items-center justify-center',
                opacityClasses[opacity],
                blur && 'backdrop-blur-sm',
                className
            )}
            role="status"
            aria-live="polite"
            aria-label={text}
        >
            <div
                className={clsx(
                    'animate-spin rounded-full border-primary-600 border-t-transparent',
                    spinnerSizeClasses[spinnerSize]
                )}
            />
            {text && (
                <p className="mt-4 text-sm font-medium text-gray-700">{text}</p>
            )}
        </div>
    );
};

LoadingOverlay.displayName = 'LoadingOverlay';
