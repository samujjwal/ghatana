/**
 * Textarea Component (Tailwind CSS + Base UI)
 * 
 * A multiline text input component using Base UI Field primitives styled with Tailwind CSS.
 * Supports auto-resize, character counting, and full accessibility.
 * 
 * @example
 * ```tsx
 * <TextareaTailwind
 *   label="Description"
 *   placeholder="Enter description..."
 *   value={description}
 *   onChange={(e) => setDescription(e.target.value)}
 *   rows={4}
 * />
 * ```
 */

import { Field } from '@base-ui/react/field';
import React, { useEffect, useRef } from 'react';

import { cn } from '../../utils/cn';

/**
 * Props for Textarea component
 */
export interface TextareaProps extends Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, 'size'> {
  /** Label for the textarea */
  label?: string;
  /** Error message */
  error?: string;
  /** Helper text */
  helperText?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Variant style */
  variant?: 'outline' | 'filled';
  /** Color scheme for focus states */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';
  /** Whether to auto-resize based on content */
  autoResize?: boolean;
  /** Maximum character count */
  maxLength?: number;
  /** Show character counter */
  showCounter?: boolean;
  /** Additional CSS classes for container */
  containerClassName?: string;
  /** Additional CSS classes for label */
  labelClassName?: string;
}

/**
 * Textarea component for multiline text input
 * 
 * Built with Base UI Field primitives and styled with Tailwind CSS.
 * Supports auto-resize, character counting, and accessibility.
 */
export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  (
    {
      label,
      error,
      helperText,
      size = 'md',
      variant = 'outline',
      colorScheme = 'primary',
      autoResize = false,
      maxLength,
      showCounter = false,
      required = false,
      disabled = false,
      className,
      containerClassName,
      labelClassName,
      value,
      onChange,
      ...props
    },
    ref
  ) => {
    const textareaRef = useRef<HTMLTextAreaElement | null>(null);
    const hasError = Boolean(error);

    // Auto-resize logic
    useEffect(() => {
      if (autoResize && textareaRef.current) {
        const textarea = textareaRef.current;
        textarea.style.height = 'auto';
        textarea.style.height = `${textarea.scrollHeight}px`;
      }
    }, [value, autoResize]);

    // Merge refs
    const mergedRef = (node: HTMLTextAreaElement) => {
      textareaRef.current = node;
      if (typeof ref === 'function') {
        ref(node);
      } else if (ref) {
        ref.current = node;
      }
    };

    // Size classes
    const sizeClasses = {
      sm: 'px-3 py-1.5 text-sm',
      md: 'px-4 py-2 text-base',
      lg: 'px-5 py-3 text-lg',
    };

    // Variant classes
    const variantClasses = {
      outline: 'border border-grey-300 bg-white',
      filled: 'border border-transparent bg-grey-100',
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

    // Character count
    const currentLength = typeof value === 'string' ? value.length : 0;
    const showCharCount = showCounter || (maxLength !== undefined);

    return (
      <Field.Root
        disabled={disabled}
        className={cn('w-full', containerClassName)}
      >
        {label && (
          <Field.Label
            className={cn(
              'mb-1 block text-sm font-medium text-grey-700',
              disabled && 'opacity-50',
              labelClassName
            )}
          >
            {label}
            {required && <span className="ml-1 text-error-500">*</span>}
          </Field.Label>
        )}

        <textarea
          ref={mergedRef}
          value={value}
          onChange={onChange}
          required={required}
          disabled={disabled}
          maxLength={maxLength}
          className={cn(
            'block w-full rounded-md shadow-sm transition-colors',
            'focus:outline-none focus:ring-2 focus:ring-offset-2',
            'resize-vertical',
            sizeClasses[size],
            variantClasses[variant],
            hasError
              ? 'border-error-500 focus:ring-error-500 focus:border-error-500'
              : colorClasses[colorScheme],
            disabled && 'cursor-not-allowed bg-grey-50 opacity-50',
            autoResize && 'resize-none overflow-hidden',
            className
          )}
          {...props}
        />

        {(helperText || error || showCharCount) && (
          <div className="mt-1 flex items-center justify-between gap-2">
            {(helperText || error) && (
              <Field.Description
                className={cn(
                  'text-sm',
                  hasError ? 'text-error-600' : 'text-grey-500',
                  !showCharCount && 'flex-1'
                )}
              >
                {error || helperText}
              </Field.Description>
            )}

            {showCharCount && (
              <span
                className={cn(
                  'text-sm',
                  maxLength && currentLength > maxLength * 0.9
                    ? 'text-warning-600'
                    : 'text-grey-500'
                )}
              >
                {currentLength}
                {maxLength && ` / ${maxLength}`}
              </span>
            )}
          </div>
        )}
      </Field.Root>
    );
  }
);
Textarea.displayName = 'Textarea';

// Export with Tailwind suffix for consistency
export { Textarea as TextareaTailwind };
