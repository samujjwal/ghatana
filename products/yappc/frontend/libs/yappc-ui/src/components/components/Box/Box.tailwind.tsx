/**
 * Box Component (Tailwind CSS)
 * 
 * A flexible container component that serves as a building block for layouts.
 * Supports polymorphic rendering (can be rendered as any HTML element).
 */

import React, { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { ElementType } from 'react';

/**
 *
 */
export interface BoxProps extends React.HTMLAttributes<HTMLElement> {
  /**
   * The HTML element to render
   * @default 'div'
   */
  as?: ElementType;
  
  /**
   * Padding (theme spacing units or Tailwind classes)
   * Examples: 'p-4', 'px-6 py-4', 'p-md'
   */
  p?: string;
  
  /**
   * Padding X-axis
   */
  px?: string;
  
  /**
   * Padding Y-axis
   */
  py?: string;
  
  /**
   * Padding top
   */
  pt?: string;
  
  /**
   * Padding right
   */
  pr?: string;
  
  /**
   * Padding bottom
   */
  pb?: string;
  
  /**
   * Padding left
   */
  pl?: string;
  
  /**
   * Margin (theme spacing units or Tailwind classes)
   */
  m?: string;
  
  /**
   * Margin X-axis
   */
  mx?: string;
  
  /**
   * Margin Y-axis
   */
  my?: string;
  
  /**
   * Margin top
   */
  mt?: string;
  
  /**
   * Margin right
   */
  mr?: string;
  
  /**
   * Margin bottom
   */
  mb?: string;
  
  /**
   * Margin left
   */
  ml?: string;
  
  /**
   * Background color (Tailwind classes)
   * Examples: 'bg-white', 'bg-primary-500', 'bg-grey-100'
   */
  bg?: string;
  
  /**
   * Text color (Tailwind classes)
   */
  color?: string;
  
  /**
   * Border radius (Tailwind classes)
   * Examples: 'rounded', 'rounded-md', 'rounded-lg', 'rounded-full'
   */
  rounded?: string;
  
  /**
   * Border (Tailwind classes)
   * Examples: 'border', 'border-2', 'border-grey-300'
   */
  border?: string;
  
  /**
   * Shadow (Tailwind classes)
   * Examples: 'shadow', 'shadow-md', 'shadow-lg'
   */
  shadow?: string;
  
  /**
   * Display mode (Tailwind classes)
   * Examples: 'block', 'flex', 'grid', 'inline-block'
   */
  display?: string;
  
  /**
   * Width (Tailwind classes)
   * Examples: 'w-full', 'w-1/2', 'w-64'
   */
  w?: string;
  
  /**
   * Height (Tailwind classes)
   * Examples: 'h-full', 'h-64', 'h-screen'
   */
  h?: string;
  
  /**
   * Max width (Tailwind classes)
   */
  maxW?: string;
  
  /**
   * Max height (Tailwind classes)
   */
  maxH?: string;
  
  /**
   * Min width (Tailwind classes)
   */
  minW?: string;
  
  /**
   * Min height (Tailwind classes)
   */
  minH?: string;
  
  /**
   * Overflow (Tailwind classes)
   * Examples: 'overflow-hidden', 'overflow-auto', 'overflow-scroll'
   */
  overflow?: string;
  
  /**
   * Position (Tailwind classes)
   * Examples: 'relative', 'absolute', 'fixed', 'sticky'
   */
  position?: string;
  
  /**
   * Top position (Tailwind classes)
   */
  top?: string;
  
  /**
   * Right position (Tailwind classes)
   */
  right?: string;
  
  /**
   * Bottom position (Tailwind classes)
   */
  bottom?: string;
  
  /**
   * Left position (Tailwind classes)
   */
  left?: string;
  
  /**
   * Z-index (Tailwind classes)
   */
  zIndex?: string;
  
  /**
   * Additional CSS classes
   */
  className?: string;
  
  /**
   * Children elements
   */
  children?: React.ReactNode;
}

/**
 * Box - A flexible container component
 * 
 * @example
 * ```tsx
 * // Simple box with padding and background
 * <Box p="p-4" bg="bg-white" rounded="rounded-lg">
 *   Content
 * </Box>
 * 
 * // Box as a different element
 * <Box as="section" p="p-6" shadow="shadow-md">
 *   Section content
 * </Box>
 * 
 * // Flex container
 * <Box display="flex" className="items-center gap-4">
 *   <div>Item 1</div>
 *   <div>Item 2</div>
 * </Box>
 * ```
 */
export const Box = forwardRef<HTMLElement, BoxProps>(
  (
    {
      as = 'div',
      p,
      px,
      py,
      pt,
      pr,
      pb,
      pl,
      m,
      mx,
      my,
      mt,
      mr,
      mb,
      ml,
      bg,
      color,
      rounded,
      border,
      shadow,
      display,
      w,
      h,
      maxW,
      maxH,
      minW,
      minH,
      overflow,
      position,
      top,
      right,
      bottom,
      left,
      zIndex,
      className,
      children,
      ...rest
    },
    ref
  ) => {
    const Component = as as ElementType;
    
    const classes = cn(
      // Padding
      p,
      px,
      py,
      pt,
      pr,
      pb,
      pl,
      // Margin
      m,
      mx,
      my,
      mt,
      mr,
      mb,
      ml,
      // Colors
      bg,
      color,
      // Border & Shadow
      rounded,
      border,
      shadow,
      // Layout
      display,
      w,
      h,
      maxW,
      maxH,
      minW,
      minH,
      overflow,
      // Positioning
      position,
      top,
      right,
      bottom,
      left,
      zIndex,
      // Additional classes
      className
    );

    return (
      <Component ref={ref as unknown} className={classes} {...rest}>
        {children}
      </Component>
    );
  }
);

Box.displayName = 'Box';
