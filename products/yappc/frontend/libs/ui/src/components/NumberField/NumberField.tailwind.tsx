/**
 * NumberField Component (Tailwind CSS + Base UI)
 * 
 * A number input component using Base UI NumberField primitives styled with Tailwind CSS.
 * Supports increment/decrement buttons, min/max validation, and step controls.
 * 
 * @example
 * ```tsx
 * <NumberFieldTailwind
 *   label="Quantity"
 *   min={0}
 *   max={100}
 *   value={quantity}
 *   onChange={setQuantity}
 * />
 * ```
 */

import { NumberField as BaseNumberField } from '@base-ui/react/number-field';
import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Props for NumberField component
 */
export interface NumberFieldProps {
  /** Label for the number field */
  label?: string;
  /** Minimum value */
  min?: number;
  /** Maximum value */
  max?: number;
  /** Step increment */
  step?: number;
  /** Current value */
  value?: number | null;
  /** Default value (uncontrolled) */
  defaultValue?: number;
  /** Callback when value changes */
  onChange?: (value: number | null) => void;
  /** Whether the field is disabled */
  disabled?: boolean;
  /** Whether the field is required */
  required?: boolean;
  /** Whether the field is read-only */
  readOnly?: boolean;
  /** Error message */
  error?: string;
  /** Helper text */
  helperText?: string;
  /** Placeholder text */
  placeholder?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Color scheme */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';
  /** Show increment/decrement buttons */
  showSteppers?: boolean;
  /** Additional CSS classes for container */
  className?: string;
  /** Additional CSS classes for label */
  labelClassName?: string;
  /** Additional CSS classes for input */
  inputClassName?: string;
}

/**
 * NumberField component for number input with steppers
 * 
 * Built with Base UI NumberField primitives and styled with Tailwind CSS.
 * Supports keyboard navigation, min/max validation, and increment/decrement controls.
 */
export const NumberField = React.forwardRef<HTMLInputElement, NumberFieldProps>(
  (
    {
      label,
      min,
      max,
      step = 1,
      value,
      defaultValue,
      onChange,
      disabled = false,
      required = false,
      readOnly = false,
      error,
      helperText,
      placeholder,
      size = 'md',
      colorScheme = 'primary',
      showSteppers = true,
      className,
      labelClassName,
      inputClassName,
    },
    ref
  ) => {
    const hasError = Boolean(error);

    // Size classes
    const sizeClasses = {
      sm: {
        input: 'px-3 py-1.5 text-sm',
        button: 'px-2 py-1',
        icon: 'h-3 w-3',
      },
      md: {
        input: 'px-4 py-2 text-base',
        button: 'px-2.5 py-1.5',
        icon: 'h-4 w-4',
      },
      lg: {
        input: 'px-5 py-3 text-lg',
        button: 'px-3 py-2',
        icon: 'h-5 w-5',
      },
    };

    // Color scheme classes
    const colorClasses = {
      primary: 'focus:ring-primary-500 focus:border-primary-500',
      secondary: 'focus:ring-secondary-500 focus:border-secondary-500',
      success: 'focus:ring-success-500 focus:border-success-500',
      error: 'focus:ring-error-500 focus:border-error-500',
      warning: 'focus:ring-warning-500 focus:border-warning-500',
      grey: 'focus:ring-grey-500 focus:border-grey-500',
    };

    return (
      <div className={cn('w-full', className)}>
        {label && (
          <label
            className={cn(
              'mb-1 block text-sm font-medium text-grey-700',
              disabled && 'opacity-50',
              labelClassName
            )}
          >
            {label}
            {required && <span className="ml-1 text-error-500">*</span>}
          </label>
        )}

        <BaseNumberField.Root
          value={value}
          defaultValue={defaultValue}
          onValueChange={(newValue) => onChange?.(newValue)}
          min={min}
          max={max}
          step={step}
          disabled={disabled}
          required={required}
          readOnly={readOnly}
        >
          <BaseNumberField.Group
            className={cn(
              'relative flex items-center rounded-md border shadow-sm transition-colors',
              hasError
                ? 'border-error-500'
                : 'border-grey-300',
              disabled && 'bg-grey-50 opacity-50',
              !disabled && 'hover:border-grey-400'
            )}
          >
            <BaseNumberField.Input
              ref={ref}
              placeholder={placeholder}
              className={cn(
                'flex-1 border-0 bg-transparent focus:outline-none focus:ring-0',
                sizeClasses[size].input,
                hasError
                  ? 'text-error-900'
                  : 'text-grey-900',
                disabled && 'cursor-not-allowed',
                readOnly && 'cursor-default',
                !showSteppers && 'rounded-md',
                inputClassName
              )}
            />

            {showSteppers && (
              <div className="flex flex-col border-l border-grey-300">
                <BaseNumberField.Increment
                  className={cn(
                    'flex items-center justify-center border-b border-grey-300 transition-colors',
                    'hover:bg-grey-100 active:bg-grey-200',
                    'focus:outline-none focus:ring-2 focus:ring-inset',
                    hasError
                      ? 'focus:ring-error-500'
                      : colorClasses[colorScheme],
                    disabled && 'cursor-not-allowed opacity-50 hover:bg-transparent',
                    sizeClasses[size].button
                  )}
                  aria-label="Increment"
                >
                  <svg
                    className={cn('text-grey-600', sizeClasses[size].icon)}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
                  </svg>
                </BaseNumberField.Increment>

                <BaseNumberField.Decrement
                  className={cn(
                    'flex items-center justify-center transition-colors',
                    'hover:bg-grey-100 active:bg-grey-200',
                    'focus:outline-none focus:ring-2 focus:ring-inset',
                    hasError
                      ? 'focus:ring-error-500'
                      : colorClasses[colorScheme],
                    disabled && 'cursor-not-allowed opacity-50 hover:bg-transparent',
                    sizeClasses[size].button
                  )}
                  aria-label="Decrement"
                >
                  <svg
                    className={cn('text-grey-600', sizeClasses[size].icon)}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </BaseNumberField.Decrement>
              </div>
            )}
          </BaseNumberField.Group>
        </BaseNumberField.Root>

        {(helperText || error) && (
          <p
            className={cn(
              'mt-1 text-sm',
              hasError ? 'text-error-600' : 'text-grey-500'
            )}
          >
            {error || helperText}
          </p>
        )}
      </div>
    );
  }
);
NumberField.displayName = 'NumberField';

// Export with Tailwind suffix for consistency
export { NumberField as NumberFieldTailwind };
