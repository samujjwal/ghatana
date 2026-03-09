/**
 * Stack Component (Tailwind CSS)
 * 
 * A layout component that arranges children in a vertical or horizontal stack
 * with consistent spacing between items.
 */

import React, { forwardRef } from 'react';

import { cn } from '../../utils/cn';

import type { ElementType } from 'react';

/**
 *
 */
export interface StackProps extends React.HTMLAttributes<HTMLElement> {
  /**
   * The HTML element to render
   * @default 'div'
   */
  as?: ElementType;
  
  /**
   * Stack direction
   * @default 'vertical'
   */
  direction?: 'vertical' | 'horizontal';
  
  /**
   * Spacing between items (Tailwind gap classes)
   * Examples: 'gap-2', 'gap-4', 'gap-6'
   * @default 'gap-4'
   */
  spacing?: string;
  
  /**
   * Alignment on cross axis
   * For vertical: 'items-start' | 'items-center' | 'items-end' | 'items-stretch'
   * For horizontal: 'items-start' | 'items-center' | 'items-end' | 'items-baseline'
   */
  align?: string;

  /**
   * Alignment on cross axis specified with CSS keywords.
   * Convenience wrapper that maps to Tailwind classes.
   */
  alignItems?: 'start' | 'center' | 'end' | 'stretch' | 'baseline';
  
  /**
   * Justification on main axis
   * Examples: 'justify-start', 'justify-center', 'justify-end', 'justify-between', 'justify-around'
   */
  justify?: string;

  /**
   * Main-axis justification specified with CSS keywords.
   * Convenience wrapper that maps to Tailwind classes.
   */
  justifyContent?: 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly';
  
  /**
   * Wrap items (Tailwind flex-wrap classes)
   * Examples: 'flex-wrap', 'flex-nowrap', 'flex-wrap-reverse'
   */
  wrap?: string;
  
  /**
   * Divider between items
   * Renders a divider element between each child
   */
  divider?: React.ReactNode;
  
  /**
   * Full width
   * @default false
   */
  fullWidth?: boolean;
  
  /**
   * Full height
   * @default false
   */
  fullHeight?: boolean;
  
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
 * Stack - A layout component for arranging children with consistent spacing
 * 
 * @example
 * ```tsx
 * // Vertical stack with spacing
 * <Stack spacing="gap-4">
 *   <div>Item 1</div>
 *   <div>Item 2</div>
 *   <div>Item 3</div>
 * </Stack>
 * 
 * // Horizontal stack with center alignment
 * <Stack direction="horizontal" spacing="gap-6" align="items-center">
 *   <button>Button 1</button>
 *   <button>Button 2</button>
 * </Stack>
 * 
 * // Stack with dividers
 * <Stack divider={<hr className="border-grey-200" />}>
 *   <div>Section 1</div>
 *   <div>Section 2</div>
 * </Stack>
 * ```
 */
export const Stack = forwardRef<HTMLElement, StackProps>(
  (
    {
      as = 'div',
      direction = 'vertical',
      spacing = 'gap-4',
      align,
      alignItems,
      justify,
      justifyContent,
      wrap,
      divider,
      fullWidth = false,
      fullHeight = false,
      className,
      children,
      ...rest
    },
    ref
  ) => {
    const Component = as as ElementType;
    
    const isVertical = direction === 'vertical';
    
    const alignClass = align ?? (alignItems ? ({
      start: 'items-start',
      center: 'items-center',
      end: 'items-end',
      stretch: 'items-stretch',
      baseline: 'items-baseline',
    }[alignItems] ?? undefined) : undefined);

    const justifyClass = justify ?? (justifyContent ? ({
      start: 'justify-start',
      center: 'justify-center',
      end: 'justify-end',
      between: 'justify-between',
      around: 'justify-around',
      evenly: 'justify-evenly',
    }[justifyContent] ?? undefined) : undefined);

    const classes = cn(
      'flex',
      isVertical ? 'flex-col' : 'flex-row',
      spacing,
      alignClass,
      justifyClass,
      wrap,
      fullWidth && 'w-full',
      fullHeight && 'h-full',
      className
    );

    // If divider is provided, insert it between children
    const childrenArray = React.Children.toArray(children);
    const childrenWithDividers = divider
      ? childrenArray.reduce<React.ReactNode[]>((acc, child, index) => {
          if (index > 0) {
            acc.push(
              <React.Fragment key={`divider-${index}`}>
                {divider}
              </React.Fragment>
            );
          }
          acc.push(child);
          return acc;
        }, [])
      : children;

    return (
      <Component ref={ref as unknown} className={classes} {...rest}>
        {childrenWithDividers}
      </Component>
    );
  }
);

Stack.displayName = 'Stack';
