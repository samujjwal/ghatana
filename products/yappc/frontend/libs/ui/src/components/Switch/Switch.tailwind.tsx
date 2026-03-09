/**
 * Tailwind CSS Switch Component
 * 
 * Toggle switch built with Base UI Switch primitives and Tailwind CSS.
 * Follows Base UI composable API: Switch.Root → Switch.Thumb
 * 
 * @example
 * ```tsx
 * // Basic switch
 * <Switch label="Enable notifications" />
 * 
 * // Checked state
 * <Switch label="Dark mode" checked />
 * 
 * // Different colors
 * <Switch label="Success" colorScheme="success" />
 * <Switch label="Error" colorScheme="error" />
 * 
 * // Sizes
 * <Switch label="Small" size="sm" />
 * <Switch label="Large" size="lg" />
 * 
 * // Disabled
 * <Switch label="Disabled" disabled />
 * ```
 * 
 * @see {@link https://base-ui.com/react/switch Base UI Switch Documentation}
 */
import { Switch as BaseSwitch } from '@base-ui/react/switch';
import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * Switch component props
 */
export interface SwitchProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  /**
   * Label text displayed next to switch
   */
  label?: string;

  /**
   * Checked state
   */
  checked?: boolean;

  /**
   * Color scheme for the switch
   * 
   * @default 'primary'
   */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';

  /**
   * Size of the switch
   * 
   * - `sm`: Small (w-8 h-5)
   * - `md`: Medium (w-11 h-6) - default
   * - `lg`: Large (w-14 h-7)
   * 
   * @default 'md'
   */
  size?: 'sm' | 'md' | 'lg';

  /**
   * Additional className for the switch element
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
 * Switch Component
 * 
 * @param props - Switch component props
 * @param ref - Forwarded ref to switch button element
 * @returns Rendered switch component
 */
export const Switch = React.forwardRef<HTMLButtonElement, SwitchProps>(
  (
    {
      label,
      colorScheme = 'primary',
      size = 'md',
      className,
      labelClassName,
      containerClassName,
      disabled,
      checked,
      ...props
    },
    ref
  ) => {
    // Size-based classes
    const sizeClasses = {
      sm: {
        track: 'w-8 h-5',
        thumb: 'h-4 w-4 data-[state=checked]:translate-x-3',
        label: 'text-sm',
      },
      md: {
        track: 'w-11 h-6',
        thumb: 'h-5 w-5 data-[state=checked]:translate-x-5',
        label: 'text-base',
      },
      lg: {
        track: 'w-14 h-7',
        thumb: 'h-6 w-6 data-[state=checked]:translate-x-7',
        label: 'text-lg',
      },
    };

    // Color scheme classes
    const colorClasses = {
      primary: 'data-[state=checked]:bg-primary-500',
      secondary: 'data-[state=checked]:bg-secondary-500',
      success: 'data-[state=checked]:bg-success-500',
      error: 'data-[state=checked]:bg-error-500',
      warning: 'data-[state=checked]:bg-warning-500',
      grey: 'data-[state=checked]:bg-grey-700',
    };

    // Focus ring colors
    const focusRingClasses = {
      primary: 'focus:ring-primary-500',
      secondary: 'focus:ring-secondary-500',
      success: 'focus:ring-success-500',
      error: 'focus:ring-error-500',
      warning: 'focus:ring-warning-500',
      grey: 'focus:ring-grey-500',
    };

    return (
      <label
        className={cn(
          'inline-flex items-center gap-2 cursor-pointer',
          disabled && 'opacity-50 cursor-not-allowed',
          containerClassName
        )}
      >
        <BaseSwitch.Root
          ref={ref}
          disabled={disabled}
          checked={checked}
          className={cn(
            // Base styles
            'relative inline-flex items-center rounded-full transition-colors',
            'bg-grey-300',

            // Focus styles
            'focus:outline-none focus:ring-2 focus:ring-offset-2',
            focusRingClasses[colorScheme],

            // Size
            sizeClasses[size].track,

            // Color scheme (checked state)
            colorClasses[colorScheme],

            // Disabled
            disabled && 'cursor-not-allowed',

            // Custom className
            className
          )}
          {...props}
        >
          <BaseSwitch.Thumb
            className={cn(
              // Base styles
              'inline-block rounded-full bg-white transition-transform',
              'shadow-md',
              'translate-x-0.5',

              // Size and translation
              sizeClasses[size].thumb
            )}
          />
        </BaseSwitch.Root>

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

Switch.displayName = 'Switch';
