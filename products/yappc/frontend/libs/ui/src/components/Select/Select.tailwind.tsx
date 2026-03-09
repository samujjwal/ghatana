/**
 * Select Component (Tailwind CSS + Base UI)
 * 
 * A dropdown select component using Base UI Select primitives styled with Tailwind CSS.
 * Supports single selection, keyboard navigation, and full accessibility.
 * 
 * @example
 * ```tsx
 * <SelectTailwind
 *   label="Choose a country"
 *   placeholder="Select country"
 *   value={country}
 *   onChange={setCountry}
 * >
 *   <SelectOption value="us">United States</SelectOption>
 *   <SelectOption value="ca">Canada</SelectOption>
 *   <SelectOption value="uk">United Kingdom</SelectOption>
 * </SelectTailwind>
 * ```
 */

import { Select as BaseSelect } from '@base-ui/react/select';
import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Props for SelectOption component
 */
export interface SelectOptionProps {
  /** The value of the option */
  value: string;
  /** The display text (if different from children) */
  label?: string;
  /** Whether the option is disabled */
  disabled?: boolean;
  /** Children to render */
  children?: React.ReactNode;
  /** Additional CSS classes */
  className?: string;
}

/**
 * SelectOption component for individual select items
 */
export const SelectOption = React.forwardRef<HTMLDivElement, SelectOptionProps>(
  ({ value, label, disabled, children, className }, ref) => {
    return (
      <BaseSelect.Item
        ref={ref}
        value={value}
        disabled={disabled}
        className={cn(
          'relative cursor-pointer select-none py-2 pl-3 pr-9',
          'text-grey-900 hover:bg-grey-100',
          'data-[highlighted]:bg-primary-50 data-[highlighted]:text-primary-900',
          'data-[selected]:bg-primary-100 data-[selected]:font-semibold',
          'data-[disabled]:opacity-50 data-[disabled]:cursor-not-allowed',
          'transition-colors',
          className
        )}
      >
        <BaseSelect.ItemText className="block truncate">
          {children || label}
        </BaseSelect.ItemText>
        <BaseSelect.ItemIndicator className="absolute inset-y-0 right-0 flex items-center pr-3 text-primary-600">
          <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
            <path
              fillRule="evenodd"
              d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
              clipRule="evenodd"
            />
          </svg>
        </BaseSelect.ItemIndicator>
      </BaseSelect.Item>
    );
  }
);
SelectOption.displayName = 'SelectOption';

/**
 * Props for SelectGroup component
 */
export interface SelectGroupProps {
  /** Label for the group */
  label?: string;
  /** Children options */
  children: React.ReactNode;
  /** Additional CSS classes */
  className?: string;
}

/**
 * SelectGroup component for grouping options
 */
export const SelectGroup: React.FC<SelectGroupProps> = ({ label, children, className }) => {
  return (
    <BaseSelect.Group className={cn('py-1', className)}>
      {label && (
        <BaseSelect.GroupLabel className="px-3 py-2 text-sm font-semibold text-grey-500">
          {label}
        </BaseSelect.GroupLabel>
      )}
      {children}
    </BaseSelect.Group>
  );
};

/**
 * Props for Select component
 */
export interface SelectProps {
  /** Label for the select */
  label?: string;
  /** Placeholder text when no value selected */
  placeholder?: string;
  /** Current selected value */
  value?: string;
  /** Default value (uncontrolled) */
  defaultValue?: string;
  /** Callback when value changes */
  onChange?: (value: string | null) => void;
  /** Whether the select is disabled */
  disabled?: boolean;
  /** Whether the select is required */
  required?: boolean;
  /** Error message */
  error?: string;
  /** Helper text */
  helperText?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Color scheme */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';
  /** Children (SelectOption or SelectGroup components) */
  children: React.ReactNode;
  /** Additional CSS classes for container */
  className?: string;
  /** Additional CSS classes for label */
  labelClassName?: string;
  /** Additional CSS classes for trigger */
  triggerClassName?: string;
}

/**
 * Select component for dropdown selection
 * 
 * Built with Base UI Select primitives and styled with Tailwind CSS.
 * Supports keyboard navigation, accessibility, and multiple size/color variants.
 */
export const Select = React.forwardRef<HTMLButtonElement, SelectProps>(
  (
    {
      label,
      placeholder = 'Select an option',
      value,
      defaultValue,
      onChange,
      disabled = false,
      required = false,
      error,
      helperText,
      size = 'md',
      colorScheme = 'primary',
      children,
      className,
      labelClassName,
      triggerClassName,
    },
    ref
  ) => {
    const hasError = Boolean(error);

    // Size classes for trigger
    const sizeClasses = {
      sm: 'px-3 py-1.5 text-sm',
      md: 'px-4 py-2 text-base',
      lg: 'px-5 py-3 text-lg',
    };

    // Color scheme classes for trigger
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

        <BaseSelect.Root
          value={value}
          defaultValue={defaultValue}
          onValueChange={(newValue) => onChange?.(newValue)}
          disabled={disabled}
          required={required}
        >
          <BaseSelect.Trigger
            ref={ref}
            className={cn(
              'relative w-full cursor-pointer rounded-md border bg-white text-left shadow-sm',
              'transition-colors',
              sizeClasses[size],
              hasError
                ? 'border-error-500 focus:ring-error-500 focus:border-error-500'
                : colorClasses[colorScheme],
              disabled
                ? 'cursor-not-allowed bg-grey-50 opacity-50'
                : 'border-grey-300 hover:border-grey-400',
              'focus:outline-none focus:ring-2 focus:ring-offset-2',
              triggerClassName
            )}
          >
            <BaseSelect.Value className="block truncate pr-10">
              {(selectedValue) =>
                selectedValue ? selectedValue : <span className="text-grey-400">{placeholder}</span>
              }
            </BaseSelect.Value>
            <BaseSelect.Icon className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2">
              <svg
                className="h-5 w-5 text-grey-400"
                viewBox="0 0 20 20"
                fill="currentColor"
                aria-hidden="true"
              >
                <path
                  fillRule="evenodd"
                  d="M10 3a.75.75 0 01.55.24l3.25 3.5a.75.75 0 11-1.1 1.02L10 4.852 7.3 7.76a.75.75 0 01-1.1-1.02l3.25-3.5A.75.75 0 0110 3zm-3.76 9.2a.75.75 0 011.06.04l2.7 2.908 2.7-2.908a.75.75 0 111.1 1.02l-3.25 3.5a.75.75 0 01-1.1 0l-3.25-3.5a.75.75 0 01.04-1.06z"
                  clipRule="evenodd"
                />
              </svg>
            </BaseSelect.Icon>
          </BaseSelect.Trigger>

          <BaseSelect.Portal>
            <BaseSelect.Positioner sideOffset={4}>
              <BaseSelect.Popup
                className={cn(
                  'max-h-60 w-full overflow-auto rounded-md bg-white py-1 shadow-lg',
                  'ring-1 ring-black ring-opacity-5',
                  'focus:outline-none',
                  'z-50'
                )}
              >
                <BaseSelect.List>{children}</BaseSelect.List>
              </BaseSelect.Popup>
            </BaseSelect.Positioner>
          </BaseSelect.Portal>
        </BaseSelect.Root>

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
Select.displayName = 'Select';

// Export with Tailwind suffix for consistency
export { Select as SelectTailwind };
