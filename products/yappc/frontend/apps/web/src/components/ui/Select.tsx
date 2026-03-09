/**
 * Select Component
 * 
 * Consistent select dropdown component with validation states and accessibility.
 * Replaces inconsistent select patterns throughout the application.
 * 
 * @doc.type component
 * @doc.purpose Standardized select with validation and accessibility
 * @doc.layer ui
 * @doc.pattern Form Component
 */

import React, { forwardRef } from 'react';
import type { SelectHTMLAttributes, ReactNode } from 'react';

type SelectSize = 'sm' | 'md' | 'lg';
type SelectState = 'default' | 'error' | 'success' | 'warning';

interface SelectOption {
    value: string;
    label: string;
    disabled?: boolean;
}

interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
    /** Select label */
    label?: string;
    /** Helper text below select */
    helperText?: string;
    /** Error message */
    error?: string;
    /** Success message */
    success?: string;
    /** Warning message */
    warning?: string;
    /** Select size */
    size?: SelectSize;
    /** Select state */
    state?: SelectState;
    /** Options array */
    options: SelectOption[];
    /** Placeholder option */
    placeholder?: string;
    /** Required indicator */
    required?: boolean;
    /** Full width */
    fullWidth?: boolean;
}

/**
 * Standardized Select Component
 * 
 * Provides consistent styling, proper states, validation feedback, and accessibility.
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(({
    label,
    helperText,
    error,
    success,
    warning,
    size = 'md',
    state,
    options,
    placeholder,
    required = false,
    fullWidth = false,
    className = '',
    disabled = false,
    value,
    ...props
}, ref) => {
    // Determine select state based on props
    const selectState = state || (error ? 'error' : success ? 'success' : warning ? 'warning' : 'default');

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
    ];

    // Combine all classes
    const selectClasses = [
        ...baseClasses,
        ...sizeClasses[size],
        ...stateClasses[selectState],
        className,
    ].filter(Boolean).join(' ');

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

            {/* Select */}
            <select
                ref={ref}
                className={selectClasses}
                disabled={disabled}
                value={value}
                aria-invalid={selectState === 'error'}
                aria-describedby={
                    error ? `${props.id}-error`
                        : success ? `${props.id}-success`
                            : warning ? `${props.id}-warning`
                                : helperText ? `${props.id}-helper`
                                    : undefined
                }
                {...props}
            >
                {placeholder && (
                    <option value="" disabled>
                        {placeholder}
                    </option>
                )}

                {options.map((option) => (
                    <option
                        key={option.value}
                        value={option.value}
                        disabled={option.disabled}
                    >
                        {option.label}
                    </option>
                ))}
            </select>

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

Select.displayName = 'Select';

export default Select;
