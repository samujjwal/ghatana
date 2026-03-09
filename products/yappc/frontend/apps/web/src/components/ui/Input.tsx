/**
 * Input Component
 * 
 * Consistent input component with validation states, sizes, and accessibility.
 * Replaces inconsistent input patterns throughout the application.
 * 
 * @doc.type component
 * @doc.purpose Standardized input with validation and accessibility
 * @doc.layer ui
 * @doc.pattern Form Component
 */

import React, { forwardRef, useState } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';

type InputSize = 'sm' | 'md' | 'lg';
type InputState = 'default' | 'error' | 'success' | 'warning';

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
    /** Input label */
    label?: string;
    /** Helper text below input */
    helperText?: string;
    /** Error message */
    error?: string;
    /** Success message */
    success?: string;
    /** Warning message */
    warning?: string;
    /** Input size */
    size?: InputSize;
    /** Input state */
    state?: InputState;
    /** Left icon */
    leftIcon?: ReactNode;
    /** Right icon */
    rightIcon?: ReactNode;
    /** Loading state */
    loading?: boolean;
    /** Required indicator */
    required?: boolean;
    /** Full width */
    fullWidth?: boolean;
}

/**
 * Standardized Input Component
 * 
 * Provides consistent styling, proper states, validation feedback, and accessibility.
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(({
    label,
    helperText,
    error,
    success,
    warning,
    size = 'md',
    state,
    leftIcon,
    rightIcon,
    loading = false,
    required = false,
    fullWidth = false,
    className = '',
    disabled = false,
    ...props
}, ref) => {
    const [focused, setFocused] = useState(false);

    // Determine input state based on props
    const inputState = state || (error ? 'error' : success ? 'success' : warning ? 'warning' : 'default');

    // Size classes
    const sizeClasses = {
        sm: ['px-3', 'py-2', 'text-sm', 'min-h-[40px]'],
        md: ['px-4', 'py-3', 'text-sm', 'min-h-[44px]'], // 44px minimum touch target
        lg: ['px-5', 'py-4', 'text-base', 'min-h-[48px]'],
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
    ];

    // Combine all classes
    const inputClasses = [
        ...baseClasses,
        ...sizeClasses[size],
        ...stateClasses[inputState],
        leftIcon ? 'pl-10' : '',
        rightIcon || loading ? 'pr-10' : '',
        className,
    ].filter(Boolean).join(' ');

    // Loading spinner
    const LoadingSpinner = () => (
        <svg
            className="animate-spin h-4 w-4 text-muted"
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

    return (
        <div className={`form-group ${fullWidth ? 'w-full' : ''}`}>
            {label && (
                <label
                    htmlFor={props.id}
                    className={`form-label ${required ? 'required' : ''}`}
                >
                    {label}
                    {required && <span className="text-error ml-1" aria-label="Required">*</span>}
                </label>
            )}

            <div className="relative">
                {/* Left icon */}
                {leftIcon && (
                    <div className="absolute left-3 top-1/2 -translate-y-1/2 text-muted pointer-events-none">
                        {leftIcon}
                    </div>
                )}

                {/* Input */}
                <input
                    ref={ref}
                    className={inputClasses}
                    disabled={disabled || loading}
                    onFocus={(e) => {
                        setFocused(true);
                        props.onFocus?.(e);
                    }}
                    onBlur={(e) => {
                        setFocused(false);
                        props.onBlur?.(e);
                    }}
                    aria-invalid={inputState === 'error'}
                    aria-describedby={
                        error ? `${props.id}-error`
                            : success ? `${props.id}-success`
                                : warning ? `${props.id}-warning`
                                    : helperText ? `${props.id}-helper`
                                        : undefined
                    }
                    {...props}
                />

                {/* Right icon or loading spinner */}
                {(rightIcon || loading) && (
                    <div className="absolute right-3 top-1/2 -translate-y-1/2 text-muted pointer-events-none">
                        {loading ? <LoadingSpinner /> : rightIcon}
                    </div>
                )}
            </div>

            {/* Helper text and validation messages */}
            {(helperText || error || success || warning) && (
                <div className="mt-2 text-sm">
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
    );
});

Input.displayName = 'Input';

export default Input;
