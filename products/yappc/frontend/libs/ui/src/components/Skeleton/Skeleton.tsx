import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * Skeleton variant types
 */
export type SkeletonVariant = 'text' | 'circular' | 'rectangular';

/**
 * Skeleton component props
 */
export interface SkeletonProps {
  /**
   * Variant type
   * @default 'text'
   */
  variant?: SkeletonVariant;
  /**
   * Width in CSS units (px, %, rem, etc.)
   */
  width?: string | number;
  /**
   * Height in CSS units (px, %, rem, etc.)
   */
  height?: string | number;
  /**
   * Custom className
   */
  className?: string;
  /**
   * Animation enabled
   * @default true
   */
  animation?: boolean;
}

/**
 * Skeleton - Loading placeholder component
 *
 * Displays a placeholder while content is loading.
 * Supports text, circular (avatar), and rectangular (image/card) variants.
 *
 * @example Text skeleton
 * ```tsx
 * <Skeleton variant="text" width="200px" />
 * ```
 *
 * @example Circular skeleton (avatar)
 * ```tsx
 * <Skeleton variant="circular" width={40} height={40} />
 * ```
 *
 * @example Rectangular skeleton (image)
 * ```tsx
 * <Skeleton variant="rectangular" width="100%" height={200} />
 * ```
 */
export const Skeleton = React.forwardRef<HTMLDivElement, SkeletonProps>(
  (
    {
      variant = 'text',
      width,
      height,
      className,
      animation = true,
    },
    ref
  ) => {
    // Default heights based on variant
    const defaultHeight = variant === 'text' ? '1em' : variant === 'circular' ? '40px' : '100px';
    
    // Convert number to px string
    const widthValue = typeof width === 'number' ? `${width}px` : width;
    const heightValue = typeof height === 'number' ? `${height}px` : height ?? defaultHeight;

    // Variant-specific classes
    const variantClasses = {
      text: 'rounded',
      circular: 'rounded-full',
      rectangular: 'rounded-lg',
    };

    return (
      <div
        ref={ref}
        className={cn(
          'bg-grey-200 dark:bg-grey-700',
          animation && 'animate-pulse',
          variantClasses[variant],
          className
        )}
        style={{
          width: widthValue,
          height: heightValue,
        }}
        aria-busy="true"
        aria-live="polite"
      />
    );
  }
);

Skeleton.displayName = 'Skeleton';
