/**
 * Tailwind CSS Button Component
 * 
 * Custom button implementation using Tailwind CSS utilities.
 * Built following Base UI philosophy - provides presentation (styling),
 * uses native HTML <button> element for behavior and accessibility.
 * 
 * This component will eventually replace the MUI Button once migration is complete.
 * For now, it coexists as Button.tailwind.tsx.
 * 
 * @example
 * ```tsx
 * // Solid primary button (default)
 * <Button>Click me</Button>
 * 
 * // Outline variant
 * <Button variant="outline">Outline button</Button>
 * 
 * // Ghost variant (hover effect only)
 * <Button variant="ghost">Ghost button</Button>
 * 
 * // Different sizes
 * <Button size="sm">Small</Button>
 * <Button size="md">Medium</Button>
 * <Button size="lg">Large</Button>
 * 
 * // Disabled state
 * <Button disabled>Disabled</Button>
 * 
 * // Custom className (overrides/extends)
 * <Button className="shadow-xl hover:scale-105">Custom styles</Button>
 * ```
 * 
 * @see {@link https://base-ui.com Base UI Documentation}
 * @see {@link BASE_UI_TAILWIND_MIGRATION_PLAN.md Migration Plan}
 */
import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 *
 */
export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /**
   * Visual style variant of the button
   * 
   * - `solid`: Filled background with primary color (default)
   * - `outline`: Border with transparent background
   * - `ghost`: No border/background, hover effect only
   * - `link`: Styled as a link (underline on hover)
   * 
   * @default 'solid'
   */
  variant?: 'solid' | 'outline' | 'ghost' | 'link';
  
  /**
   * Size variant of the button
   * 
   * - `sm`: Small (px-3 py-1.5, text-sm)
   * - `md`: Medium (px-4 py-2, text-base) - default
   * - `lg`: Large (px-6 py-3, text-lg)
   * 
   * @default 'md'
   */
  size?: 'sm' | 'md' | 'lg';
  
  /**
   * Color scheme of the button
   * 
   * Uses design tokens from @ghatana/yappc-shared-ui-core/tokens
   * 
   * @default 'primary'
   */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';
  
  /**
   * Render button with full width
   * 
   * @default false
   */
  fullWidth?: boolean;
  
  /**
   * Loading state (shows spinner, disables interaction)
   * 
   * @default false
   */
  isLoading?: boolean;
  
  /**
   * Icon to display before button text
   */
  leftIcon?: React.ReactNode;
  
  /**
   * Icon to display after button text
   */
  rightIcon?: React.ReactNode;
}

/**
 * Tailwind Button Component
 * 
 * Accessible button component built with Tailwind CSS utilities.
 * 
 * Features:
 * - Semantic HTML (<button> element)
 * - Full keyboard support (Space/Enter)
 * - Focus ring (2px outline, primary color)
 * - Disabled state (opacity-50, cursor-not-allowed)
 * - Loading state (spinner + disabled)
 * - Responsive sizing
 * - Customizable via className prop
 */
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'solid',
      size = 'md',
      colorScheme = 'primary',
      fullWidth = false,
      isLoading = false,
      leftIcon,
      rightIcon,
      className,
      disabled,
      children,
      type = 'button',
      ...props
    },
    ref
  ) => {
    const isDisabled = disabled || isLoading;
    
    return (
      <button
        ref={ref}
        type={type}
        disabled={isDisabled}
        className={cn(
          // Base styles (all variants)
          'inline-flex items-center justify-center',
          'rounded-md font-medium',
          'transition-colors duration-200',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2',
          'disabled:opacity-50 disabled:cursor-not-allowed',
          
          // Size variants
          {
            'px-3 py-1.5 text-sm gap-1.5': size === 'sm',
            'px-4 py-2 text-base gap-2': size === 'md',
            'px-6 py-3 text-lg gap-2.5': size === 'lg',
          },
          
          // Variant + Color combinations
          {
            // Solid variant
            'bg-primary-500 text-white hover:bg-primary-600 active:bg-primary-700':
              variant === 'solid' && colorScheme === 'primary',
            'bg-secondary-500 text-white hover:bg-secondary-600 active:bg-secondary-700':
              variant === 'solid' && colorScheme === 'secondary',
            'bg-success-DEFAULT text-white hover:bg-success-dark':
              variant === 'solid' && colorScheme === 'success',
            'bg-error-DEFAULT text-white hover:bg-error-dark':
              variant === 'solid' && colorScheme === 'error',
            'bg-warning-DEFAULT text-white hover:bg-warning-dark':
              variant === 'solid' && colorScheme === 'warning',
            'bg-grey-500 text-white hover:bg-grey-600':
              variant === 'solid' && colorScheme === 'grey',
            
            // Outline variant
            'border-2 border-primary-500 text-primary-500 hover:bg-primary-50 active:bg-primary-100':
              variant === 'outline' && colorScheme === 'primary',
            'border-2 border-secondary-500 text-secondary-500 hover:bg-secondary-50':
              variant === 'outline' && colorScheme === 'secondary',
            'border-2 border-success-DEFAULT text-success-DEFAULT hover:bg-green-50':
              variant === 'outline' && colorScheme === 'success',
            'border-2 border-error-DEFAULT text-error-DEFAULT hover:bg-red-50':
              variant === 'outline' && colorScheme === 'error',
            'border-2 border-warning-DEFAULT text-warning-DEFAULT hover:bg-orange-50':
              variant === 'outline' && colorScheme === 'warning',
            'border-2 border-grey-500 text-grey-700 hover:bg-grey-50':
              variant === 'outline' && colorScheme === 'grey',
            
            // Ghost variant
            'text-primary-500 hover:bg-primary-50 active:bg-primary-100':
              variant === 'ghost' && colorScheme === 'primary',
            'text-secondary-500 hover:bg-secondary-50':
              variant === 'ghost' && colorScheme === 'secondary',
            'text-success-DEFAULT hover:bg-green-50':
              variant === 'ghost' && colorScheme === 'success',
            'text-error-DEFAULT hover:bg-red-50':
              variant === 'ghost' && colorScheme === 'error',
            'text-warning-DEFAULT hover:bg-orange-50':
              variant === 'ghost' && colorScheme === 'warning',
            'text-grey-700 hover:bg-grey-50':
              variant === 'ghost' && colorScheme === 'grey',
            
            // Link variant
            'text-primary-500 hover:underline active:text-primary-700':
              variant === 'link' && colorScheme === 'primary',
            'text-secondary-500 hover:underline':
              variant === 'link' && colorScheme === 'secondary',
            'text-success-DEFAULT hover:underline':
              variant === 'link' && colorScheme === 'success',
            'text-error-DEFAULT hover:underline':
              variant === 'link' && colorScheme === 'error',
            'text-warning-DEFAULT hover:underline':
              variant === 'link' && colorScheme === 'warning',
            'text-grey-700 hover:underline':
              variant === 'link' && colorScheme === 'grey',
          },
          
          // Full width
          fullWidth && 'w-full',
          
          // Custom className (overrides/extends)
          className
        )}
        {...props}
      >
        {/* Loading spinner */}
        {isLoading && (
          <svg
            className="animate-spin h-4 w-4"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            />
          </svg>
        )}
        
        {/* Left icon */}
        {leftIcon && !isLoading && leftIcon}
        
        {/* Button text */}
        {children}
        
        {/* Right icon */}
        {rightIcon && rightIcon}
      </button>
    );
  }
);

Button.displayName = 'Button';
