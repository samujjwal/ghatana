import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * Form field layout orientation
 */
export type FormFieldLayout = 'vertical' | 'horizontal';

/**
 * Form field size
 */
export type FormFieldSize = 'small' | 'medium' | 'large';

/**
 * FormField component props
 */
export interface FormFieldProps {
  /**
   * Unique field identifier
   */
  id?: string;
  /**
   * Field label
   */
  label?: React.ReactNode;
  /**
   * Error message (shows when present)
   */
  error?: string;
  /**
   * Helper text (shows when no error)
   */
  helperText?: string;
  /**
   * Required field indicator
   * @default false
   */
  required?: boolean;
  /**
   * Disabled state
   * @default false
   */
  disabled?: boolean;
  /**
   * Layout orientation
   * @default 'vertical'
   */
  layout?: FormFieldLayout;
  /**
   * Field size
   * @default 'medium'
   */
  size?: FormFieldSize;
  /**
   * Label width (for horizontal layout)
   * @default '120px'
   */
  labelWidth?: string;
  /**
   * Hide label visually (but keep for screen readers)
   * @default false
   */
  hideLabel?: boolean;
  /**
   * Custom className for wrapper
   */
  className?: string;
  /**
   * Custom className for label
   */
  labelClassName?: string;
  /**
   * Custom className for input wrapper
   */
  inputWrapperClassName?: string;
  /**
   * Form control element (input, select, textarea, etc.)
   */
  children: React.ReactNode;
}

/**
 * FormField - Form field wrapper with label, error, and helper text
 *
 * Provides consistent layout and styling for form inputs with
 * labels, validation messages, and helper text.
 *
 * @example Basic usage
 * ```tsx
 * <FormField label="Email" error={errors.email}>
 *   <input type="email" />
 * </FormField>
 * ```
 *
 * @example With helper text and required
 * ```tsx
 * <FormField
 *   label="Username"
 *   helperText="Must be 3-20 characters"
 *   required
 * >
 *   <input type="text" />
 * </FormField>
 * ```
 *
 * @example Horizontal layout
 * ```tsx
 * <FormField
 *   label="First Name"
 *   layout="horizontal"
 *   labelWidth="150px"
 * >
 *   <input type="text" />
 * </FormField>
 * ```
 */
export const FormField = React.forwardRef<HTMLDivElement, FormFieldProps>(
  (
    {
      id,
      label,
      error,
      helperText,
      required = false,
      disabled = false,
      layout = 'vertical',
      size = 'medium',
      labelWidth = '120px',
      hideLabel = false,
      className,
      labelClassName,
      inputWrapperClassName,
      children,
    },
    ref
  ) => {
    const fieldId = id || React.useId();
    const errorId = error ? `${fieldId}-error` : undefined;
    const helperId = helperText && !error ? `${fieldId}-helper` : undefined;

    // Size-based spacing
    const sizeClasses = {
      small: 'gap-1',
      medium: 'gap-2',
      large: 'gap-3',
    };

    // Layout-based classes
    const layoutClasses = {
      vertical: 'flex-col',
      horizontal: 'flex-row items-start',
    };

    // Label size classes
    const labelSizeClasses = {
      small: 'text-xs',
      medium: 'text-sm',
      large: 'text-base',
    };

    // Clone children to add aria attributes
    const enhancedChildren = React.Children.map(children, (child) => {
      if (React.isValidElement(child)) {
        const childProps = child.props as unknown;
        return React.cloneElement(child, {
          id: fieldId,
          'aria-invalid': error ? true : undefined,
          'aria-describedby': cn(errorId, helperId).trim() || undefined,
          'aria-required': required ? true : undefined,
          disabled: disabled || childProps.disabled,
        } as unknown);
      }
      return child;
    });

    return (
      <div
        ref={ref}
        className={cn(
          'flex',
          layoutClasses[layout],
          sizeClasses[size],
          className
        )}
      >
        {/* Label */}
        {label && (
          <label
            htmlFor={fieldId}
            className={cn(
              'font-medium text-grey-700 dark:text-grey-300',
              labelSizeClasses[size],
              disabled && 'opacity-50 cursor-not-allowed',
              hideLabel && 'sr-only',
              layout === 'horizontal' && 'pt-2',
              labelClassName
            )}
            style={layout === 'horizontal' ? { width: labelWidth, flexShrink: 0 } : undefined}
          >
            {label}
            {required && (
              <span className="ml-1 text-red-500" aria-label="required">
                *
              </span>
            )}
          </label>
        )}

        {/* Input wrapper */}
        <div
          className={cn(
            'flex-1 flex flex-col gap-1',
            inputWrapperClassName
          )}
        >
          {/* Input */}
          {enhancedChildren}

          {/* Error message */}
          {error && (
            <div
              id={errorId}
              className="text-xs text-red-600 dark:text-red-400 flex items-start gap-1"
              role="alert"
            >
              <svg className="w-4 h-4 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{error}</span>
            </div>
          )}

          {/* Helper text */}
          {helperText && !error && (
            <div
              id={helperId}
              className="text-xs text-grey-600 dark:text-grey-400"
            >
              {helperText}
            </div>
          )}
        </div>
      </div>
    );
  }
);

FormField.displayName = 'FormField';
