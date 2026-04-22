/**
 * LabeledInput Component
 *
 * Accessible, reusable form input with explicit visible or screen-reader label.
 * Enforces label presence — never relies on placeholder-only text.
 *
 * @doc.type component
 * @doc.purpose Accessible form input with mandatory label
 * @doc.layer shared
 * @doc.pattern Form Component
 * @example
 * ```tsx
 * <LabeledInput
 *   id="email"
 *   label="Email address"
 *   type="email"
 *   value={email}
 *   onChange={(e) => setEmail(e.target.value)}
 * />
 * ```
 */

import React from 'react';
import { cn } from '../../lib/theme';

interface LabeledInputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'className'> {
  /** Visible label text — required for accessibility */
  label: string;
  /** Input identifier — links label to input via htmlFor */
  id: string;
  /** Optional helper text displayed below input */
  helperText?: string;
  /** Optional error message */
  error?: string;
  /** Whether to visually hide the label (screen-reader only) */
  labelSrOnly?: boolean;
  /** Custom className for the wrapper */
  wrapperClassName?: string;
}

export const LabeledInput = React.forwardRef<HTMLInputElement, LabeledInputProps>(
  ({ label, id, helperText, error, labelSrOnly = false, wrapperClassName, ...inputProps }, ref) => {
    return (
      <div className={cn('space-y-1.5', wrapperClassName)}>
        <label
          htmlFor={id}
          className={cn(
            'block text-sm font-medium text-gray-700 dark:text-gray-300',
            labelSrOnly && 'sr-only'
          )}
        >
          {label}
        </label>
        <input
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={
            cn(
              helperText && `${id}-helper`,
              error && `${id}-error`
            ) || undefined
          }
          className={cn(
            'w-full rounded-md border px-3 py-2 text-sm',
            'bg-white dark:bg-gray-800',
            'text-gray-900 dark:text-white',
            'placeholder-gray-400 dark:placeholder-gray-500',
            'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500',
            error
              ? 'border-red-500 focus:ring-red-500 focus:border-red-500'
              : 'border-gray-300 dark:border-gray-600'
          )}
          {...inputProps}
        />
        {helperText && !error && (
          <p id={`${id}-helper`} className="text-xs text-gray-500 dark:text-gray-400">
            {helperText}
          </p>
        )}
        {error && (
          <p id={`${id}-error`} className="text-xs text-red-600 dark:text-red-400" role="alert">
            {error}
          </p>
        )}
      </div>
    );
  }
);

LabeledInput.displayName = 'LabeledInput';

/**
 * LabeledSelect Component
 *
 * Accessible select dropdown with mandatory label.
 *
 * @doc.type component
 * @doc.purpose Accessible select dropdown with mandatory label
 * @doc.layer shared
 * @doc.pattern Form Component
 */
interface LabeledSelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'className'> {
  label: string;
  id: string;
  helperText?: string;
  error?: string;
  labelSrOnly?: boolean;
  wrapperClassName?: string;
  children: React.ReactNode;
}

export const LabeledSelect = React.forwardRef<HTMLSelectElement, LabeledSelectProps>(
  ({ label, id, helperText, error, labelSrOnly = false, wrapperClassName, children, ...selectProps }, ref) => {
    return (
      <div className={cn('space-y-1.5', wrapperClassName)}>
        <label
          htmlFor={id}
          className={cn(
            'block text-sm font-medium text-gray-700 dark:text-gray-300',
            labelSrOnly && 'sr-only'
          )}
        >
          {label}
        </label>
        <select
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={
            cn(
              helperText && `${id}-helper`,
              error && `${id}-error`
            ) || undefined
          }
          className={cn(
            'w-full rounded-md border px-3 py-2 text-sm',
            'bg-white dark:bg-gray-800',
            'text-gray-900 dark:text-white',
            'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500',
            error
              ? 'border-red-500 focus:ring-red-500 focus:border-red-500'
              : 'border-gray-300 dark:border-gray-600'
          )}
          {...selectProps}
        >
          {children}
        </select>
        {helperText && !error && (
          <p id={`${id}-helper`} className="text-xs text-gray-500 dark:text-gray-400">
            {helperText}
          </p>
        )}
        {error && (
          <p id={`${id}-error`} className="text-xs text-red-600 dark:text-red-400" role="alert">
            {error}
          </p>
        )}
      </div>
    );
  }
);

LabeledSelect.displayName = 'LabeledSelect';

export default LabeledInput;
