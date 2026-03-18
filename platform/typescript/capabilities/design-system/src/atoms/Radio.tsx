import React from 'react';
import { clsx } from 'clsx';

export interface RadioProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
    /**
     * Radio label text
     */
    label?: string;
    /**
     * Radio description text
     */
    description?: string;
    /**
     * Error message to display
     */
    error?: string;
    /**
     * Visual size variant
     * @default 'md'
     */
    size?: 'sm' | 'md' | 'lg';
    /**
     * Radio value
     */
    value?: string;
}

/**
 * Radio component for single selection within a group.
 *
 * @example
 * ```tsx
 * <Radio
 *   name="plan"
 *   value="basic"
 *   label="Basic Plan"
 *   description="For individuals"
 *   checked={selectedPlan === 'basic'}
 *   onChange={(e) => setSelectedPlan(e.target.value)}
 * />
 * ```
 */
export const Radio = React.forwardRef<HTMLInputElement, RadioProps>(
    (
        {
            label,
            description,
            error,
            size = 'md',
            className,
            disabled,
            ...props
        },
        ref
    ) => {
        const safeName = props.name ?? 'radio';
        const safeValue = props.value ?? 'on';
        const radioId = props.id || `radio-${safeName}-${safeValue}`;

        const sizeClasses = {
            sm: 'h-4 w-4',
            md: 'h-5 w-5',
            lg: 'h-6 w-6',
        };

        const labelSizeClasses = {
            sm: 'text-sm',
            md: 'text-base',
            lg: 'text-lg',
        };

        return (
            <div className="flex items-start">
                <div className="flex items-center h-5">
                    <input
                        ref={ref}
                        id={radioId}
                        type="radio"
                        disabled={disabled}
                        className={clsx(
                            sizeClasses[size],
                            'text-primary-600 border-gray-300 focus:ring-primary-500',
                            'disabled:cursor-not-allowed disabled:opacity-50',
                            error && 'border-error-500 focus:ring-error-500',
                            className
                        )}
                        aria-describedby={
                            description ? `${radioId}-description` : undefined
                        }
                        aria-invalid={error ? 'true' : 'false'}
                        {...props}
                    />
                </div>
                {(label || description) && (
                    <div className="ml-3 text-sm">
                        {label && (
                            <label
                                htmlFor={radioId}
                                className={clsx(
                                    'font-medium',
                                    labelSizeClasses[size],
                                    disabled ? 'text-gray-400 cursor-not-allowed' : 'text-gray-700',
                                    error && 'text-error-700'
                                )}
                            >
                                {label}
                            </label>
                        )}
                        {description && (
                            <p
                                id={`${radioId}-description`}
                                className={clsx(
                                    'text-gray-500',
                                    size === 'sm' && 'text-xs',
                                    disabled && 'text-gray-400'
                                )}
                            >
                                {description}
                            </p>
                        )}
                        {error && (
                            <p className="mt-1 text-sm text-error-600" role="alert">
                                {error}
                            </p>
                        )}
                    </div>
                )}
            </div>
        );
    }
);

Radio.displayName = 'Radio';
