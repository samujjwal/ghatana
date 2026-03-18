/**
 * Card Component
 * 
 * Consistent card component with multiple variants, sizes, and states.
 * Replaces inconsistent card patterns throughout the application.
 * 
 * @doc.type component
 * @doc.purpose Standardized card with variants and accessibility
 * @doc.layer ui
 * @doc.pattern Presentational Component
 */

import React, { forwardRef } from 'react';
import type { HTMLAttributes, ReactNode } from 'react';

type CardVariant = 'default' | 'elevated' | 'outlined' | 'filled';
type CardSize = 'sm' | 'md' | 'lg';
type CardState = 'default' | 'hover' | 'pressed' | 'disabled';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
    /** Card content */
    children: ReactNode;
    /** Visual variant */
    variant?: CardVariant;
    /** Card size */
    size?: CardSize;
    /** Card state */
    state?: CardState;
    /** Header content */
    header?: ReactNode;
    /** Footer content */
    footer?: ReactNode;
    /** Clickable card */
    clickable?: boolean;
    /** Full width */
    fullWidth?: boolean;
    /** Custom padding */
    padding?: boolean;
}

/**
 * Standardized Card Component
 * 
 * Provides consistent styling, proper states, and accessibility.
 */
export const Card = forwardRef<HTMLDivElement, CardProps>(({
    children,
    variant = 'default',
    size = 'md',
    state = 'default',
    header,
    footer,
    clickable = false,
    fullWidth = false,
    padding = true,
    className = '',
    ...props
}, ref) => {
    // Variant classes
    const variantClasses = {
        default: [
            'bg-surface',
            'border',
            'border-surface',
        ],
        elevated: [
            'bg-surface-elevated',
            'border',
            'border-surface-elevated',
            'shadow-sm',
            'hover:shadow-md',
            'transition-shadow',
            'duration-200',
        ],
        outlined: [
            'bg-surface',
            'border-2',
            'border-primary',
        ],
        filled: [
            'bg-surface-subtle',
            'border',
            'border-transparent',
        ],
    };

    // Size classes
    const sizeClasses = {
        sm: padding ? ['p-4'] : [],
        md: padding ? ['p-6'] : [],
        lg: padding ? ['p-8'] : [],
    };

    // State classes
    const stateClasses = {
        default: [],
        hover: clickable ? [
            'hover:shadow-md',
            'hover:border-primary-light',
            'transition-all',
            'duration-200',
        ] : [],
        pressed: clickable ? [
            'active:scale-[0.98]',
            'active:shadow-sm',
            'transition-transform',
            'duration-100',
        ] : [],
        disabled: [
            'opacity-50',
            'cursor-not-allowed',
        ],
    };

    // Interactive classes
    const interactiveClasses = clickable ? [
        'cursor-pointer',
        'focus:outline-none',
        'focus:ring-2',
        'focus:ring-primary',
        'focus:ring-offset-2',
        'rounded-lg',
    ] : ['rounded-lg'];

    // Base classes
    const baseClasses = [
        'relative',
        'overflow-hidden',
        'transition-all',
        'duration-200',
    ];

    // Combine all classes
    const cardClasses = [
        ...baseClasses,
        ...variantClasses[variant],
        ...sizeClasses[size],
        ...stateClasses[state],
        ...interactiveClasses,
        fullWidth ? 'w-full' : '',
        className,
    ].filter(Boolean).join(' ');

    return (
        <div
            ref={ref}
            className={cardClasses}
            role={clickable ? 'button' : undefined}
            tabIndex={clickable ? 0 : undefined}
            onKeyDown={clickable ? (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    e.currentTarget.click();
                }
            } : undefined}
            {...props}
        >
            {/* Header */}
            {header && (
                <div className="card-header">
                    {header}
                </div>
            )}

            {/* Content */}
            <div className="card-body">
                {children}
            </div>

            {/* Footer */}
            {footer && (
                <div className="card-footer">
                    {footer}
                </div>
            )}
        </div>
    );
});

Card.displayName = 'Card';

export default Card;
