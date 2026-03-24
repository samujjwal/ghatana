/**
 * Tailwind CSS TextField Component
 * 
 * Text input field built with Base UI Field primitives and Tailwind CSS.
 * Follows Base UI composable API pattern: Field.Root → Field.Label → Field.Control → Field.Description/Error
 * 
 * @example
 * ```tsx
 * // Basic text field
 * <TextField label="Email" placeholder="Enter your email" />
 * 
 * // With helper text
 * <TextField 
 *   label="Username" 
 *   helperText="Must be 3-20 characters"
 * />
 * 
 * // Error state
 * <TextField 
 *   label="Password" 
 *   error="Password is required"
 * />
 * 
 * // Different sizes
 * <TextField size="sm" label="Small" />
 * <TextField size="md" label="Medium" />
 * <TextField size="lg" label="Large" />
 * 
 * // Variants
 * <TextField variant="outline" label="Outline" />
 * <TextField variant="filled" label="Filled" />
 * ```
 * 
 * @see {@link https://base-ui.com/react/field Base UI Field Documentation}
 */
import { Field } from '@base-ui/react/field';
import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * TextField component props
 */
export interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /**
   * Label text displayed above the input
   */
  label?: string;

  /**
   * Helper text displayed below the input (when no error)
   */
  helperText?: string;

  /**
   * Error message - shows error state and displays message
   */
  error?: string;

  /**
   * Visual variant of the text field
   * 
   * - `outline`: Border with transparent background (default)
   * - `filled`: Filled background with subtle border
   * 
   * @default 'outline'
   */
  variant?: 'outline' | 'filled';

  /**
   * Size of the text field
   * 
   * - `sm`: Small (py-1.5, text-sm)
   * - `md`: Medium (py-2, text-base) - default
   * - `lg`: Large (py-3, text-lg)
   * 
   * @default 'md'
   */
  size?: 'sm' | 'md' | 'lg';

  /**
   * Whether the field is required
   */
  required?: boolean;

  /**
   * Additional className for the input element
   */
  className?: string;

  /**
   * Additional className for the root container
   */
  containerClassName?: string;
}

/**
 * TextField Component
 * 
 * @param props - TextField component props
 * @param ref - Forwarded ref to input element
 * @returns Rendered text field component
 */
export const TextField = React.forwardRef<HTMLInputElement, TextFieldProps>(
  (
    {
      label,
      helperText,
      error,
      variant = 'outline',
      size = 'md',
      required = false,
      className,
      containerClassName,
      disabled,
      ...props
    },
    ref
  ) => {
    const inputId = React.useId();
    const hasError = Boolean(error);

    // Size-based classes
    const sizeClasses = {
      sm: 'px-3 py-1.5 text-sm',
      md: 'px-4 py-2 text-base',
      lg: 'px-5 py-3 text-lg',
    };

    // Variant-based classes
    const variantClasses = {
      outline: cn(
        'bg-white border',
        hasError ? 'border-error-500' : 'border-grey-300',
        'focus:border-primary-500'
      ),
      filled: cn(
        'bg-grey-50 border border-grey-200',
        hasError ? 'border-error-500' : '',
        'focus:bg-white focus:border-primary-500'
      ),
    };

    return (
      <Field.Root
        invalid={hasError}
        disabled={disabled}
        className={cn('flex flex-col gap-1', containerClassName)}
      >
        {label && (
          <Field.Label
            htmlFor={inputId}
            className={cn(
              'text-sm font-medium',
              hasError ? 'text-error-700' : 'text-grey-900',
              disabled && 'opacity-50'
            )}
          >
            {label}
            {required && <span className="text-error-500 ml-1">*</span>}
          </Field.Label>
        )}

        <Field.Control
          ref={ref}
          id={inputId}
          required={required}
          className={cn(
            // Base styles
            'w-full rounded-md transition-colors',
            'placeholder:text-grey-400',
            'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-0',

            // Size
            sizeClasses[size],

            // Variant
            variantClasses[variant],

            // Disabled state
            disabled && 'opacity-50 cursor-not-allowed bg-grey-100',

            // Custom className
            className
          )}
          {...props}
        />

        {helperText && !hasError && (
          <Field.Description
            className={cn(
              'text-sm text-grey-600',
              disabled && 'opacity-50'
            )}
          >
            {helperText}
          </Field.Description>
        )}

        {hasError && (
          <Field.Error className="text-sm text-error-600 font-medium">
            {error}
          </Field.Error>
        )}
      </Field.Root>
    );
  }
);

TextField.displayName = 'TextField';
