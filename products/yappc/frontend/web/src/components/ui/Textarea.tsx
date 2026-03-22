/**
 * Textarea Component
 * 
 * Consistent textarea component with validation states and accessibility.
 * Extends the Input component for multi-line text input.
 * 
 * @doc.type component
 * @doc.purpose Standardized textarea with validation and accessibility
 * @doc.layer ui
 * @doc.pattern Form Component
 */

import React, { forwardRef } from 'react';
import type { TextareaHTMLAttributes, ReactNode } from 'react';

type TextareaSize = 'sm' | 'md' | 'lg';
type TextareaState = 'default' | 'error' | 'success' | 'warning';

interface TextareaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'size'> {
    /** Textarea label */
    label?: string;
    /** Helper text below textarea */
    helperText?: string;
    /** Error message */
    error?: string;
    /** Success message */
    success?: string;
    /** Warning message */
    warning?: string;
    /** Textarea size */
    size?: TextareaSize;
    /** Textarea state */
    state?: TextareaState;
    /** Character count */
    showCharCount?: boolean;
    /** Maximum characters */
    maxLength?: number;
    /** Resize behavior */
    resize?: 'none' | 'vertical' | 'horizontal' | 'both';
    /** Full width */
    fullWidth?: boolean;
}

/**
 * Standardized Textarea Component
 * 
 * Provides consistent styling, proper states, validation feedback, and accessibility.
 */
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(({
    label,
    helperText,
    error,
    success,
    warning,
    size = 'md',
    state,
    showCharCount = false,
    maxLength,
    resize = 'vertical',
    fullWidth = false,
    className = '',
    disabled = false,
    value,
    ...props
}, ref) => {
    // Determine textarea state based on props
    const textareaState = state || (error ? 'error' : success ? 'success' : warning ? 'warning' : 'default');

    // Size classes
    const sizeClasses = {
        sm: ['px-3', 'py-2', 'text-sm', 'min-h-[80px]'],
        md: ['px-4', 'py-3', 'text-sm', 'min-h-[120px]'],
        lg: ['px-5', 'py-4', 'text-base', 'min-h-[160px]'],
    };

    // Resize classes
    const resizeClasses = {
        none: 'resize-none',
        vertical: 'resize-y',
        horizontal: 'resize-x',
        both: 'resize',
    };

    // State classes
    const stateClasses = {
        default: [
            'border-surface',
            'focus:border-primary',
            'focus:ring-primary',
        ],
        error: [
            'border-error',
            'focus:border-error',
            'focus:ring-error',
        ],
        success: [
            'border-success',
            'focus:border-success',
            'focus:ring-success',
        ],
        warning: [
            'border-warning',
            'focus:border-warning',
            'focus:ring-warning',
        ],
    };

    // Base classes
    const baseClasses = [
        'w-full',
        'border',
        'rounded-lg',
        'bg-surface',
        'text-primary',
        'transition-all',
        'duration-200',
        'focus:outline-none',
        'focus:ring-2',
        'focus:ring-offset-2',
        'disabled:opacity-50',
        'disabled:cursor-not-allowed',
        'placeholder:text-muted',
        resizeClasses[resize],
    ];

    // Combine all classes
    const textareaClasses = [
        ...baseClasses,
        ...sizeClasses[size],
        ...stateClasses[textareaState],
        className,
    ].filter(Boolean).join(' ');

    // Character count
    const charCount = typeof value === 'string' ? value.length : 0;
    const charCountExceeded = maxLength && charCount > maxLength;

    return (
        <div className={`form-group ${fullWidth ? 'w-full' : ''}`}>
            {label && (
                <label
                    htmlFor={props.id}
                    className="form-label"
                >
                    {label}
                </label>
            )}

            {/* Textarea */}
            <textarea
                ref={ref}
                className={textareaClasses}
                disabled={disabled}
                maxLength={maxLength}
                aria-invalid={textareaState === 'error' || charCountExceeded}
                aria-describedby={
                    error ? `${props.id}-error`
                        : success ? `${props.id}-success`
                            : warning ? `${props.id}-warning`
                                : helperText ? `${props.id}-helper`
                                    : showCharCount ? `${props.id}-charcount`
                                        : undefined
                }
                value={value}
                {...props}
            />

            {/* Helper text and validation messages */}
            <div className="mt-2 flex justify-between items-start">
                <div className="flex-1">
                    {(helperText || error || success || warning) && (
                        <div className="text-sm">
                            {error && (
                                <p
                                    id={`${props.id}-error`}
                                    className="text-error"
                                    role="alert"
                                    aria-live="polite"
                                >
                                    {error}
                                </p>
                            )}
                            {success && (
                                <p
                                    id={`${props.id}-success`}
                                    className="text-success"
                                    role="status"
                                    aria-live="polite"
                                >
                                    {success}
                                </p>
                            )}
                            {warning && (
                                <p
                                    id={`${props.id}-warning`}
                                    className="text-warning"
                                    role="alert"
                                    aria-live="polite"
                                >
                                    {warning}
                                </p>
                            )}
                            {helperText && !error && !success && !warning && (
                                <p
                                    id={`${props.id}-helper`}
                                    className="text-muted"
                                >
                                    {helperText}
                                </p>
                            )}
                        </div>
                    )}
                </div>

                {/* Character count */}
                {showCharCount && maxLength && (
                    <div className="ml-4 text-sm">
                        <p
                            id={`${props.id}-charcount`}
                            className={`${charCountExceeded ? 'text-error' : 'text-muted'
                                }`}
                            aria-live="polite"
                        >
                            {charCount}/{maxLength}
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
});

Textarea.displayName = 'Textarea';

export default Textarea;
