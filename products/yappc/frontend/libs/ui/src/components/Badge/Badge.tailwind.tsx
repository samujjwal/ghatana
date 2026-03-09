/**
 * Badge Component (Tailwind CSS)
 * 
 * A small status indicator that appears near another element.
 * Used for notification counts, status indicators, or labels.
 */

import { forwardRef } from 'react';

import { cn } from '../../utils/cn';

/**
 *
 */
export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  /**
   * Badge content (usually a number or short text)
   */
  badgeContent?: React.ReactNode;
  
  /**
   * Color variant
   * @default 'primary'
   */
  color?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' | 'default';
  
  /**
   * Badge size
   * @default 'medium'
   */
  size?: 'small' | 'medium' | 'large';
  
  /**
   * Badge variant
   * @default 'standard'
   */
  variant?: 'standard' | 'dot';
  
  /**
   * Hide badge when badgeContent is 0
   * @default false
   */
  showZero?: boolean;
  
  /**
   * Max number to display (shows "max+" when exceeded)
   * @default 99
   */
  max?: number;
  
  /**
   * Overlap with child element
   * @default 'rectangular'
   */
  overlap?: 'rectangular' | 'circular';
  
  /**
   * Badge position anchor
   * @default 'top-right'
   */
  anchorOrigin?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';
  
  /**
   * Show pulse animation
   * @default false
   */
  pulse?: boolean;
  
  /**
   * Hide the badge
   * @default false
   */
  invisible?: boolean;
  
  /**
   * Element to apply badge to
   */
  children?: React.ReactNode;
}

/**
 * Color variant classes
 */
const colorClasses: Record<string, string> = {
  primary: 'bg-primary-500 text-white',
  secondary: 'bg-secondary-500 text-white',
  error: 'bg-error-500 text-white',
  warning: 'bg-warning-500 text-white',
  info: 'bg-info-500 text-white',
  success: 'bg-success-500 text-white',
  default: 'bg-grey-500 text-white',
};

/**
 * Size classes for standard badge
 */
const sizeClasses: Record<string, string> = {
  small: 'min-w-[16px] h-4 text-[10px] px-1',
  medium: 'min-w-[20px] h-5 text-xs px-1.5',
  large: 'min-w-[24px] h-6 text-sm px-2',
};

/**
 * Size classes for dot badge
 */
const dotSizeClasses: Record<string, string> = {
  small: 'w-1.5 h-1.5',
  medium: 'w-2 h-2',
  large: 'w-2.5 h-2.5',
};

/**
 * Position classes
 */
const positionClasses: Record<string, string> = {
  'top-right': 'top-0 right-0 translate-x-1/2 -translate-y-1/2',
  'top-left': 'top-0 left-0 -translate-x-1/2 -translate-y-1/2',
  'bottom-right': 'bottom-0 right-0 translate-x-1/2 translate-y-1/2',
  'bottom-left': 'bottom-0 left-0 -translate-x-1/2 translate-y-1/2',
};

export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(
  (
    {
      badgeContent,
      color = 'primary',
      size = 'medium',
      variant = 'standard',
      showZero = false,
      max = 99,
      overlap = 'rectangular',
      anchorOrigin = 'top-right',
      pulse = false,
      invisible = false,
      className,
      children,
      ...props
    },
    ref
  ) => {
    // Determine if badge should be shown
    const isZero = badgeContent === 0 || badgeContent === '0';
    const shouldHide = invisible || (isZero && !showZero);
    
    // Format badge content (handle max)
    let displayContent = badgeContent;
    if (typeof badgeContent === 'number' && badgeContent > max) {
      displayContent = `${max}+`;
    }
    
    // If no children, render standalone badge
    if (!children) {
      if (variant === 'dot') {
        return (
          <span
            ref={ref}
            className={cn(
              'inline-flex rounded-full',
              colorClasses[color],
              dotSizeClasses[size],
              pulse && 'animate-pulse',
              shouldHide && 'hidden',
              className
            )}
            {...props}
          />
        );
      }
      
      return (
        <span
          ref={ref}
          className={cn(
            'inline-flex items-center justify-center rounded-full font-medium',
            colorClasses[color],
            sizeClasses[size],
            pulse && 'animate-pulse',
            shouldHide && 'hidden',
            className
          )}
          {...props}
        >
          {displayContent}
        </span>
      );
    }
    
    // Render badge with children
    return (
      <span ref={ref} className={cn('relative inline-flex', className)} {...props}>
        {children}
        {!shouldHide && (
          <span
            className={cn(
              'absolute flex items-center justify-center rounded-full font-medium',
              colorClasses[color],
              variant === 'dot' ? dotSizeClasses[size] : sizeClasses[size],
              positionClasses[anchorOrigin],
              overlap === 'circular' && 'origin-center scale-100',
              pulse && 'animate-pulse',
              'ring-2 ring-white'
            )}
          >
            {variant === 'standard' && displayContent}
          </span>
        )}
      </span>
    );
  }
);
