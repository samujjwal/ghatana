/**
 * Paper Component (Tailwind CSS)
 * 
 * A surface component that mimics MUI Paper using Box with predefined elevation shadows.
 * Provides elevation-based shadow styling for creating layered surfaces.
 */

import { forwardRef } from 'react';

import { Box, type BoxProps } from '../Box';

/**
 *
 */
export interface PaperProps extends Omit<BoxProps, 'shadow'> {
  /**
   * Shadow elevation level (0-24)
   * Maps to MUI Paper elevation prop
   * @default 1
   */
  elevation?: 0 | 1 | 2 | 3 | 4 | 6 | 8 | 12 | 16 | 24;
  
  /**
   * Add border
   * @default false
   */
  variant?: 'elevation' | 'outlined';
  
  /**
   * Make the paper square (no border radius)
   * @default false
   */
  square?: boolean;
}

/**
 * Map elevation to Tailwind shadow classes
 */
const elevationToShadow: Record<number, string> = {
  0: 'shadow-none',
  1: 'shadow-sm',
  2: 'shadow',
  3: 'shadow-md',
  4: 'shadow-md',
  6: 'shadow-lg',
  8: 'shadow-lg',
  12: 'shadow-xl',
  16: 'shadow-2xl',
  24: 'shadow-2xl',
};

export const Paper = forwardRef<HTMLDivElement, PaperProps>(
  (
    {
      elevation = 1,
      variant = 'elevation',
      square = false,
      className,
      bg = 'bg-white',
      rounded,
      border,
      ...props
    },
    ref
  ) => {
    return (
      <Box
        ref={ref}
        bg={bg}
        rounded={square ? undefined : (rounded || 'rounded-lg')}
        shadow={variant === 'elevation' ? elevationToShadow[elevation] : undefined}
        border={variant === 'outlined' ? (border || 'border border-grey-300') : border}
        className={className}
        {...props}
      />
    );
  }
);
