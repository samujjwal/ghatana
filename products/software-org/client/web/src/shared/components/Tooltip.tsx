import React, { useState } from 'react';
import { clsx } from 'clsx';

/**
 * Tooltip component props
 *
 * @doc.type interface
 * @doc.purpose Tooltip component props
 * @doc.layer product
 * @doc.pattern Props
 */
export interface TooltipProps {
    /** Tooltip content text */
    content: string;
    /** Position of tooltip relative to trigger element */
    position?: 'top' | 'bottom' | 'left' | 'right';
    /** Child element that triggers the tooltip */
    children: React.ReactNode;
    /** Additional CSS classes */
    className?: string;
    /** Delay before showing tooltip in ms */
    delay?: number;
}

/**
 * Tooltip component
 *
 * <p><b>Purpose</b><br>
 * Provides contextual help text on hover for buttons, icons, and interactive elements.
 * Shows/hides tooltip with smooth animations and proper positioning.
 *
 * <p><b>Features</b><br>
 * - Smooth fade in/out animations
 * - Configurable position (top, bottom, left, right)
 * - Configurable delay before showing
 * - Dark mode support
 * - Accessibility friendly
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <Tooltip content="Go back to previous page" position="bottom">
 *   <button>Back</button>
 * </Tooltip>
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Contextual help tooltip
 * @doc.layer product
 * @doc.pattern Tooltip
 */
export const Tooltip: React.FC<TooltipProps> = ({
    content,
    position = 'top',
    children,
    className = '',
    delay = 200,
}) => {
    const [isVisible, setIsVisible] = useState(false);
    const [timeoutId, setTimeoutId] = useState<NodeJS.Timeout | null>(null);

    const handleMouseEnter = () => {
        // Cancel any pending hide
        if (timeoutId) {
            clearTimeout(timeoutId);
            setTimeoutId(null);
        }
        // Show tooltip after delay
        const id = setTimeout(() => {
            setIsVisible(true);
        }, delay);
        setTimeoutId(id);
    };

    const handleMouseLeave = () => {
        if (timeoutId) {
            clearTimeout(timeoutId);
            setTimeoutId(null);
        }
        setIsVisible(false);
    };

    const positionClasses = {
        top: 'bottom-full mb-2 left-1/2 transform -translate-x-1/2',
        bottom: 'top-full mt-2 left-1/2 transform -translate-x-1/2',
        left: 'right-full mr-2 top-1/2 transform -translate-y-1/2',
        right: 'left-full ml-2 top-1/2 transform -translate-y-1/2',
    };

    const arrowClasses = {
        top: 'bottom-0 left-1/2 transform -translate-x-1/2 translate-y-1/2 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent',
        bottom: 'top-0 left-1/2 transform -translate-x-1/2 -translate-y-1/2 border-l-4 border-r-4 border-b-4 border-l-transparent border-r-transparent',
        left: 'left-0 top-1/2 transform translate-x-1/2 -translate-y-1/2 border-t-4 border-b-4 border-l-4 border-t-transparent border-b-transparent',
        right: 'right-0 top-1/2 transform -translate-x-1/2 -translate-y-1/2 border-t-4 border-b-4 border-r-4 border-t-transparent border-b-transparent',
    };

    const arrowBorderColor = {
        top: 'border-t-slate-900 dark:border-t-slate-700',
        bottom: 'border-b-slate-900 dark:border-b-slate-700',
        left: 'border-l-slate-900 dark:border-l-slate-700',
        right: 'border-r-slate-900 dark:border-r-slate-700',
    };

    return (
        <div
            className="relative inline-block"
            onMouseEnter={handleMouseEnter}
            onMouseLeave={handleMouseLeave}
        >
            {children}

            {isVisible && (
                <div
                    className={clsx(
                        'absolute z-50 px-3 py-2 text-sm font-medium text-white',
                        'bg-slate-900 dark:bg-neutral-700 rounded-lg shadow-lg',
                        'whitespace-nowrap pointer-events-none',
                        'transition-opacity duration-200 opacity-100',
                        positionClasses[position],
                        className
                    )}
                >
                    {content}
                    <div
                        className={clsx(
                            'absolute w-0 h-0',
                            arrowClasses[position],
                            arrowBorderColor[position]
                        )}
                    />
                </div>
            )}
        </div>
    );
};

export default Tooltip;
