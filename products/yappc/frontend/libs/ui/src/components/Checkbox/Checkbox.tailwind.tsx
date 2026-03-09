/**
 * Tailwind CSS Checkbox Component
 *
 * Checkbox built with Base UI Checkbox primitives and Tailwind CSS.
 * Follows Base UI composable API: Checkbox.Root → Checkbox.Indicator
 *
 * @example
 * ```tsx
 * // Basic checkbox
 * <Checkbox label="Accept terms" />
 *
 * // Checked state
 * <Checkbox label="Subscribe" checked />
 *
 * // Indeterminate state (partial selection)
 * <Checkbox label="Select all" indeterminate />
 *
 * // Different colors
 * <Checkbox label="Primary" colorScheme="primary" />
 * <Checkbox label="Success" colorScheme="success" />
 *
 * // Sizes
 * <Checkbox label="Small" size="sm" />
 * <Checkbox label="Large" size="lg" />
 *
 * // Disabled
 * <Checkbox label="Disabled" disabled />
 * ```
 *
 * @see {@link https://base-ui.com/react/checkbox Base UI Checkbox Documentation}
 */
import { Checkbox as BaseCheckbox } from '@base-ui/react/checkbox';
import * as React from 'react';

import { cn } from '../../utils/cn';

// Check icon SVG
const CheckIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
  >
    <path
      d="M13.5 4L6 11.5L2.5 8"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

// Indeterminate icon (minus/dash)
const IndeterminateIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
  >
    <path
      d="M3 8H13"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    />
  </svg>
);

/**
 * Checkbox component props
 */
export interface CheckboxProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /**
   * Label text displayed next to checkbox
   */
  label?: string;

  /**
   * Indeterminate state (partially checked)
   * Useful for "select all" checkboxes
   */
  indeterminate?: boolean;

  /**
   * Color scheme for the checkbox
   *
   * @default 'primary'
   */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';

  /**
   * Size of the checkbox
   *
   * - `sm`: 16px (h-4 w-4)
   * - `md`: 20px (h-5 w-5) - default
   * - `lg`: 24px (h-6 w-6)
   *
   * @default 'md'
   */
  size?: 'sm' | 'md' | 'lg';

  /**
   * Additional className for the checkbox element
   */
  className?: string;

  /**
   * Additional className for the label
   */
  labelClassName?: string;

  /**
   * Additional className for the root container
   */
  containerClassName?: string;
}

/**
 * Checkbox Component
 *
 * @param props - Checkbox component props
 * @param ref - Forwarded ref to checkbox input element
 * @returns Rendered checkbox component
 */
export const Checkbox = React.forwardRef<HTMLButtonElement, CheckboxProps>(
  (
    {
      label,
      indeterminate = false,
      colorScheme = 'primary',
      size = 'md',
      className,
      labelClassName,
      containerClassName,
      disabled,
      checked,
      value,
      // Exclude HTML input attributes that conflict with BaseCheckbox.Root
      type: _type,
      form: _form,
      formAction: _formAction,
      formEncType: _formEncType,
      formMethod: _formMethod,
      formNoValidate: _formNoValidate,
      formTarget: _formTarget,
      name: _name,
      pattern: _pattern,
      placeholder: _placeholder,
      readOnly: _readOnly,
      required: _required,
      size: _size,
      src: _src,
      step: _step,
      ...props
    },
    ref
  ) => {
    // Size-based classes
    const sizeClasses = {
      sm: {
        box: 'h-4 w-4',
        icon: 'h-3 w-3',
        label: 'text-sm',
      },
      md: {
        box: 'h-5 w-5',
        icon: 'h-4 w-4',
        label: 'text-base',
      },
      lg: {
        box: 'h-6 w-6',
        icon: 'h-5 w-5',
        label: 'text-lg',
      },
    };

    // Color scheme classes
    const colorClasses = {
      primary: 'data-[state=checked]:bg-primary-500 data-[state=checked]:border-primary-500 data-[state=indeterminate]:bg-primary-500 data-[state=indeterminate]:border-primary-500',
      secondary: 'data-[state=checked]:bg-secondary-500 data-[state=checked]:border-secondary-500 data-[state=indeterminate]:bg-secondary-500 data-[state=indeterminate]:border-secondary-500',
      success: 'data-[state=checked]:bg-success-500 data-[state=checked]:border-success-500 data-[state=indeterminate]:bg-success-500 data-[state=indeterminate]:border-success-500',
      error: 'data-[state=checked]:bg-error-500 data-[state=checked]:border-error-500 data-[state=indeterminate]:bg-error-500 data-[state=indeterminate]:border-error-500',
      warning: 'data-[state=checked]:bg-warning-500 data-[state=checked]:border-warning-500 data-[state=indeterminate]:bg-warning-500 data-[state=indeterminate]:border-warning-500',
      grey: 'data-[state=checked]:bg-grey-700 data-[state=checked]:border-grey-700 data-[state=indeterminate]:bg-grey-700 data-[state=indeterminate]:border-grey-700',
    };

    return (
      <label
        className={cn(
          'inline-flex items-center gap-2 cursor-pointer',
          disabled && 'opacity-50 cursor-not-allowed',
          containerClassName
        )}
      >
        <BaseCheckbox.Root
          ref={ref}
          disabled={disabled}
          checked={checked}
          indeterminate={indeterminate}
          value={typeof value === 'string' ? value : undefined}
          className={cn(
            // Base styles
            'inline-flex items-center justify-center rounded border-2 transition-colors',
            'bg-white border-grey-300',

            // Focus styles
            'focus:outline-none focus:ring-2 focus:ring-offset-2',
            colorScheme === 'primary' && 'focus:ring-primary-500',
            colorScheme === 'secondary' && 'focus:ring-secondary-500',
            colorScheme === 'success' && 'focus:ring-success-500',
            colorScheme === 'error' && 'focus:ring-error-500',
            colorScheme === 'warning' && 'focus:ring-warning-500',
            colorScheme === 'grey' && 'focus:ring-grey-500',

            // Size
            sizeClasses[size].box,

            // Color scheme
            colorClasses[colorScheme],

            // Disabled
            disabled && 'cursor-not-allowed',

            // Custom className
            className
          )}
        >
          <BaseCheckbox.Indicator
            className={cn(
              'text-white flex items-center justify-center',
              sizeClasses[size].icon
            )}
          >
            {indeterminate ? (
              <IndeterminateIcon className="w-full h-full" />
            ) : (
              <CheckIcon className="w-full h-full" />
            )}
          </BaseCheckbox.Indicator>
        </BaseCheckbox.Root>

        {label && (
          <span
            className={cn(
              'select-none',
              sizeClasses[size].label,
              disabled && 'cursor-not-allowed',
              labelClassName
            )}
          >
            {label}
          </span>
        )}
      </label>
    );
  }
);

Checkbox.displayName = 'Checkbox';
